package com.arqnova.servicios.puertos.entrada;
import com.arqnova.modelo.ia.ProjectPush.Event;
public interface ProjectPushUseCase {
    boolean enabled();
    void register(String username,String fid);
    void unregister(String username,String fid);
    void notifyChange(Event event);
}
