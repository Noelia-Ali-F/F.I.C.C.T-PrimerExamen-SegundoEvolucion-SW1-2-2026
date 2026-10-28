package com.arqnova.servicios.puertos.salida;
import com.arqnova.modelo.BinaryFile;
import com.arqnova.modelo.dto.request.UmlAiCommandRequest;
import com.arqnova.modelo.dto.response.UmlAiCommandResponse;
import java.util.Map;
public interface UmlInterpretationPort {
 boolean isConfigured();
 UmlAiCommandResponse interpret(UmlAiCommandRequest request) throws Exception;
 Map<String,Object> analyzeImage(BinaryFile image) throws Exception;
}
