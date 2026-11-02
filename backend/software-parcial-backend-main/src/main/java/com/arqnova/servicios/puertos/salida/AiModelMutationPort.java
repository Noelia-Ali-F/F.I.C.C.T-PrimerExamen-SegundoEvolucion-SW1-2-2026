package com.arqnova.servicios.puertos.salida;

import com.arqnova.modelo.ia.AiConversation.*;
import java.util.Optional;

/** Atomic application receipt and canonical model update belong to the same database transaction. */
public interface AiModelMutationPort {
    record Applied(Answer answer,int diagramVersion) {}
    boolean enabled();
    Optional<Applied> find(Turn turn);
    Applied apply(Turn turn,Context context,Answer answer);
}
