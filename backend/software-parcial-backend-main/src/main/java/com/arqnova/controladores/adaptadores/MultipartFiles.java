package com.arqnova.controladores.adaptadores;
import com.arqnova.modelo.BinaryFile;
import org.springframework.web.multipart.MultipartFile;
public final class MultipartFiles {
 private MultipartFiles(){}
 public static BinaryFile read(MultipartFile value){
  if(value==null)return null;
  try{return new BinaryFile(value.getOriginalFilename(),value.getContentType(),value.getBytes());}
  catch(java.io.IOException error){throw new IllegalStateException("No se pudo leer el archivo",error);}
 }
}
