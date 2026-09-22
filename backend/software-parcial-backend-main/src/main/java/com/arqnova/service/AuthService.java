package com.arqnova.service;

import com.arqnova.dto.request.LoginRequest;
import com.arqnova.dto.request.RegisterRequest;
import com.arqnova.dto.response.AuthResponse;
import com.arqnova.dto.response.CurrentUserResponse;

public interface AuthService {
    
    AuthResponse login(LoginRequest request);
    
    AuthResponse register(RegisterRequest request);

    CurrentUserResponse currentUser(String username);
}
