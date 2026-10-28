package com.arqnova.repositorios.serializacion;
import com.arqnova.servicios.puertos.salida.GraphDataPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.springframework.stereotype.Component;
@Component public class JacksonGraphDataAdapter implements GraphDataPort {
 public Map<String,Object> copy(Map<String,Object> value){return value==null?new LinkedHashMap<>():new ObjectMapper().convertValue(value,LinkedHashMap.class);}
}
