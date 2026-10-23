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

Con Gemini y Groq, el servicio realiza una segunda lectura especializada de las conexiones sobre la **foto original**, sin reconstruir el grafo a partir de recortes de posiciones aproximadas. Los recortes opcionales de títulos solo pueden confirmar un trazo vertical final añadido; nunca sustituyen una palabra por la de otro recuadro. Los triángulos alrededor de una asociación muchos a muchos reciben una comprobación visual específica: se distingue la unión discontinua al centro de las líneas realmente dibujadas hasta las cajas. Solo se eliminan conexiones que esa comprobación confirma como inexistentes; un triángulo real se conserva. Una unión ilegible impide insertar el resultado. Esta revisión confirma el grafo UML completo, sus extremos, multiplicidades y clases de asociación; no transforma clases de asociación en tablas puente con relaciones nuevas. Los nombres solo admiten correcciones pequeñas y unívocas; no se intercambian entidades ni se trasladan atributos. Las multiplicidades ausentes se mantienen vacías, sin inventar `1`. Las referencias desconocidas, nombres ambiguos y multiplicidades inválidas rechazan la revisión de forma atómica. Una lectura `UNCLEAR` no se devuelve como un diagrama listo para insertar.

La importación desde imagen inserta las clases dibujadas, sin crear una clase adicional solo porque una relación sea muchos a muchos; la creación automática manual y por texto se conserva. El lienzo conserva las posiciones reconocidas de las clases de asociación y admite su vínculo explícito con cualquier nombre y multiplicidad. Una clase de asociación puede mantener además relaciones propias con otras clases.

Groq recibe el análisis inicial en un formato interno de arrays compactos; el servicio reconstruye el JSON habitual antes de validarlo. La revisión de conexiones ajusta su presupuesto al número de clases, hasta 4096 tokens de salida; Gemini reserva al menos 2048 para evitar truncar su razonamiento. Si el proveedor indica una pausa `Retry-After` de hasta diez segundos, se permite un único reintento de la revisión; los errores de cuota no se convierten en un resultado listo con conexiones sin confirmar. Si Groq rechaza el presupuesto inicial de salida por su cuota, se permite un único intento con un máximo de 768 tokens; nunca se aceptan respuestas truncadas. `GROQ_IMAGE_MAX_TOKENS` configura el presupuesto inicial.

Al insertar el resultado se puede añadir al modelo existente o **reemplazar explícitamente el lienzo con la imagen**. El reemplazo elimina las conexiones de intentos anteriores, se persiste y se comunica a los colaboradores. La vista previa identifica la clase de asociación de cada vínculo.
