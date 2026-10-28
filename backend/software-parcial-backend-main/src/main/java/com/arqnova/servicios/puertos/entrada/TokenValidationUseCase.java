package com.arqnova.servicios.puertos.entrada;

public interface TokenValidationUseCase extends com.arqnova.modelo.seguridad.TokenClaims { Claims verificar(String token); }
