package com.arqnova.service;

import com.arqnova.dto.request.UmlAiCommandRequest;
import com.arqnova.dto.response.UmlAiCommandResponse;

public interface UmlAiCommandService {
    UmlAiCommandResponse interpret(UmlAiCommandRequest request);
}
