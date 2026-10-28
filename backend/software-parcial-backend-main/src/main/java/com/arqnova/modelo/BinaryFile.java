package com.arqnova.modelo;
import java.io.*;
public record BinaryFile(String originalFilename,String contentType,byte[] content) {
 public BinaryFile { content=content==null?new byte[0]:content.clone(); }
 @Override public byte[] content(){return content.clone();}
 public String getOriginalFilename(){return originalFilename;}
 public String getContentType(){return contentType;}
 public byte[] getBytes(){return content();}
 public long getSize(){return content.length;}
 public boolean isEmpty(){return content.length==0;}
 public InputStream getInputStream(){return new ByteArrayInputStream(content);}
}
