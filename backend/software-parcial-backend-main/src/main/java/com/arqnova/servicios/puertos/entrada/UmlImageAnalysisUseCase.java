package com.arqnova.servicios.puertos.entrada;
import com.arqnova.modelo.BinaryFile;import java.util.Map;
public interface UmlImageAnalysisUseCase { Map<String,Object> analyzeImage(BinaryFile image)throws Exception; }
