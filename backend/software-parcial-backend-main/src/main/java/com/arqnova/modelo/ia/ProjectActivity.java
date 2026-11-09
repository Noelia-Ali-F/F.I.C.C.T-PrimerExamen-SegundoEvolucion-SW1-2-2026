package com.arqnova.modelo.ia;
import java.util.List;

/** Read-only project activity; AI conversation history remains available through its own cursor. */
public record ProjectActivity(String id,String author,String kind,String description,long timestamp,
                              String requesterId,String executor,String provider,String requestId,String instruction) {
    public ProjectActivity(String id,String author,String kind,String description,long timestamp){
        this(id,author,kind,description,timestamp,null,null,null,null,null);
    }
    public record Page(List<ProjectActivity> items,String nextCursor){public Page{items=List.copyOf(items);}}
}
