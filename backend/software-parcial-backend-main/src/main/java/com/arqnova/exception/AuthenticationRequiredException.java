package com.arqnova.exception;

public class AuthenticationRequiredException extends RuntimeException {
    public AuthenticationRequiredException() {
        super("Se requiere un contexto autenticado válido");
    }
    public AuthenticationRequiredException(String message) { super(message); }
}
