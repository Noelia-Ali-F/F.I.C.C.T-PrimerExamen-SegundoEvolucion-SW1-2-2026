package com.arqnova.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.security.Principal;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
@RequiredArgsConstructor
public class ApiAuthenticationFilter extends OncePerRequestFilter {

    private final AuthTokenService tokenService;
    private final com.arqnova.persistence.UserStore usuarioRepository;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;
        if (!path.startsWith("/api/v1/")) return true;
        return path.equals("/api/v1/auth/login") || path.equals("/api/v1/auth/register");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = request.getParameter("access_token");
        String authorization = request.getHeader("Authorization");
        if ((token == null || token.isBlank()) && authorization != null && authorization.startsWith("Bearer ")) {
            token = authorization.substring(7);
        }
        try {
            AuthTokenService.Claims claims = tokenService.verificar(token);
            com.arqnova.domain.model.Usuario usuario = usuarioRepository.findByUsername(claims.username())
                    .orElseThrow(() -> new IllegalArgumentException("Usuario inexistente"));
            if (Boolean.FALSE.equals(usuario.getHabilitado())) {
                writeError(response, 403, "Tu cuenta se encuentra deshabilitada");
                return;
            }
            if (usuario.getRol() == null || !usuario.getRol().name().equalsIgnoreCase(claims.rol())) {
                writeError(response, 401, "La sesión ya no coincide con los permisos actuales");
                return;
            }
            if (!matchesIfPresent(request.getHeader("X-Usuario"), claims.username())
                    || !matchesIfPresent(request.getHeader("X-Rol"), claims.rol())
                    || !matchesIfPresent(request.getHeader("X-Departamento"), claims.departamento())) {
                writeError(response, 403, "El contexto enviado no coincide con la identidad firmada");
                return;
            }
            chain.doFilter(new ClaimsRequestWrapper(request, claims), response);
        } catch (IllegalArgumentException ex) {
            writeError(response, 401, "Autenticación requerida: " + ex.getMessage());
        }
    }

    private boolean matchesIfPresent(String supplied, String signed) {
        return supplied == null || supplied.isBlank() || supplied.equalsIgnoreCase(signed == null ? "" : signed);
    }

    private void writeError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"exito\":false,\"mensaje\":\"" + message.replace("\"", "'") + "\"}");
    }

    private static class ClaimsRequestWrapper extends HttpServletRequestWrapper {
        private final AuthTokenService.Claims claims;
        ClaimsRequestWrapper(HttpServletRequest request, AuthTokenService.Claims claims) {
            super(request);
            this.claims = claims;
        }
        @Override public String getHeader(String name) {
            if ("X-Usuario".equalsIgnoreCase(name)) return claims.username();
            if ("X-Rol".equalsIgnoreCase(name)) return claims.rol();
            if ("X-Departamento".equalsIgnoreCase(name)) return claims.departamento();
            return super.getHeader(name);
        }
        @Override public Enumeration<String> getHeaders(String name) {
            if ("X-Usuario".equalsIgnoreCase(name) || "X-Rol".equalsIgnoreCase(name) || "X-Departamento".equalsIgnoreCase(name)) {
                return Collections.enumeration(Collections.singletonList(getHeader(name)));
            }
            return super.getHeaders(name);
        }
        @Override public Principal getUserPrincipal() { return () -> claims.username(); }
        @Override public String getRemoteUser() { return claims.username(); }
    }
}
