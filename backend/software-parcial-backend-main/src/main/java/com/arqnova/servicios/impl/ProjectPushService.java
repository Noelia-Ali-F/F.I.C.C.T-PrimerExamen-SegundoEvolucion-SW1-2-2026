package com.arqnova.servicios.impl;
import com.arqnova.modelo.ia.ProjectPush.Event;
import com.arqnova.modelo.excepciones.UnauthorizedActionException;
import com.arqnova.servicios.puertos.entrada.*;
import com.arqnova.servicios.puertos.salida.*;

/** Optional best-effort notifications never participate in diagram or conversation transactions. */
public final class ProjectPushService implements ProjectPushUseCase {
    private final PushDevicePort devices;private final PushDeliveryPort sender;private final PushTaskPort tasks;private final UserQueryUseCase users;
    public ProjectPushService(PushDevicePort devices,PushDeliveryPort sender,PushTaskPort tasks,UserQueryUseCase users){this.devices=devices;this.sender=sender;this.tasks=tasks;this.users=users;}
    public boolean enabled(){return devices.enabled()&&sender.enabled();}
    public void register(String username,String fid){
        validate(fid);if(!enabled())throw new IllegalArgumentException("Las notificaciones todavía no están habilitadas");
        if(users.findByUsername(username).filter(u->!Boolean.FALSE.equals(u.getHabilitado())).isEmpty())throw new UnauthorizedActionException("Usuario no disponible");
        devices.register(username,fid);
    }
    public void unregister(String username,String fid){validate(fid);if(devices.enabled())devices.remove(username,fid);}
    public void notifyChange(Event event){
        if(!enabled()||event==null||event.id()==null||event.id().length()>160||event.projectId()==null)return;
        try{tasks.schedule(()->deliver(event));}catch(RuntimeException busy){/* Notification failure cannot undo a saved change. */}
    }
    private void deliver(Event event){
        try{
            for(var device:devices.recipients(event.projectId())){
                boolean claimed=false;
                try{
                    claimed=devices.claim(event.id(),device.fid());
                    if(claimed)sender.send(device.fid(),event);
                }catch(Exception unavailable){
                    if(claimed)try{devices.releaseClaim(event.id(),device.fid());}
                    catch(RuntimeException cleanupUnavailable){/* Continue with the other recipients. */}
                }
            }
        }catch(RuntimeException unavailable){/* The canonical diagram/history remains available in the app. */}
    }
    private void validate(String fid){if(fid==null||!fid.matches("[A-Za-z0-9_-]{8,256}"))throw new IllegalArgumentException("Identificador de instalación inválido");}
}
