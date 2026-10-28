package com.arqnova.servicios.puertos.salida;
import com.arqnova.modelo.BinaryFile;
public interface AvatarStoragePort {
 String almacenarAvatar(BinaryFile avatar,String usuarioResponsable);
 byte[] cargarAvatarBytes(String fileName);
 String tipoContenido(String fileName);
}
