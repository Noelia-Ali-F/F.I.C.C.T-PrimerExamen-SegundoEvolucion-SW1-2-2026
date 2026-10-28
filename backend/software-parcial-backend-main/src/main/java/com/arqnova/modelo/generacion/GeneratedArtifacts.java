package com.arqnova.modelo.generacion;
public interface GeneratedArtifacts {
 record GeneratedBackend(String fileName,byte[] content,int classCount,int relationCount) {}
 record GeneratedPostman(String fileName,byte[] content,int entityCount,int requestCount) {}
 record GeneratedFlutter(String fileName,byte[] content,int modelCount,int screenCount) {}
}
