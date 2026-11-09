package com.arqnova.servicios.puertos.entrada;
import com.arqnova.modelo.ia.ProjectActivity.Page;
public interface ProjectActivityUseCase {Page history(String project,String user,String cursor,int limit);}
