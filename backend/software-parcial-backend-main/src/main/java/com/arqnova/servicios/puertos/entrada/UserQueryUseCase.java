package com.arqnova.servicios.puertos.entrada;
import com.arqnova.modelo.Usuario;
import com.arqnova.servicios.puertos.salida.UserStore;
import lombok.RequiredArgsConstructor;
import java.util.Optional;
public interface UserQueryUseCase {
 Optional<Usuario> findByUsername(String username) ;

}
