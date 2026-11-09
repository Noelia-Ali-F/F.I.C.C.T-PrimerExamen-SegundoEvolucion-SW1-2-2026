package com.arqnova.modelo.excepciones;
public class AiMemoryUnavailableException extends RuntimeException {
    public AiMemoryUnavailableException() { super("La conversación compartida no está disponible temporalmente. El editor y el asistente de edición siguen disponibles."); }
}
