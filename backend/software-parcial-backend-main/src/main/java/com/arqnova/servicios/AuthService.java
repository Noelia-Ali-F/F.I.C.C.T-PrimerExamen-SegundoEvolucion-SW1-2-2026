package com.arqnova.servicios;

import com.arqnova.modelo.dto.request.LoginRequest;
import com.arqnova.modelo.dto.request.RegisterRequest;
import com.arqnova.modelo.dto.response.AuthResponse;
import com.arqnova.modelo.dto.response.CurrentUserResponse;

public interface AuthService {

    AuthResponse login(LoginRequest request);

    AuthResponse register(RegisterRequest request);

    CurrentUserResponse currentUser(String username);
}
