package com.arqnova.controladores;
import com.arqnova.modelo.dto.response.ApiResponse;
import com.arqnova.modelo.ia.ProjectActivity.Page;
import com.arqnova.servicios.puertos.entrada.ProjectActivityUseCase;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;

@RestController @RequestMapping("/api/v1/projects/{projectId}/activities")
public class ProjectActivityController {
    private final ProjectActivityUseCase service;
    public ProjectActivityController(ProjectActivityUseCase service){this.service=service;}
    @GetMapping public ApiResponse<Page> history(@PathVariable String projectId,Principal user,@RequestParam(required=false)String cursor,@RequestParam(defaultValue="30")int limit){
        return ApiResponse.ok("Actividad del proyecto",service.history(projectId,user.getName(),cursor,limit));
    }
}
