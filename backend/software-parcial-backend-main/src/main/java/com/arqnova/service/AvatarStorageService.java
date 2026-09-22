package com.arqnova.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class AvatarStorageService {

    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;
    private static final Set<String> ALLOWED_TYPES = Set.of("image/png", "image/jpeg", "image/webp");

    @Value("${app.avatars.dir:./uploads/avatars}")
    private String avatarsDir;

    private Path avatarsPath;

    @PostConstruct
    void initialize() {
        avatarsPath = Paths.get(avatarsDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(avatarsPath);
        } catch (IOException exception) {
            throw new IllegalStateException("No se pudo inicializar el almacenamiento de avatares", exception);
        }
    }

    public String almacenarAvatar(MultipartFile avatar, String usuarioResponsable) {
        validar(avatar);
        String fileName = UUID.randomUUID() + extension(avatar.getContentType());
        Path destination = resolve(fileName);
        try {
            Files.copy(avatar.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            throw new IllegalStateException("No se pudo almacenar el avatar", exception);
        }
        return "/api/v1/admin/usuarios/avatars/" + fileName;
    }

    public Resource cargarAvatar(String fileName) {
        try {
            Resource resource = new UrlResource(resolve(fileName).toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new IllegalArgumentException("Avatar no encontrado");
            }
            return resource;
        } catch (MalformedURLException exception) {
            throw new IllegalArgumentException("Nombre de avatar inválido", exception);
        }
    }

    public String tipoContenido(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".webp")) return "image/webp";
        return "image/jpeg";
    }

    private void validar(MultipartFile avatar) {
        if (avatar == null || avatar.isEmpty()) throw new IllegalArgumentException("El avatar está vacío");
        if (avatar.getSize() > MAX_FILE_SIZE) throw new IllegalArgumentException("El avatar excede el límite de 5 MB");
        if (!ALLOWED_TYPES.contains(avatar.getContentType())) {
            throw new IllegalArgumentException("Formato de avatar no permitido. Usa PNG, JPEG o WEBP");
        }
    }

    private Path resolve(String fileName) {
        if (fileName == null || !fileName.matches("[a-f0-9-]+\\.(png|jpg|webp)")) {
            throw new IllegalArgumentException("Nombre de avatar inválido");
        }
        Path path = avatarsPath.resolve(fileName).normalize();
        if (!path.startsWith(avatarsPath)) throw new IllegalArgumentException("Ruta de avatar inválida");
        return path;
    }

    private String extension(String contentType) {
        if ("image/png".equals(contentType)) return ".png";
        if ("image/webp".equals(contentType)) return ".webp";
        return ".jpg";
    }
}
