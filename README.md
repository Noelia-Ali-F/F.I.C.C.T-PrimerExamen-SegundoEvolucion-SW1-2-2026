# ArqNova

**ArqNova** es una plataforma de modelado UML colaborativo que integra edición en tiempo real, inteligencia artificial, interoperabilidad XMI y generación automática de software. El repositorio reúne la aplicación web, el backend, PostgreSQL y la aplicación móvil Android desarrollados para el examen de Software I.

## Funcionalidades implementadas

### Acceso y administración de usuarios

- Inicio de sesión, registro, consulta de sesión y cierre de sesión.
- Validación de credenciales y del estado activo del usuario.
- Protección de rutas mediante guards e interceptor HTTP.
- Control de acceso según los roles internos del sistema.
- Interfaz orientada a **Administrador** y **Usuario/Colaborador**.
- Creación, consulta, edición, activación, desactivación y eliminación de usuarios.
- Gestión de avatar y contraseñas almacenadas mediante hash.
- Autenticación mediante token.

### Gestión de proyectos UML

- Creación, listado, búsqueda, edición y eliminación de proyectos.
- Nombre, descripción, creador, estado, fecha de creación y última modificación.
- Validación de datos y prevención de nombres duplicados.
- Confirmación antes de eliminar y acceso directo al editor.
- Persistencia en PostgreSQL y recuperación del diagrama guardado.

### Editor de diagramas de clases

- Lienzo UML interactivo y editable.
- Creación, edición, movimiento y eliminación de clases.
- Creación, edición y eliminación de atributos y métodos.
- Tipos UML compatibles: `String`, `Integer`, `Long`, `Boolean`, `Double`, `Float`, `BigDecimal`, `Date`, `DateTime` y `UUID`.
- Definición de identificadores o claves primarias.
- Relaciones de asociación, agregación, composición y herencia.
- Edición de origen, destino, nombre y multiplicidades.
- Inspector visual, zoom, restablecimiento y guardado integral del modelo.
- Modelo interno reutilizable por colaboración, XMI, IA y generadores de código.

### Colaboración en tiempo real

- Creación de sesiones colaborativas por proyecto.
- Código y enlace de invitación para usuarios autenticados.
- Identificación del anfitrión y lista de participantes conectados.
- Sincronización UML mediante WebSocket/STOMP.
- Cursores remotos con posición, color y nombre del colaborador.
- Sincronización de clases, atributos, métodos, relaciones y movimientos.
- Actualización de participantes al ingresar o abandonar la sesión.

### Concurrencia y bloqueos

- Bloqueo temporal de elementos UML durante la edición.
- Identificación del usuario que está editando un elemento.
- Renovación, liberación y expiración automática de locks.
- Prevención de modificaciones simultáneas incompatibles.

### Bitácora colaborativa

- Registro de ingreso y salida de participantes.
- Registro de creación, modificación, eliminación y movimiento de elementos UML.
- Identificación del usuario, acción, elemento afectado y fecha/hora.
- Panel actualizado en tiempo real.
- Persistencia de actividades colaborativas en PostgreSQL.

### Modelado mediante inteligencia artificial

- Interpretación de instrucciones UML en lenguaje natural.
- Entrada por texto o reconocimiento de voz desde el editor web.
- Creación y modificación asistida de clases, atributos y relaciones.
- Integración con Google OpenAI mediante configuración externa.
- Validación de respuestas antes de incorporarlas al modelo editable.
- Manejo controlado de errores y respaldo determinista para comandos soportados.

### Reconstrucción desde imagen

- Carga y análisis de imágenes de diagramas.
- Reconocimiento de clases, atributos, tipos y relaciones mediante IA.
- Conversión al modelo interno y generación de elementos UML reales y editables.
- Validación y mensajes de error para resultados incompletos.

### Interoperabilidad XMI

- Importación de archivos `.xmi` y `.xml` de Enterprise Architect.
- Validación XML/XMI y vista previa antes de insertar.
- Interpretación de clases, atributos, tipos, identificadores, relaciones y multiplicidades.
- Conversión a elementos editables dentro del lienzo actual.
- Exportación del modelo vigente como archivo XMI descargable.
- Identificadores estables y advertencias para elementos no soportados.

### Generación de backend Spring Boot

- Validación previa del modelo UML.
- Generación de un proyecto Maven descargable en ZIP.
- Clases UML transformadas en entidades JPA y atributos convertidos a tipos Java.
- Generación de `@Id` y relaciones `@OneToOne`, `@OneToMany`, `@ManyToOne` y `@ManyToMany`.
- Repositorios Spring Data JPA, servicios CRUD y controladores REST.
- Endpoints `GET`, `POST`, `PUT` y `DELETE`.
- `pom.xml`, configuración PostgreSQL, `.gitignore` y README de ejecución.
- Proyecto preparado para IntelliJ IDEA, Eclipse o VS Code.

### Generación de recursos Postman

- Colección derivada de los controladores generados.
- Carpetas y solicitudes CRUD por entidad.
- Bodies JSON de ejemplo para `POST` y `PUT`.
- Variables `baseUrl` y `port`.
- Descarga independiente de `collection.json`.
- Inclusión de la misma colección dentro del ZIP del backend.

### Generación de frontend Flutter

- Generación independiente de un proyecto Flutter desde el modelo UML.
- Modelos Dart equivalentes a las entidades.
- Servicios HTTP para los endpoints REST.
- Configuración centralizada de la URL del backend.
- Pantallas CRUD básicas por recurso.
- Estructura organizada y exportación en ZIP.
- Separación respecto de la aplicación móvil ArqNova existente.

### Aplicación móvil Android

- Aplicación **ArqNova Mobile** desarrollada con Flutter.
- Asistente IA local por texto y voz.
- Transcripción mediante `speech_to_text`.
- Intenciones locales: registrar, consultar, actualizar y eliminar.
- Extracción de entidad y datos desde lenguaje natural.
- Confirmación para operaciones destructivas.
- Comunicación HTTP con el backend Spring Boot generado.
- Estados de ejecución y mensajes claros de éxito o error.
- Configuración del servidor mediante `API_BASE_URL`.

### Operación offline y sincronización móvil

- Estados `ONLINE`, `OFFLINE` y `SYNCING`.
- Interpretación local de comandos sin conexión.
- Operaciones pendientes almacenadas en SQLite.
- Estados `PENDIENTE`, `SINCRONIZADO` y `ERROR`.
- Pantalla de operaciones pendientes y sincronizadas.
- Sincronización automática al recuperar conectividad y sincronización manual.
- Clasificación de errores temporales y permanentes.
- Contador de intentos, último error y fecha de última sincronización.
- UUID local e `Idempotency-Key` para evitar duplicados.
- Conservación de pendientes cuando el servidor no está disponible.

## Arquitectura general

```text
┌──────────────────────────┐
│ Angular / Editor UML     │
│ HTTP + WebSocket/STOMP   │
└─────────────┬────────────┘
              │
┌─────────────▼────────────┐       ┌──────────────────────────┐
│ Spring Boot / API REST   │◄─────►│ Flutter Android          │
│ Colaboración y generación│       │ IA local + Offline       │
└─────────────┬────────────┘       └──────────────────────────┘
              │
┌─────────────▼────────────┐
│ PostgreSQL + Flyway      │
│ UML JSON + colaboración  │
└──────────────────────────┘
```

## Stack tecnológico

### Frontend web

- Angular 21 y TypeScript 5.9.
- Angular Router, Forms, CDK y Material.
- RxJS y STOMP.js.
- Tailwind CSS y SCSS.
- Web Speech API para voz.
- APIs DOM/XML del navegador para XMI.
- Vitest y jsdom para pruebas.
- Nginx para publicación y proxy.

### Backend

- Java 17 y Spring Boot.
- Spring Web, WebSocket/STOMP, Data JPA y Validation.
- Spring Security Crypto.
- PostgreSQL Driver y Flyway.
- Springdoc OpenAPI / Swagger UI.
- Lombok, Maven y Maven Wrapper.
- JUnit, Mockito y Spring Boot Test.

### Persistencia e IA

- PostgreSQL 16.
- Persistencia relacional de usuarios, proyectos, sesiones y actividades.
- Modelo UML serializado en JSON/JSONB.
- Migraciones versionadas con Flyway.
- Google OpenAI para instrucciones UML y análisis de imágenes.
- Reconocimiento de voz web y móvil.

### Aplicación móvil

- Flutter 3.29.3 administrado con FVM y Dart 3.7.2.
- Android como plataforma objetivo.
- `http`, `speech_to_text`, `connectivity_plus`, `sqflite` y `uuid`.
- `flutter_test` y `flutter_lints`.

### Infraestructura

- Docker y Docker Compose.
- Contenedores de frontend, backend y PostgreSQL.
- Nginx como servidor web y proxy de API/WebSocket.
- Volúmenes persistentes para PostgreSQL y avatares.
- Variables de entorno para configuración sensible.
- Ejecución local y despliegue en AWS EC2.

## Estructura del repositorio

```text
backend/software-parcial-backend-main/   Backend Spring Boot
frontend/arqnova-frontend/               Editor web Angular
mobile/arqnova-mobile/                   Aplicación Flutter Android
deploy/                                  Configuración de despliegue
scripts/                                 Scripts operativos
docker-compose.yml                       Entorno integrado
docker-compose.dev.yml                   Entorno de desarrollo
.env.example                             Variables locales de ejemplo
.env.aws.example                         Variables AWS de ejemplo
```

## Documentación del examen

- [Matriz de cumplimiento](docs/MATRIZ_CUMPLIMIENTO_EXAMEN.md).
- [Estándares de programación](docs/ESTANDARES_DE_PROGRAMACION.md).
- [Videotutoriales y guiones de demostración](docs/tutoriales/README.md).

## Ejecución con Docker

Requisitos: Git, Docker Engine y Docker Compose v2.

```bash
cp .env.example .env
# Complete las variables obligatorias de .env.
docker compose config
docker compose up --build -d
docker compose ps
```

| Servicio | Dirección predeterminada |
|---|---|
| Frontend ArqNova | `http://localhost:8081` |
| Backend ArqNova | `http://localhost:8080` |
| Swagger UI | `http://localhost:8080/swagger-ui/index.html` |
| OpenAPI JSON | `http://localhost:8080/v3/api-docs` |
| PostgreSQL | `localhost:5432` |

## Ejecución y pruebas por módulo

### Backend

```bash
cd backend/software-parcial-backend-main
./mvnw clean test
./mvnw spring-boot:run
```

Para generar `target/arqnova-backend-1.0.0-SNAPSHOT.jar`:

```bash
./mvnw -DskipTests package
```

### Frontend web

```bash
cd frontend/arqnova-frontend
npm ci --legacy-peer-deps
npm test -- --watch=false
npm run build
npm start
```

Durante desarrollo se publica normalmente en `http://localhost:4200`.

### Aplicación móvil Android con FVM

```bash
cd mobile/arqnova-mobile
fvm flutter pub get
fvm flutter analyze
fvm flutter test
fvm flutter build apk --debug
```

APK generado:

```text
mobile/arqnova-mobile/build/app/outputs/flutter-apk/app-debug.apk
```

Para ejecutar en un teléfono con depuración USB:

```bash
fvm flutter devices
fvm flutter run
```

## Variables de entorno principales

| Variable | Uso |
|---|---|
| `DB_NAME` | Nombre de PostgreSQL |
| `DB_USER` | Usuario PostgreSQL |
| `DB_PASSWORD` | Contraseña PostgreSQL |
| `AUTH_TOKEN_SECRET` | Firma de tokens |
| `OPENAI_API_KEY` | Acceso a Google OpenAI |
| `OPENAI_MODEL` | Modelo OpenAI seleccionado |
| `CORS_ALLOWED_ORIGINS` | Orígenes web permitidos |
| `FRONTEND_API_URL` | URL de API para el frontend |
| `API_BASE_URL` | URL consumida por ArqNova Mobile |

Los secretos reales no deben almacenarse en Git. Use los archivos de ejemplo como plantilla.

## Despliegue en AWS EC2

```bash
git clone https://github.com/Noelia-Ali-F/F.I.C.C.T-PrimerExamen-SW1-2-2026.git arqnova
cd arqnova
cp .env.aws.example .env
# Configure los valores del entorno AWS.
docker compose --env-file .env config
docker compose --env-file .env up --build -d
docker compose ps
```

Debe publicarse el puerto del frontend en el Security Group. PostgreSQL debe permanecer restringido a localhost o a la red privada. El backend solo necesita exposición directa cuando no se utiliza el proxy web o cuando la aplicación móvil debe consumirlo.

## Alcance y consideraciones

- El JSON es la representación interna y la persistencia JSONB del modelo; no es una opción visible de importación/exportación.
- La interoperabilidad visible del editor se realiza mediante XMI/XML.
- XMI se concentra en diagramas de clases, atributos, identificadores, asociaciones y multiplicidades compatibles con el modelo interno.
- Los proyectos generados ofrecen una base CRUD ejecutable; las reglas específicas dependen del dominio modelado.
- El asistente móvil interpreta comandos básicos localmente, pero las operaciones REST requieren acceso al backend.
- La plataforma móvil objetivo es exclusivamente Android.

## Identidad técnica

- Backend: `com.arqnova` / `arqnova-backend`.
- Frontend: `arqnova-frontend`.
- Aplicación móvil: `arqnova_mobile`.
- Package Android: `com.arqnova.mobile`.

## Actualización del editor y la IA

La integración actual usa OpenAI. Consulta [OPENAI.md](backend/software-parcial-backend-main/OPENAI.md) para configurar los modelos y la clave en variables de entorno. La generación desde imágenes conserva composiciones, métodos y posiciones relativas; las multiplicidades ausentes se señalan para revisión. El exportador XMI conserva las clases de asociación de muchos a muchos y utiliza los colores predeterminados de Enterprise Architect.
