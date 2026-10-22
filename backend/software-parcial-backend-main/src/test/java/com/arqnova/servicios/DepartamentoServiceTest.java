package com.arqnova.servicios;

import com.arqnova.modelo.Departamento;
import com.arqnova.modelo.dto.request.CrearDepartamentoRequest;
import com.arqnova.modelo.enums.RolUsuario;
import com.arqnova.modelo.excepciones.*;
import com.arqnova.repositorios.DepartamentoRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DepartamentoServiceTest {
    private final DepartamentoRepository repository=mock(DepartamentoRepository.class);
    private final DepartamentoService service=new DepartamentoService(repository);
    private CrearDepartamentoRequest request(String nombre,String descripcion){var r=new CrearDepartamentoRequest();r.setNombre(nombre);r.setDescripcion(descripcion);return r;}
    @Test void crearConservaNormalizacionAutorYDatosDevueltos(){
        when(repository.findByNombreIgnoreCase("Sistemas")).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation->{Departamento d=invocation.getArgument(0);d.setId("d1");return d;});
        var result=service.crearDepartamento(request(" Sistemas "," Equipo "),"admin",RolUsuario.ADMINISTRADOR);
        assertThat(result.getId()).isEqualTo("d1");assertThat(result.getNombre()).isEqualTo("Sistemas");assertThat(result.getDescripcion()).isEqualTo("Equipo");assertThat(result.getCreadoPor()).isEqualTo("admin");
    }
    @Test void crearDuplicadoInactivoConservaMensajeDeReactivacion(){
        when(repository.findByNombreIgnoreCase("Sistemas")).thenReturn(Optional.of(Departamento.builder().nombre("Sistemas").activo(false).build()));
        assertThatThrownBy(()->service.crearDepartamento(request("Sistemas",null),"admin",RolUsuario.ADMINISTRADOR)).isInstanceOf(DuplicateResourceException.class).hasMessageContaining("Reactivalo");verify(repository,never()).save(any());
    }
    @Test void solicitanteNoPuedeModificarDepartamentos(){
        assertThatThrownBy(()->service.eliminarDepartamento("d1","user",RolUsuario.SOLICITANTE)).isInstanceOf(UnauthorizedActionException.class);verifyNoInteractions(repository);
    }
    @Test void nombresRequiereContexto(){
        assertThatThrownBy(()->service.listarNombresDepartamentos("",null)).isInstanceOf(UnauthorizedActionException.class);verifyNoInteractions(repository);
    }
    @Test void eliminarSigueSiendoDesactivacionLogica(){
        var item=Departamento.builder().id("d1").nombre("Sistemas").activo(true).build();when(repository.findById("d1")).thenReturn(Optional.of(item));
        var payload=service.eliminarDepartamento("d1","admin",RolUsuario.ADMINISTRADOR);
        assertThat(item.isActivo()).isFalse();assertThat(payload).containsEntry("accion","DESACTIVADO").containsEntry("id","d1");verify(repository).save(item);verify(repository,never()).delete(any());
    }
    @Test void noReactivaUnDepartamentoYaActivo(){
        when(repository.findById("d1")).thenReturn(Optional.of(Departamento.builder().id("d1").activo(true).build()));
        assertThatThrownBy(()->service.reactivarDepartamento("d1","admin",RolUsuario.ADMINISTRADOR)).isInstanceOf(IllegalStateException.class).hasMessage("El departamento ya está activo.");verify(repository,never()).save(any());
    }
    @Test void actualizarImpideNombreDuplicado(){
        when(repository.findById("d1")).thenReturn(Optional.of(Departamento.builder().id("d1").nombre("A").build()));when(repository.existsByNombreIgnoreCase("B")).thenReturn(true);
        assertThatThrownBy(()->service.actualizarDepartamento("d1",request("B",null),"admin",RolUsuario.ADMINISTRADOR)).isInstanceOf(DuplicateResourceException.class);verify(repository,never()).save(any());
    }
    @Test void departamentoAusenteConservaError(){
        when(repository.findById("missing")).thenReturn(Optional.empty());
        assertThatThrownBy(()->service.eliminarDepartamento("missing","admin",RolUsuario.ADMINISTRADOR)).isInstanceOf(ResourceNotFoundException.class);
    }
}
