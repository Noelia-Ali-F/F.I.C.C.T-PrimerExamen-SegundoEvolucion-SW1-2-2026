# ArqNova

ArqNova es una plataforma UML colaborativa con inteligencia artificial y generación automática de backend. El repositorio contiene el backend Spring Boot, la aplicación web Angular y la aplicación móvil Flutter del sistema del examen actual.

## Funcionalidades

- autenticación, usuarios, roles y seguridad;
- gestión y persistencia de proyectos UML;
- edición colaborativa en tiempo real mediante WebSocket/STOMP;
- bloqueo temporal de clases, atributos y relaciones;
- edición UML mediante texto o voz con Google Gemini y respaldo determinista;
- reconstrucción de diagramas desde imágenes;
- importación y exportación XMI compatible con Enterprise Architect;
- generación de proyectos Spring Boot con PostgreSQL;
- generación de colecciones Postman;
- asistente móvil local para consumir los backends generados.

## Estructura

```text
backend/software-parcial-backend-main/       Backend ArqNova (Spring Boot)
frontend/arqnova-frontend/                   Frontend ArqNova (Angular)
mobile/arqnova-mobile/                       ArqNova Mobile (Flutter)
docker-compose.yml                           Entorno integrado
docker-compose.dev.yml                       Entorno de desarrollo
```

La estructura física y la identidad compilada de los tres proyectos utilizan los nombres finales de ArqNova.

## Inicio con Docker

Configure las variables de `.env.example` y ejecute:

```bash
docker compose up --build
```

Servicios predeterminados:

- frontend: `http://localhost:8081`;
- backend: `http://localhost:8080`;
- PostgreSQL: puerto `5432` configurable mediante `POSTGRES_PUBLIC_PORT`.

Los volúmenes `postgres_data` y `backend_uploads` conservan sus nombres para mantener los datos existentes.

## Backend

```bash
cd backend/software-parcial-backend-main
./mvnw clean test
./mvnw -DskipTests package
```

El artefacto generado es `target/arqnova-backend-1.0.0-SNAPSHOT.jar`.

## Frontend web

```bash
cd frontend/arqnova-frontend
npm ci --legacy-peer-deps
npm test -- --watch=false
npm run build
```

## Aplicación móvil

```bash
cd mobile/arqnova-mobile
flutter pub get
flutter analyze
flutter test
flutter build apk --debug
```

La dirección del backend móvil se configura mediante `API_BASE_URL`; no se fija una URL de despliegue nueva en este repositorio.

## Configuración sensible

Las claves y secretos se proporcionan mediante variables de entorno. No deben escribirse en documentación ni almacenarse en archivos versionados. Entre las variables utilizadas están `AUTH_TOKEN_SECRET`, `GEMINI_API_KEY`, `GEMINI_MODEL`, `DB_NAME`, `DB_USER` y `DB_PASSWORD`.

## Despliegues externos

Las URLs externas que todavía apunten a despliegues anteriores se mantienen por compatibilidad. Su cambio queda pendiente hasta disponer de un despliegue ArqNova real; no deben sustituirse por direcciones inventadas.

### Despliegue en una máquina AWS

La instancia necesita Git, Docker Engine y Docker Compose v2. Para un despliegue directo desde la instancia:

```bash
git clone https://github.com/Noelia-Ali-F/F.I.C.C.T-PrimerExamen-SW1-2-2026.git arqnova
cd arqnova
cp .env.aws.example .env
# Edite .env y reemplace todos los valores change_me y YOUR_AWS_HOST.
docker compose --env-file .env config --quiet
docker compose --env-file .env up --build -d
docker compose ps
```

En el Security Group publique el puerto del frontend (`8081` por defecto). Publique el backend (`8080`) solo si la aplicación móvil lo necesita y no existe un proxy HTTPS. No exponga PostgreSQL (`5432`) a Internet; restrínjalo a localhost o a la red privada.

También se puede desplegar desde otra máquina con `scripts/deploy-cloud-aws.sh`. Configure primero el alias SSH o `REMOTE_HOST`, copie `.env.aws.example` como `.env.aws` y, si corresponde, establezca `REMOTE_DIR`.
