package com.arqnova.repositorios.ia.memoria;
import com.arqnova.modelo.ia.AiConversation.*;
import com.arqnova.repositorios.ia.UmlAiProviderPipeline;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class PipelineConversationAdapterTest {
    final UmlAiProviderPipeline pipeline=mock(UmlAiProviderPipeline.class);
    final PipelineConversationAdapter adapter=new PipelineConversationAdapter(pipeline,new ObjectMapper());
    Turn turn(){var now=Instant.now();return new Turn("request-001","p","noelia","Noelia",Mode.REVIEW,"Revisa",State.PENDING,null,null,3,now,now);}
    Context context(Map<String,Object> model){return new Context("p","Biblioteca","noelia",model,3,List.of(),List.of());}
    @Test void usesBoundedOutputAndReturnsAdviceWithoutOperations()throws Exception{
        when(pipeline.generateConversation(anyString(),anyMap(),eq(1024))).thenReturn(new UmlAiProviderPipeline.Result("{\"answer\":\"Puedes agregar un identificador.\",\"findings\":[]}","GROQ","fake",false));
        var answer=adapter.answer(turn(),context(Map.of("classes",List.of(),"relations",List.of())));
        assertThat(answer.text()).contains("identificador");verify(pipeline).generateConversation(contains("No has modificado el lienzo"),anyMap(),eq(1024));
    }
    @Test void rejectsFindingsAboutInventedElements()throws Exception{
        when(pipeline.generateConversation(anyString(),anyMap(),anyInt())).thenReturn(new UmlAiProviderPipeline.Result("{\"answer\":\"Revisión\",\"findings\":[{\"severity\":\"ERROR\",\"elementId\":\"inventado\",\"message\":\"No existe\"}]}","GROQ","fake",false));
        assertThatThrownBy(()->adapter.answer(turn(),context(Map.of("classes",List.of(),"relations",List.of())))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void rejectsExcessiveContextBeforeCallingAnApi(){
        assertThatThrownBy(()->adapter.answer(turn(),context(Map.of("classes",List.of(Map.of("id","a","name","x".repeat(15000))))))).isInstanceOf(IllegalArgumentException.class).hasMessage("AI_CONTEXT_TOO_LARGE");verifyNoInteractions(pipeline);
    }
    @Test void removesCanvasCoordinatesFromTheAiPrompt()throws Exception{
        when(pipeline.generateConversation(anyString(),anyMap(),anyInt())).thenReturn(new UmlAiProviderPipeline.Result("{\"answer\":\"Bien\",\"findings\":[]}","GEMINI","fake",false));
        adapter.answer(turn(),context(Map.of("classes",List.of(Map.of("id","a","name","Cliente","x",12345,"y",98765)),"relations",List.of())));
        var prompt=org.mockito.ArgumentCaptor.forClass(String.class);verify(pipeline).generateConversation(prompt.capture(),anyMap(),anyInt());
        assertThat(prompt.getValue()).contains("Cliente").doesNotContain("12345","98765");
    }
}
