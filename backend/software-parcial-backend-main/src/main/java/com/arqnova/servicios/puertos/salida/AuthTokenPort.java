package com.arqnova.servicios.puertos.salida;
import com.arqnova.modelo.Usuario;
public interface AuthTokenPort extends com.arqnova.modelo.seguridad.TokenClaims {
 String emitir(Usuario usuario);
 Claims verificar(String token);

}
