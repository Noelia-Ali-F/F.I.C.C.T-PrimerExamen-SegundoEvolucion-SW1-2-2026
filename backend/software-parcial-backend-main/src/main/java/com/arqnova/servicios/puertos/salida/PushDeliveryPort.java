package com.arqnova.servicios.puertos.salida;
import com.arqnova.modelo.ia.ProjectPush.Event;
public interface PushDeliveryPort {
    boolean enabled();
    void send(String fid,Event event)throws Exception;
}
