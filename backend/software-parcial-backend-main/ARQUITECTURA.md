# Backend Spring Boot: cuatro capas

ArqNova conserva Java 17 y Spring Boot 3.3.9. El código de aplicación se distribuye en cuatro paquetes principales bajo `com.arqnova`; la clase de arranque `ArqNovaApplication` permanece en el paquete raíz para que Spring detecte componentes, entidades y repositorios.

| Capa | Contenido | Dependencias internas permitidas |
|---|---|---|
| `modelo` | POJOs, `entidades`, `enums`, `dto` y `excepciones` | Modelo |
| `servicios` | Casos de uso, implementaciones, IA, persistencia de proyectos, seguridad e inicialización | Modelo y repositorios |
| `controladores` | REST, STOMP/WebSocket, filtros, configuración de transporte y manejo global de errores | Modelo y servicios |
| `repositorios` | Almacenes de usuarios y sesiones, mapeo de persistencia y repositorios `jpa` | Modelo |

## Distribución

```text
com.arqnova
├── ArqNovaApplication
├── modelo
│   ├── entidades
│   ├── enums
│   ├── dto
│   │   ├── project
│   │   ├── request
│   │   └── response
│   └── excepciones
├── servicios
│   ├── impl
│   ├── persistencia
│   ├── seguridad
│   └── configuracion
├── controladores
│   ├── configuracion
│   ├── seguridad
│   └── excepciones
└── repositorios
    └── jpa
```

Las entidades JPA y los modelos del dominio mantienen sus clases y responsabilidades originales; se agrupan dentro de `modelo` sin cambiar tablas, columnas ni migraciones. Los DTO mantienen los mismos campos JSON.

`DepartamentoService` concentra las reglas de departamentos que antes estaban en el controlador: contexto, autorización, duplicados, normalización, desactivación y reactivación. `UsuarioConsultaService` permite a los filtros y a WebSocket consultar usuarios mediante la capa de servicios. Los permisos y mensajes se conservan.

La configuración web, el filtro de autenticación y el manejador HTTP de excepciones pertenecen a `controladores`. La firma de tokens, las contraseñas y el seed pertenecen a `servicios`. Se mantienen dentro de las cuatro capas, sin crear capas principales adicionales.

## Compatibilidad y pruebas

- Las rutas REST, los DTO, los encabezados y los destinos STOMP se conservan.
- Las ocho migraciones Flyway existentes no se modifican.
- Los proveedores Gemini/GroqCloud mantienen su configuración y respaldo automático.
- La generación de proyectos Spring Boot y Flutter conserva sus plantillas y formatos de descarga.
- Las pruebas existentes se trasladan con sus paquetes; se agregan reglas de arquitectura y pruebas de las reglas extraídas de departamentos.
- Las pruebas de arquitectura rechazan código fuera de las cuatro capas y dependencias en sentido incorrecto.

Esta estructura es una arquitectura por capas con Spring Boot. No se presenta como arquitectura hexagonal. Es una reorganización técnica de las funcionalidades existentes, sin atribuir nuevas funcionalidades al segundo examen.

## Lectura de diagramas manuscritos

El análisis clasifica cada relación por el símbolo visible en sus propios extremos. Un codo, un borde de clase o una multiplicidad no se consideran rombos. Los nombres se transcriben literalmente y no se infieren claves primarias a partir de `Id`.

Cuando interviene Groq, el servicio revisa los títulos con una imagen de recortes ampliados de las clases ubicadas por el análisis inicial (hasta 12 clases). Esta revisión es una llamada adicional con un máximo de 512 tokens de salida. Los cambios de nombre actualizan las referencias de las relaciones, incluida la clase de asociación, sin cambiar tipos ni multiplicidades. Las respuestas incompletas o con nombres duplicados no se aplican. Si falla la revisión o un título no se lee, se conserva el resultado inicial y se advierte al usuario para que lo revise. Las fotos sin posiciones utilizables o sin soporte de decodificación mantienen el análisis original.
