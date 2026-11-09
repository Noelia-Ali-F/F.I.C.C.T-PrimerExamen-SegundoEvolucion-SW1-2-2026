package com.arqnova.modelo.ia;
public final class ProjectPush {
    private ProjectPush(){}
    public record Device(String fid,String username){}
    public record Event(String id,String projectId,String title,String body){}
}
