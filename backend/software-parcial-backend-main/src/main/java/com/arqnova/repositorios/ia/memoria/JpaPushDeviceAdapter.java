package com.arqnova.repositorios.ia.memoria;
import com.arqnova.modelo.ia.ProjectPush.Device;
import com.arqnova.servicios.puertos.salida.PushDevicePort;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.List;

@Repository @Profile("!dynamodb")
public class JpaPushDeviceAdapter implements PushDevicePort {
    private final JdbcTemplate jdbc;
    public JpaPushDeviceAdapter(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public boolean enabled(){return true;}
    public void register(String username,String fid){
        jdbc.update("INSERT INTO firebase_devices(fid,username) VALUES(?,?) ON CONFLICT(fid) DO UPDATE SET username=EXCLUDED.username,updated_at=CURRENT_TIMESTAMP",fid,username);
    }
    public void remove(String username,String fid){jdbc.update("DELETE FROM firebase_devices WHERE fid=? AND username=?",fid,username);}
    public List<Device> recipients(String projectId){
        return jdbc.query("SELECT DISTINCT d.fid,d.username FROM firebase_devices d JOIN users u ON u.username=d.username JOIN project_members m ON m.user_id=d.username WHERE m.project_id=? AND u.enabled=true AND d.updated_at>CURRENT_TIMESTAMP-INTERVAL '30 days'",
                (r,n)->new Device(r.getString(1),r.getString(2)),projectId);
    }
    public boolean claim(String eventId,String fid){return jdbc.update("INSERT INTO firebase_push_deliveries(event_id,fid) VALUES(?,?) ON CONFLICT DO NOTHING",eventId,fid)==1;}
    public void releaseClaim(String eventId,String fid){jdbc.update("DELETE FROM firebase_push_deliveries WHERE event_id=? AND fid=?",eventId,fid);}
}
