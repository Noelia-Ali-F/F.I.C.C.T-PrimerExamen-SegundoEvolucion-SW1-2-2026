package com.arqnova.servicios.impl;
import com.arqnova.servicios.puertos.entrada.TokenValidationUseCase;
import com.arqnova.servicios.puertos.salida.AuthTokenPort;
public class TokenValidationService implements TokenValidationUseCase {
 private final AuthTokenPort tokens;
 public TokenValidationService(AuthTokenPort tokens){this.tokens=tokens;}
 public AuthTokenPort.Claims verificar(String token){return tokens.verificar(token);}
}
