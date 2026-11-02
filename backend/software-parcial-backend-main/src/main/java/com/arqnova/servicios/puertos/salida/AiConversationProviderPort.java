package com.arqnova.servicios.puertos.salida;
import com.arqnova.modelo.ia.AiConversation.*;
public interface AiConversationProviderPort { Answer answer(Turn turn, Context context) throws Exception; }
