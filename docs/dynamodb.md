# Migración de la base principal a Amazon DynamoDB

El backend incorpora un adaptador NoSQL para DynamoDB. La activación en AWS es un paso separado: **la instalación existente continúa en PostgreSQL hasta crear la tabla, asociar el rol IAM y verificar la migración**. No se cambia la base de producción automáticamente al actualizar Git.

## Arquitectura y contratos

Se conservan las cuatro capas exigidas: `modelo`, `servicios`, `controladores`, `repositorios`. Los servicios siguen usando los mismos puertos; las nuevas implementaciones viven en `repositorios/dynamodb`. No se modifican las rutas REST, DTO, mensajes WebSocket, lógica de IA, reglas UML, importación/exportación XMI ni componentes web/móviles.

El perfil `dynamodb` desactiva DataSource, Hibernate, Spring Data JPA y Flyway. Los adaptadores PostgreSQL solo se registran cuando ese perfil está ausente; quedan disponibles para reversión y comparación durante la transición. No se usan simultáneamente dos bases para una misma petición.

La base SQLite del móvil sigue siendo almacenamiento local para trabajar sin conexión. Las plantillas de proyectos backend que descarga el generador mantienen su tecnología original; esta migración corresponde a la persistencia del propio sistema ArqNova.

## Modelo NoSQL

Una tabla con claves de texto `pk` (partición) y `sk` (ordenación):

| pk | sk | Contenido lógico |
| --- | --- | --- |
| USER | id | Usuario y hash de contraseña existente |
| DEPARTMENT | id | Departamento |
| PROJECT | id | Proyecto |
| MEMBER | clave compuesta codificada | Membresía y rol |
| DIAGRAM | id | Modelo completo, posiciones y versión |
| SESSION | id | Invitación/sesión colaborativa |
| ACTIVITY | eventId | Historial de colaboración |
| UNIQUE | nombre normalizado | Reserva de nombre/identificador único |

Cada documento lleva `revision` y `payload` (JSON UTF-8 comprimido con gzip). Los documentos grandes usan un manifiesto `chunks` y fragmentos binarios de 280 KiB bajo una partición `BLOB:tipo:id`. No se pierden posiciones, relaciones, atributos ni identificadores del modelo.

`DynamoTransactionManager` conserva las fronteras REQUIRED configuradas en `HexagonalConfiguration`. `DynamoDocumentStore` acumula mutaciones y las confirma juntas mediante `TransactWriteItems`; un fallo previo descarta los cambios. Las revisiones se comprueban condicionalmente para impedir sobrescrituras concurrentes; los conflictos siguen siendo HTTP 409. Las reservas de unicidad también son condicionales. Al crear documentos dependientes se incluye el padre en la transacción, evitando que una eliminación concurrente deje datos huérfanos.

Las consultas por tipo usan `Query` con paginación completa y lecturas consistentes. Los filtros secundarios de listados se aplican en el adaptador. Esto es adecuado para el volumen universitario actual; a gran escala conviene diseñar particiones e índices para cada patrón de acceso, evitando leer todos los usuarios/proyectos/historial.

**Límites explícitos:** AWS limita cada transacción a 100 elementos y 4 MiB. El adaptador usa un margen conservador de 3,5 MB para documentos nuevos y anteriores; cuenta también los fragmentos y sus eliminaciones. Una operación que exceda esos límites se rechaza antes de escribir, sin confirmaciones parciales. Esto incluye eliminar un proyecto con muchos miembros/eventos: la eliminación masiva requeriría un proceso adicional para superar el límite transaccional. Los documentos sin comprimir tienen un máximo defensivo de 32 MiB. No se promete tamaño ilimitado.

Fuentes: [transacciones DynamoDB](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/transaction-apis.html), [permisos de transacciones](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/transaction-apis-iam.html), [DynamoDB Local](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/DynamoDBLocal.html).

## Preparar AWS (us-east-2)

1. En CloudFormation, crear una pila con `deploy/dynamodb-cloudformation.json` y capacidad `CAPABILITY_IAM`. La plantilla crea la tabla `arqnova`, un rol EC2 con acceso solo a esa tabla y su instance profile. La tabla tiene política de retención. Se usa pago por solicitudes; no implica coste cero ni un presupuesto máximo.
2. Asociar el instance profile mostrado en las salidas a la instancia `i-098aace5b0cdfe43a`. Si la instancia ya tiene un rol, revisar su uso antes de sustituirlo; el diagnóstico inicial no encontró uno.
3. Para acceso a IMDSv2 desde Docker, el límite de saltos de respuesta de metadata debe permitir el salto del contenedor (normalmente 2). No desactivar IMDSv2. No hacen falta access keys estáticas.
4. En `.env`, establecer `AWS_REGION=us-east-2` y `DYNAMODB_TABLE=arqnova`. `DYNAMODB_ENDPOINT` debe estar vacío en AWS.

Equivalente por CLI, desde una sesión administrativa de AWS autorizada:

```sh
aws cloudformation deploy --region us-east-2 --stack-name arqnova-dynamodb --template-file deploy/dynamodb-cloudformation.json --capabilities CAPABILITY_IAM
aws cloudformation describe-stacks --region us-east-2 --stack-name arqnova-dynamodb --query 'Stacks[0].Outputs'
# Usar el InstanceProfileName real devuelto arriba:
aws ec2 associate-iam-instance-profile --region us-east-2 --instance-id i-098aace5b0cdfe43a --iam-instance-profile Name=INSTANCE_PROFILE_NAME
aws ec2 modify-instance-metadata-options --region us-east-2 --instance-id i-098aace5b0cdfe43a --http-tokens required --http-put-response-hop-limit 2
```

## Copiar los datos conservando el origen

1. Construir y probar el JAR. Mantener la imagen y configuración anteriores disponibles.
2. Programar una ventana sin escrituras: detener el backend antes de la exportación final y mantenerlo detenido hasta verificar y activar DynamoDB. La copia de ensayo realizada mientras el sitio funciona **no reemplaza** esa exportación final.
3. Obtener un respaldo privado con `pg_dump` y exportar el JSON usando `deploy/export-postgres-dynamodb.sql` con `psql -qAt -v ON_ERROR_STOP=1`. El SQL usa una transacción de solo lectura y snapshot consistente. El JSON contiene datos privados y hashes; guardarlo fuera de Git.
4. Ejecutar preflight, importar en tabla nueva/vacía y verificar:

```sh
python deploy/migrate_dynamodb.py /ruta/privada/export.json --mode preflight
python -m pip install boto3
python deploy/migrate_dynamodb.py /ruta/privada/export.json --mode import --table arqnova --region us-east-2
python deploy/migrate_dynamodb.py /ruta/privada/export.json --mode verify --table arqnova --region us-east-2
```

La herramienta valida relaciones, tamaños y unicidad antes de escribir. Conserva IDs, versiones, fechas, modelos y hashes de contraseña. No sobrescribe documentos diferentes. Puede reanudar una importación incompleta si los documentos ya copiados coinciden exactamente. La copia completa no es una única transacción; el backend debe permanecer detenido hasta acabar. La verificación compara todos los documentos y las reservas de unicidad, no solamente cantidades. No modifica PostgreSQL ni activa el backend.

5. Desplegar el JAR y activar el backend con `docker compose -f docker-compose.dynamodb.yml up -d --no-deps --no-build backend`. Este archivo no inicia PostgreSQL. Conservar su contenedor/volumen mientras se verifica el resultado.
6. Verificar inicio sin JPA/Flyway, autenticación, proyectos, guardado con versión, colaboración, REST y WebSocket. No borrar PostgreSQL durante esta comprobación.

Si falla la activación antes de aceptar nuevas escrituras, volver a la imagen anterior y `docker-compose.yml`. **Después de aceptar escrituras en DynamoDB, no volver simplemente al snapshot antiguo**: primero reconciliar/exportar esos cambios para no perderlos.

## Pruebas aisladas

No se realizan llamadas de pago a IA. Las pruebas anteriores usan una PostgreSQL desechable; las nuevas usan DynamoDB Local con tabla aleatoria. No usar una tabla de producción para ejecutar tests.

```sh
# DynamoDB Local en memoria; puerto publicado solo en loopback.
docker run --rm -p 127.0.0.1:18001:8000 amazon/dynamodb-local:1.25.1 -jar DynamoDBLocal.jar -inMemory -sharedDb -disableTelemetry
export DYNAMODB_TEST_ENDPOINT=http://127.0.0.1:18001
cd backend/software-parcial-backend-main
mvn -Dtest=DynamoPersistenceIntegrationTest,ArquitecturaCuatroCapasTest test
```

Para un ensayo cruzado Python/Java, importar primero un export privado en una tabla local y definir `DYNAMODB_REHEARSAL_SOURCE` y `DYNAMODB_REHEARSAL_TABLE`; ejecutar `DynamoMigrationCompatibilityTest`. Esta prueba lee por los puertos Java todos los campos escritos por el importador Python y sus índices de búsqueda. Para la suite completa también se requieren `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER` y `DB_PASSWORD` de la PostgreSQL de pruebas.
