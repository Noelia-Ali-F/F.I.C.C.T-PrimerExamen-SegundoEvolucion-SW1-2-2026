package com.arqnova.servicios.puertos.salida;
import com.arqnova.modelo.ia.ProjectPush.Device;
import java.util.List;
public interface PushDevicePort {
    boolean enabled();
    void register(String username,String fid);
    void remove(String username,String fid);
    List<Device> recipients(String projectId);
    boolean claim(String eventId,String fid);
    void releaseClaim(String eventId,String fid);
}
