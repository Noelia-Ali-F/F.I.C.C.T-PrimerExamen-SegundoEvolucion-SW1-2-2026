package com.arqnova.servicios.puertos.salida;
public interface PasswordPort { String encode(String rawPassword);boolean matches(String rawPassword,String storedPassword);boolean isEncoded(String password); }
