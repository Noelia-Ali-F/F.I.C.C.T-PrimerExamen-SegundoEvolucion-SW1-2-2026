package com.arqnova.servicios.impl;
import com.arqnova.servicios.puertos.entrada.AvatarQueryUseCase;
import com.arqnova.servicios.puertos.salida.AvatarStoragePort;
public class AvatarQueryService implements AvatarQueryUseCase {
 private final AvatarStoragePort avatars;
 public AvatarQueryService(AvatarStoragePort avatars){this.avatars=avatars;}
 public byte[] cargarAvatarBytes(String fileName){return avatars.cargarAvatarBytes(fileName);}
 public String tipoContenido(String fileName){return avatars.tipoContenido(fileName);}
}
