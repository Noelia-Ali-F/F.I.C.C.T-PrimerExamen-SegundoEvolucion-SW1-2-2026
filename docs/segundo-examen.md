# Segundo examen: implementación incremental

La referencia funcional es la segunda tabla de **3.1.2 Priorizar Casos de Uso** del documento del proyecto: ocho casos web y tres móviles. La descripción corregida del CU10 corresponde a consultar actividades.

## Primera entrega técnica: conversaciones y memoria

Se incorpora una conversación persistente por proyecto, compartida por sus integrantes. Cada interacción conserva solicitante, modo, pregunta, respuesta, observaciones, versión del modelo analizado, fecha y estado. Los modos **Consultar** y **Revisar** no modifican el diagrama. El modo opcional **Modificar diagrama guardado** aplica un lote validado desde el servidor. El asistente anterior de edición por texto/voz y el reconocimiento de imágenes mantienen sus contratos.

| Caso | Cobertura de esta entrega |
| --- | --- |
| CU1 | Mensajes compartidos por proyecto, identificación del autor y notificaciones WebSocket |
| CU2 | Contexto del diagrama guardado, conversación reciente del grupo e historial reciente del participante |
| CU3 | Recuperación paginada al volver a abrir; estados persistentes y detección de solicitudes interrumpidas |
| CU4 y CU7 | Modo MODEL: interpretación existente, validación de lotes en servidor, comprobante transaccional, control de versión/bloqueos y sincronización del modelo guardado; habilitación independiente |
| CU5 | Modo de consulta con explicaciones y ejemplos sin modificar el lienzo |
| CU6 | Revisión con observaciones y referencias a elementos existentes; muestra la versión revisada |
| CU8 | Adaptador independiente DynamoDB para memoria, compatible con PostgreSQL/JPA activos |
| CU9 | Flutter: acceso con cuenta web, proyectos compartidos, visor de diagramas con zoom/desplazamiento y consulta de clases/relaciones; solo lectura |
| CU10 | Historial paginado por proyecto; une actividad humana e intervenciones de IA con autores y fechas |
| CU11 | Integración FCM preparada: FID por usuario, avisos posteriores al commit y recepción móvil; activación y prueba real quedan para el entorno local |

La parte web/backend está activada en AWS desde el 2 de noviembre de 2026. La comprobación posterior verificó autenticación, memoria compartida, acceso y modelado idempotente; no consumió APIs de IA. Los casos móviles continúan pendientes de prueba en Android. Ver [despliegue AWS](despliegue-segundo-examen-aws.md).

## Persistencia híbrida y arquitectura

No es necesario reemplazar PostgreSQL para cumplir CU8. Se mantiene la persistencia actual de usuarios, proyectos, diagramas, sesiones y actividades. La nueva tabla `arqnova-ai-memory` almacena únicamente conversaciones e índices de memoria.

El núcleo sigue dentro de las cuatro capas: valores en `modelo/ia`, casos de uso y puertos en `servicios`, REST/configuración en `controladores` y adaptadores de proveedor/memoria/mensajería en `repositorios/ia/memoria`. Angular añade modelos de dominio, puerto/caso de uso y adaptador HTTP/STOMP; el componente es presentación.

**No activar `SPRING_PROFILES_ACTIVE=prod,dynamodb` para esta funcionalidad:** ese perfil pertenece a la adaptación anterior para cambiar toda la base. La memoria nueva utiliza variables independientes y no reemplaza el transaction manager de JPA.

## Configuración y AWS

Por defecto `AI_CONVERSATIONS_ENABLED=false`: la aplicación conserva sus funciones aunque la tabla nueva todavía no exista. Para habilitar:

```text
AI_CONVERSATIONS_ENABLED=true
AI_MODELING_ENABLED=true
AI_MEMORY_TABLE=arqnova-ai-memory
AWS_REGION=us-east-2
```

`AI_MEMORY_ENDPOINT` solo se usa en pruebas locales y debe permanecer vacío en AWS. La plantilla `deploy/ai-memory-cloudformation.json` crea una tabla con claves `pk`/`sk` y un instance profile de EC2 limitado a esa tabla. La instancia necesita ese perfil y acceso a metadata desde Docker. No se requieren nuevas access keys dentro del código.

Infraestructura preparada y verificada en Ohio: stack `arqnova-ai-memory` creado, tabla activa con pago por uso y rol asignado a EC2. Se comprobó acceso desde el host y Docker mediante credenciales temporales del rol. El chat y MODEL se activaron mediante el override AWS, conservando las imágenes anteriores y PostgreSQL. No se migran los datos existentes a NoSQL. La migración aditiva V9 crea únicamente `ai_model_mutation_receipts`, necesaria antes de activar el modo MODEL. El nuevo panel, **Chat de equipo**, está separado de **Editar diagrama** dentro del asistente.

## API y flujo

- `GET /api/v1/projects/{projectId}/ai/status`: configuración del módulo.
- `GET /api/v1/projects/{projectId}/ai/messages`: historial reciente y cursor de paginación.
- `GET /api/v1/projects/{projectId}/ai/messages/{requestId}`: estado de una interacción.
- `POST /api/v1/projects/{projectId}/ai/messages`: acepta `requestId`, `mode` (`ASK`, `REVIEW` o `MODEL`) e `instruction`.
- `/topic/ai/{projectId}`: notifica cambios de estado; las suscripciones comprueban pertenencia al proyecto.

El POST registra la solicitud antes de ejecutar el proveedor en una cola acotada. El identificador de solicitud y los cambios de estado se guardan condicionalmente en DynamoDB. Reenviar la misma solicitud no genera otra ejecución de IA; reutilizar el ID para otro contenido o autor devuelve conflicto. Los fallos del proveedor quedan registrados. Una solicitud pendiente más de tres minutos se marca interrumpida al consultarla; no se reinicia silenciosamente ni se cobra otra respuesta automática.

La memoria usa una partición por proyecto y claves ordenadas para mensajes, referencias de solicitudes e historial por usuario. Los mensajes se almacenan individualmente y el historial se consulta con `Query`, sin leer toda la tabla. En ASK/REVIEW el contexto se reconstruye desde esos datos persistentes, con límites: seis intercambios recientes del grupo, cuatro del usuario y una representación compacta del diagrama. No hay todavía resúmenes semánticos de conversaciones antiguas.

## Consumo y límites

Se reutiliza el pipeline Gemini/Groq existente. Las consultas y revisiones utilizan un presupuesto de salida de 1024 tokens; una pregunta compartida no genera una llamada por participante. El modo MODEL reutiliza el presupuesto del intérprete existente (hasta 8192 tokens), y algunas instrucciones se resuelven con el parser determinista sin consumir una API. Los reintentos de transporte reutilizan su ID. Las pruebas utilizan proveedores simulados, sin gastar cuota de IA.

Las consultas tienen un máximo de 4000 caracteres. El modelo compacto tiene un límite defensivo de 14000 caracteres para evitar consumos inesperados; si lo supera, la interacción falla sin alterar el diagrama. No se afirma que una revisión cubra modelos de tamaño ilimitado. La revisión está vinculada a la versión guardada que se envió al proveedor.

## Verificación

- Pruebas de casos de uso: idempotencia, memoria individual/grupal, acceso, fallos y solicitudes interrumpidas.
- DynamoDB Local: persistencia, paginación, índice por usuario, finalización condicional y solicitudes concurrentes.
- Integración híbrida: PostgreSQL real desechable + DynamoDB Local; confirma que JPA y el diagrama original se conservan.
- Web: historial, reintentos y fusión de eventos sin degradar una respuesta completada a pendiente.
- Regresión: suites existentes de IA, edición, generadores, persistencia y arquitectura.
- Prueba de navegador con dos usuarios: recepción compartida sin recargar, consulta y revisión completadas, recuperación tras recargar y reenvío idempotente. Se utilizó la tabla DynamoDB real, PostgreSQL temporal independiente y proveedor simulado: dos llamadas simuladas, ninguna llamada a una API de IA real. El diagrama mantuvo versión 1, posiciones, clases y relación. Los siete registros de la partición de prueba fueron eliminados al terminar.
- Última validación automática: suite completa del backend con 212 pruebas ejecutadas correctas y una prueba opcional omitida. Frontend: 131 pruebas correctas y compilación de producción correcta. Validación dirigida final del modelado: 39 pruebas correctas, incluidas nueve de integración híbrida, y JAR de Spring Boot construido correctamente.

Para ejecutar los tests NoSQL definir `DYNAMODB_TEST_ENDPOINT=http://127.0.0.1:18001`. La suite completa requiere además una PostgreSQL de pruebas mediante `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER` y `DB_PASSWORD`. Nunca apuntar las pruebas a los datos de AWS.

## Modelado compartido: aplicación única y concurrencia

`AI_MODELING_ENABLED` vale `false` por defecto. El modo MODEL se ofrece únicamente cuando el servidor lo anuncia y mantiene sin cambios el asistente anterior y el análisis de imágenes. Reutiliza el intérprete UML existente; una respuesta que pide aclaración termina sin modificar el modelo.

Un planificador puro valida hasta 50 operaciones sobre una copia profunda. Admite clases, atributos, métodos, asociación, agregación, composición, herencia y dependencia. Una asociación muchos a muchos genera su clase de asociación; si la solicitud identifica una clase de asociación ya incluida, conserva esa clase y sus atributos. Un lote inválido se rechaza completo.

El adaptador PostgreSQL bloquea la fila del diagrama, exige la versión analizada y comprueba permisos de edición y bloqueos manuales. Guarda el diagrama y un comprobante por proyecto/solicitud en una sola transacción. Un reintento encuentra ese comprobante y no vuelve a aplicar los cambios. Si falla la finalización en DynamoDB después del commit SQL, una lectura posterior recupera la respuesta desde el comprobante. La memoria de conversación sigue en DynamoDB; el comprobante es parte del control transaccional del diagrama.

La sincronización del navegador recupera el modelo canónico, sin ejecutar nuevamente las operaciones. Si existe una edición local pendiente, la conserva y avisa del conflicto; no adelanta la versión de su caché. Los eventos manuales también leen el modelo bajo el bloqueo de la fila para no sobrescribir un lote de IA con una copia anterior.

Este mecanismo mantiene el alcance actual de una instancia del backend: los bloqueos de edición y la coordinación del vencimiento son locales al proceso. No se afirma soporte para varias réplicas del backend sin un coordinador distribuido. El perfil anterior que cambia toda la base a DynamoDB no habilita MODEL.

### Comprobación del modelado en navegador

En un entorno aislado, dos usuarios autenticados del mismo proyecto comprobaron la sincronización sin recargar, incluso con el panel de chat cerrado en el segundo navegador. Se creó Libro, una asociación muchos a muchos Cliente–Libro con DetalleClienteLibro y una composición Libro–Venta. Se verificaron en el SVG del segundo usuario un conector discontinuo de clase de asociación y un marcador de composición relleno. Reenviar la solicitud de composición conservó la versión 4 y las tres relaciones, sin duplicar el cambio.

La prueba utilizó el intérprete determinista y respuestas locales simuladas del proveedor, sin llamadas a APIs de IA reales. Una respuesta de simulación con formato incorrecto fue rechazada sin modificar el modelo; después de corregir la simulación, la composición se aplicó correctamente. Se eliminaron únicamente los 13 registros de la partición de esa prueba y se detuvieron la PostgreSQL temporal y los auxiliares. La aplicación publicada no fue reiniciada ni modificada.

## Visor móvil e historial del segundo examen

La nueva opción **Ver mis proyectos UML** abre un módulo independiente del asistente local. El módulo conserva las cuatro áreas hexagonales de Flutter: dominio y puerto, casos de uso, adaptador HTTP y presentación. Su puerto no ofrece operaciones para modificar diagramas. La sesión se mantiene en memoria durante la navegación; los datos se consultan con el token de la misma cuenta usada en el sistema web.

El lienzo de solo lectura conserva posiciones, atributos, métodos, multiplicidades, composición, agregación, herencia, dependencia y el enlace discontinuo de clases de asociación. Permite ampliar/desplazar y consultar la lista completa de elementos. El historial combina autores y fechas de actividad humana con consultas, revisiones y modelado de IA. Cada fuente conserva su cursor para recuperar entradas anteriores sin duplicarlas. Si la memoria IA está temporalmente indisponible, la actividad humana permanece visible junto con un aviso.

La API aditiva `GET /api/v1/projects/{projectId}/activities?cursor=&limit=30` consulta actividad de todas las sesiones del proyecto, previa comprobación de pertenencia. Su cursor ordena por fecha y por ID, evitando perder eventos con la misma marca de tiempo. No altera los endpoints anteriores de actividad por sesión.

La dirección del modelador se configura mediante `UML_API_BASE_URL`, separada de `API_BASE_URL`, que sigue controlando el backend generado del asistente local. Validación: 23 pruebas Flutter correctas y `flutter analyze` sin incidencias; cinco pruebas backend dirigidas correctas, incluida paginación real en PostgreSQL y rechazo de usuarios ajenos. La ampliación móvil aún no está desplegada ni se afirma haber probado una notificación real en un teléfono.

## Preparación de notificaciones y continuidad local

El proyecto Firebase **ArqNova Segundo Examen** (`arqnova-segundo-examen`) y la app Android `com.arqnova.mobile` están creados. Se verificó que Cloud Messaging V1 está habilitado. El backend incorpora un puerto de entrega HTTP V1 con OAuth temporal y el móvil utiliza FIDs del SDK actual. La credencial privada del backend se proporcionará en local; no se generó ni se incorporó al repositorio.

La migración V10 añade registros de dispositivos y deduplicación por evento/dispositivo. La identidad del dispositivo se vincula al usuario autenticado; los destinatarios se filtran por pertenencia al proyecto y cuenta habilitada. El envío se programa después del commit y su fallo no afecta a las operaciones del editor. El servidor mantiene `PUSH_NOTIFICATIONS_ENABLED=false` por defecto. El perfil anterior que cambia toda la base a DynamoDB no habilita este adaptador de dispositivos.

Por indicación del usuario, la compilación del APK y la prueba en un teléfono se completarán después de clonar en local. El visor y el registro push apuntan por defecto al backend del emulador (`http://10.0.2.2:8080`), configurable con `UML_API_BASE_URL`. Se prepararon overrides Docker locales para PostgreSQL, memoria DynamoDB Local persistente y credenciales Firebase montadas únicamente cuando se activa push. La integración push permanece desactivada en AWS; la ampliación web/backend sí está desplegada.

Validación final del código: **230 pruebas backend correctas y una opcional omitida**, **30 pruebas Flutter correctas**, `flutter analyze` sin incidencias y la compilación Angular verificada con **131 pruebas correctas**. OAuth, FCM y los proveedores de IA se simularon. No se afirma haber probado recepción real de push ni haber construido el APK.

La guía [Puesta en marcha local del segundo examen](puesta-en-marcha-local-segundo-examen.md) contiene los pasos concretos para clonar, levantar los servicios y continuar esas verificaciones.
