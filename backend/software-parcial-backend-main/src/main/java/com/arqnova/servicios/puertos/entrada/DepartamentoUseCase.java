package com.arqnova.servicios.puertos.entrada;
import com.arqnova.modelo.enums.RolUsuario;
import com.arqnova.modelo.Departamento;
import com.arqnova.modelo.dto.request.CrearDepartamentoRequest;
import com.arqnova.modelo.dto.response.DepartamentoResponse;
import com.arqnova.modelo.excepciones.DuplicateResourceException;
import com.arqnova.modelo.excepciones.ResourceNotFoundException;
import com.arqnova.modelo.excepciones.UnauthorizedActionException;
import com.arqnova.servicios.puertos.salida.DepartamentoStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
public interface DepartamentoUseCase {
 List<DepartamentoResponse> listarDepartamentos(String usuario, RolUsuario rolUsuario) ;
 List<String> listarNombresDepartamentos(String usuario, RolUsuario rolUsuario) ;
 DepartamentoResponse crearDepartamento(CrearDepartamentoRequest request, String usuario, RolUsuario rolUsuario) ;
 Map<String,String> eliminarDepartamento(String id, String usuario, RolUsuario rolUsuario) ;
 DepartamentoResponse reactivarDepartamento(String id, String usuario, RolUsuario rolUsuario) ;
 DepartamentoResponse actualizarDepartamento(String id, CrearDepartamentoRequest request, String usuario, RolUsuario rolUsuario) ;

}
