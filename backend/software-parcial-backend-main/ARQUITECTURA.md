# Arquitectura hexagonal dentro de las cuatro capas

La persistencia también dispone de adaptadores NoSQL en `repositorios/dynamodb`. El perfil `dynamodb` reemplaza JPA/PostgreSQL por el SDK de DynamoDB sin cambiar los puertos de los servicios. La activación y sus límites se documentan en [DynamoDB](../../docs/dynamodb.md). Durante la transición se conserva PostgreSQL para una reversión controlada.

ArqNova mantiene Java 17, Spring Boot y exactamente los cuatro paquetes principales exigidos por el examen: **modelo, servicios, controladores y repositorios**. La arquitectura hexagonal se aplica mediante inversión de dependencias; no se crea una quinta capa principal ni se cambia el contrato del sistema.

| Capa obligatoria | Papel en la arquitectura | Dependencias |
|---|---|---|
| `modelo` | Entidades de dominio, valores, excepciones y contratos de datos | Sin dependencias de las otras capas; las entidades de dominio no contienen JPA/Hibernate |
| `servicios` | Casos de uso, puertos de entrada y puertos de salida | Modelo y otros contratos del núcleo; sin Spring, JPA, HTTP, almacenamiento ni implementaciones externas |
| `controladores` | Adaptadores de entrada REST, WebSocket, seguridad de transporte y composición Spring | Puertos y modelos; `configuracion` ensambla las implementaciones |
| `repositorios` | Adaptadores de salida: JPA/PostgreSQL, IA, archivos, criptografía, serialización y generación | Implementa puertos definidos en `servicios`; depende del núcleo |

```text
com.arqnova
├── ArqNovaApplication.java
├── modelo/
│   ├── entidades/             POJOs de dominio, sin anotaciones de persistencia
│   ├── dto/                   Contratos existentes y validación de entrada
│   ├── generacion/            Valores de los artefactos generados
│   ├── seguridad/             Claims del token
│   └── excepciones/
├── servicios/
│   ├── puertos/entrada/       Proyectos, colaboración, bloqueos, consultas e imágenes
│   ├── puertos/salida/        Contratos de persistencia, IA, archivos y seguridad
│   ├── impl/                  Casos de uso independientes del framework
│   └── persistencia/          Reglas de acceso, membresías y versiones del proyecto
├── controladores/
│   ├── adaptadores/           Conversión MultipartFile → BinaryFile
│   ├── configuracion/        HexagonalConfiguration y configuración de transporte
│   ├── seguridad/
│   └── excepciones/
└── repositorios/
    ├── jpa/entidades/         Mapeo ORM original, tablas y callbacks de auditoría
    ├── jpa/adaptadores/       Traducción explícita entre JPA y dominio
    ├── ia/                   Intérprete y pipeline Gemini/Groq/OpenAI existente
    ├── archivos/             Avatares y puente multipart del adaptador
    ├── seguridad/            HMAC y BCrypt
    ├── serializacion/        Copia JSON de grafos
    └── generacion/           Plantillas Spring Boot, Flutter y Postman
```

## Reglas de dependencia

Los puertos pertenecen al núcleo. Por ejemplo, `UmlProjectPersistenceService` utiliza `ProjectDataPort`, `ProjectMemberDataPort`, `UmlDiagramDataPort` y `UserDataPort`; no importa repositorios Spring Data. Los adaptadores `Jpa*Adapter` implementan esos puertos, convierten los datos a POJOs y mantienen las consultas y los bloqueos existentes.

Los controladores reciben casos de uso. Los archivos se convierten en `BinaryFile` antes de cruzar el puerto; el núcleo no conoce `MultipartFile`. Las implementaciones externas de IA permanecen juntas para conservar íntegros los prompts, el respaldo entre proveedores, los límites y la revisión de relaciones. Los generadores siguen entregando los mismos formatos y plantillas; la organización interna de los proyectos descargados no se cambia por esta refactorización.

`HexagonalConfiguration` es la raíz de composición. Crea los servicios y aplica las mismas políticas transaccionales y de solo lectura que antes estaban declaradas con anotaciones en los casos de uso. Las escrituras de proyecto y membresía permanecen en una misma transacción. El bloqueo pesimista del diagrama se mantiene en el adaptador JPA. `DataSeeder` conserva su condición de perfil `seed` y `FORCE_SEED`.

## Compatibilidad y verificación

- Se conservan las rutas REST, DTO, encabezados, roles, versiones del modelo y destinos STOMP.
- No se modifican tablas, columnas, migraciones Flyway ni datos guardados.
- Los algoritmos de IA, XMI, generación, firmas y contraseñas se conservan detrás de sus puertos.
- Las pruebas de arquitectura exigen las cuatro capas e impiden dependencias desde el núcleo hacia Spring, JPA o adaptadores.
- La integración con PostgreSQL prueba persistencia, permisos, versiones y rollback si falla la membresía después de guardar el proyecto.
- Los casos de uso también se prueban con implementaciones de puertos en memoria, sin nube ni HTTP.

Esta es una reorganización técnica. No atribuye nuevas funcionalidades al segundo examen.

La organización de Angular y Flutter se describe en [la arquitectura del proyecto](../../docs/arquitectura-hexagonal.md).

## Lectura de diagramas manuscritos

El análisis clasifica cada relación por el símbolo visible en sus propios extremos. Un codo, un borde de clase o una multiplicidad no se consideran rombos. Los nombres se transcriben literalmente y no se infieren claves primarias a partir de `Id`.

Con Gemini y Groq, el servicio realiza una segunda lectura especializada de las conexiones sobre la **foto original**, sin reconstruir el grafo a partir de recortes de posiciones aproximadas. Los recortes opcionales de títulos solo pueden confirmar un trazo vertical final añadido; nunca sustituyen una palabra por la de otro recuadro. Todas las posibles terceras clases de una asociación muchos a muchos reciben una comprobación visual específica, aunque la primera lectura haya omitido una o ambas conexiones: se distingue la unión discontinua al centro de las líneas realmente dibujadas hasta las cajas. Solo se eliminan conexiones que esa comprobación confirma como inexistentes; un triángulo real se conserva. Una unión ilegible impide insertar el resultado. Esta revisión confirma el grafo UML completo, sus extremos, multiplicidades y clases de asociación; no transforma clases de asociación en tablas puente con relaciones nuevas. Los nombres solo admiten correcciones pequeñas y unívocas; no se intercambian entidades ni se trasladan atributos. Las multiplicidades ausentes se mantienen vacías, sin inventar `1`. Las referencias desconocidas, nombres ambiguos y multiplicidades inválidas rechazan la revisión de forma atómica. Una lectura `UNCLEAR` no se devuelve como un diagrama listo para insertar.

La importación desde imagen inserta las clases dibujadas, sin crear una clase adicional solo porque una relación sea muchos a muchos; la creación automática manual y por texto se conserva. El lienzo conserva las posiciones reconocidas de las clases de asociación y admite su vínculo explícito con cualquier nombre y multiplicidad. Una clase de asociación puede mantener además relaciones propias con otras clases.

Groq recibe el análisis inicial en un formato interno de arrays compactos; el servicio reconstruye el JSON habitual antes de validarlo. La revisión de conexiones ajusta su presupuesto al número de clases, hasta 4096 tokens de salida; Gemini reserva al menos 2048 para evitar truncar su razonamiento. Si el proveedor indica una pausa `Retry-After` de hasta diez segundos, se permite un único reintento de la revisión. Para cuotas de tokens por minuto, la pausa puede llegar a 45 segundos, para que las comprobaciones de la misma foto no compitan con su primera lectura; no se reintenta indefinidamente ni se espera una cuota diaria; los errores de cuota no se convierten en un resultado listo con conexiones sin confirmar. Si Groq rechaza el presupuesto inicial de salida por su cuota, se permite un único intento con un máximo de 768 tokens; nunca se aceptan respuestas truncadas. `GROQ_IMAGE_MAX_TOKENS` configura el presupuesto inicial.

Al insertar el resultado se puede añadir al modelo existente o **reemplazar explícitamente el lienzo con la imagen**. El reemplazo elimina las conexiones de intentos anteriores, se persiste y se comunica a los colaboradores. La vista previa identifica la clase de asociación de cada vínculo.
