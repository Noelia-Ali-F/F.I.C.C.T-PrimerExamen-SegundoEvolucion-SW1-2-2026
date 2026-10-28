package com.arqnova.modelo.excepciones;
public class AiProviderException extends IllegalStateException {
 public final int httpStatus;
 public AiProviderException(int status){super("OpenAI rechazó la solicitud (HTTP "+status+")");this.httpStatus=status;}
}
