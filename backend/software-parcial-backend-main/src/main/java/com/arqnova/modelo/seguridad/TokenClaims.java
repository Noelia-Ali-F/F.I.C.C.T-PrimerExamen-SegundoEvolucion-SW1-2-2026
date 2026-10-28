package com.arqnova.modelo.seguridad;
public interface TokenClaims { record Claims(String username,String rol,String departamento,long expiresAt) {} }
