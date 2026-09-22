package com.arqnova.security;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class PasswordService {
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);

    public String encode(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    public boolean matches(String rawPassword, String storedPassword) {
        if (rawPassword == null || storedPassword == null) return false;
        if (isEncoded(storedPassword)) return encoder.matches(rawPassword, storedPassword);
        return constantTimeEquals(rawPassword, storedPassword);
    }

    public boolean isEncoded(String password) {
        return password != null && (password.startsWith("$2a$") || password.startsWith("$2b$") || password.startsWith("$2y$"));
    }

    private boolean constantTimeEquals(String first, String second) {
        return java.security.MessageDigest.isEqual(first.getBytes(java.nio.charset.StandardCharsets.UTF_8), second.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
