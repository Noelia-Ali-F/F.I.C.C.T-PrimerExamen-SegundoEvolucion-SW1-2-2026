package com.arqnova.servicios;

import com.arqnova.modelo.dto.request.UmlAiCommandRequest;
import com.arqnova.modelo.dto.response.UmlAiCommandResponse;

public interface UmlAiCommandService {
    UmlAiCommandResponse interpret(UmlAiCommandRequest request);
}
