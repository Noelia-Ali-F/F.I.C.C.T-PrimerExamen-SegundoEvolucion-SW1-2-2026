package com.arqnova.repositorios.ia.memoria;
import com.arqnova.modelo.ia.AiConversation.Change;
import com.arqnova.servicios.puertos.salida.AiConversationEventsPort;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
@Component
public class StompAiConversationEvents implements AiConversationEventsPort {
    private final SimpMessagingTemplate messaging;
    public StompAiConversationEvents(SimpMessagingTemplate messaging){this.messaging=messaging;}
    public void changed(Change event){messaging.convertAndSend("/topic/ai/"+event.projectId(),event);}
}
