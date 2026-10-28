package com.arqnova.repositorios.archivos;
import com.arqnova.modelo.BinaryFile;
import org.springframework.web.multipart.MultipartFile;
import java.io.*;
public record BinaryMultipartAdapter(BinaryFile value) implements MultipartFile {
 public String getName(){return "image";}
 public String getOriginalFilename(){return value.getOriginalFilename();}
 public String getContentType(){return value.getContentType();}
 public boolean isEmpty(){return value.isEmpty();}
 public long getSize(){return value.getSize();}
 public byte[] getBytes(){return value.getBytes();}
 public InputStream getInputStream(){return value.getInputStream();}
 public void transferTo(File dest)throws IOException{java.nio.file.Files.write(dest.toPath(),getBytes());}
}
