package com.arqnova.servicios.impl;
import com.arqnova.modelo.BinaryFile;
import com.arqnova.servicios.puertos.entrada.UmlImageAnalysisUseCase;
import com.arqnova.servicios.puertos.salida.UmlInterpretationPort;
import java.util.Map;
public class UmlImageAnalysisService implements UmlImageAnalysisUseCase {
 private final UmlInterpretationPort interpreter;
 public UmlImageAnalysisService(UmlInterpretationPort interpreter){this.interpreter=interpreter;}
 public Map<String,Object> analyzeImage(BinaryFile image)throws Exception{return interpreter.analyzeImage(image);}
}
