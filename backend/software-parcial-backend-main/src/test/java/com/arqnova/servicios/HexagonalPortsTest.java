package com.arqnova.servicios;
import com.arqnova.modelo.BinaryFile;
import com.arqnova.modelo.dto.request.UmlAiCommandRequest;
import com.arqnova.modelo.dto.response.UmlAiCommandResponse;
import com.arqnova.servicios.impl.UmlImageAnalysisService;
import com.arqnova.servicios.puertos.salida.UmlInterpretationPort;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
class HexagonalPortsTest {
 @Test void analizaUnaImagenSinSpringNiMultipartNiHttp() throws Exception {
  byte[] input={1,2,3};BinaryFile image=new BinaryFile("ejemplo.png","image/png",input);input[0]=9;
  var result=Map.<String,Object>of("status","READY","name","Ejemplo");
  UmlInterpretationPort fake=new UmlInterpretationPort(){
   public boolean isConfigured(){return true;}
   public UmlAiCommandResponse interpret(UmlAiCommandRequest request){throw new AssertionError("No corresponde a este caso de uso");}
   public Map<String,Object> analyzeImage(BinaryFile file){assertThat(file.getBytes()).containsExactly((byte)1,(byte)2,(byte)3);return result;}
  };
  assertThat(new UmlImageAnalysisService(fake).analyzeImage(image)).isSameAs(result);
  byte[] copy=image.getBytes();copy[0]=7;assertThat(image.getBytes()[0]).isEqualTo((byte)1);
 }
}
