package com.arqnova.repositorios.ia.memoria;
import com.arqnova.modelo.ia.AiConversation.Change;
import com.arqnova.servicios.puertos.salida.AiConversationEventsPort;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
@Component
public class StompAiConversationEvents implements AiConversationEventsPort {
    private final SimpMessagingTemplate messaging;
    private final com.arqnova.servicios.puertos.entrada.ProjectPushUseCase push;
    public StompAiConversationEvents(SimpMessagingTemplate messaging,com.arqnova.servicios.puertos.entrada.ProjectPushUseCase push){this.messaging=messaging;this.push=push;}
    public void changed(Change event){
        try{messaging.convertAndSend("/topic/ai/"+event.projectId(),event);}
        finally{if(event.state()==com.arqnova.modelo.ia.AiConversation.State.COMPLETED){
            String id="ai-"+java.util.UUID.nameUUIDFromBytes((event.projectId()+"|"+event.requestId()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            push.notifyChange(new com.arqnova.modelo.ia.ProjectPush.Event(id,event.projectId(),"ArqNova: respuesta de IA","Hay una nueva intervención de IA en la conversación del proyecto."));
        }}
    }
}
