# Segundo examen en AWS

La ampliación web/backend se desplegó el **2 de noviembre de 2026**, desde la rama `feat/segundo-examen-ia-colaborativa`, revisión de código `d64432a`. Imágenes: `arqnova-backend:second-exam-d64432a` y `arqnova-frontend:second-exam-d64432a`. El móvil continúa en local; push permanece desactivado.

El chat usa `arqnova-backend:chat-isolated-20261102` y una clave independiente; ver [segundo examen](segundo-examen.md). Para recrearlo, agregar `-f /home/ubuntu/arqnova-chat.runtime.json` al comando siguiente.

## Configuración y operación

Se mantienen PostgreSQL, sus volúmenes, autenticación, claves y configuración de los proveedores existentes. Las conversaciones usan la tabla DynamoDB `arqnova-ai-memory` en Ohio mediante el rol de EC2. Se habilitan conversaciones y modelado con `docker-compose.second-exam-aws.yml`; no se utiliza el override local ni se cambia toda la base al perfil DynamoDB.

Las imágenes se compilan fuera de EC2 y se cargan antes de desplegar. Para reproducir este despliegue, desde el repositorio de EC2:

```bash
sudo env SECOND_EXAM_RELEASE=second-exam-d64432a docker compose \
  -f docker-compose.yml -f docker-compose.second-exam-aws.yml \
  up -d --no-deps --no-build backend frontend
```

Usar los dos archivos y la etiqueta publicada; el compose base por sí solo no reproduce esta configuración. Las migraciones aditivas V9 y V10 se aplicaron correctamente. No se reiniciaron PostgreSQL ni el gateway.

## Comprobaciones realizadas

- Backend saludable y página pública accesible por HTTPS.
- Los nueve diagramas previos conservaron su contenido y versión, comprobados mediante huella antes/después.
- Registro e inicio de sesión con cuentas temporales; invitación de un segundo participante y rechazo de un tercero ajeno al proyecto.
- Una instrucción MODEL creó una clase con atributos en el proyecto temporal y ambos participantes pudieron consultar el resultado y la conversación persistida en DynamoDB.
- Reenviar el mismo identificador no volvió a modificar ni incrementar la versión del diagrama.
- Se eliminaron el proyecto, las tres cuentas temporales y sus cuatro registros DynamoDB al terminar.

Esta prueba de despliegue utilizó el intérprete determinista: **cero llamadas a proveedores de IA**. Las respuestas reales de consulta/revisión, las cuotas de proveedores y el reconocimiento de imágenes no se volvieron a probar en este despliegue. Las suites anteriores con proveedores simulados siguen documentadas en `segundo-examen.md`; no se afirma una nueva validación completa de todos los flujos en producción.

## Respaldo y restauración

Se guardaron fuera del repositorio, en la computadora de desarrollo, el dump PostgreSQL, la configuración, el inventario de contenedores y los archivos de uploads, con manifiesto SHA-256. Las imágenes anteriores se conservan en EC2 bajo `rollback-second-exam-20261102`.

El override de restauración está en `/home/ubuntu/arqnova-rollbacks/second-exam-20261102/compose.rollback.json`. Desde el repositorio EC2:

```bash
sudo docker compose -f docker-compose.yml \
  -f /home/ubuntu/arqnova-rollbacks/second-exam-20261102/compose.rollback.json \
  up -d --no-deps --no-build backend frontend
```

La restauración de imágenes conserva las tablas aditivas y los datos. El disco sigue limitado; antes de nuevas cargas se debe revisar espacio y conservar estos respaldos. La limpieza realizada afectó únicamente cachés regenerables de APT y Maven, sin eliminar datos ni imágenes anteriores.

## Disco ampliado el 2 de noviembre de 2026

El volumen gp3 `vol-08bfd9dfdd1704d35` de la instancia `i-098aace5b0cdfe43a` se amplió de 8 a 20 GiB, conservando 3000 IOPS y 125 MiB/s. Antes se creó la instantánea `snap-07f58130bb799c2c6`, verificada al 100 %, y un nuevo respaldo local PostgreSQL/configuración.

Con el volumen en estado optimizing, se ejecutaron `growpart /dev/nvme0n1 1` y `resize2fs /dev/nvme0n1p1`. Resultado: raíz ext4 de aproximadamente 19 GiB, 6.5 GiB usados y 12 GiB libres (36 % de uso). No se detuvo EC2 ni se recrearon contenedores. Se verificaron backend/PostgreSQL saludables, HTTPS 200 y la misma huella de los nueve diagramas. AWS puede continuar la optimización en segundo plano aunque el espacio ya esté disponible. El volumen ampliado y la instantánea quedan sujetos a la facturación de AWS.


Push se activo despues de este despliegue inicial. Para recrear el backend actual debe agregarse tambien `-f docker-compose.push-aws.yml` como ultimo override. Ver [configuracion y pruebas push](notificaciones-push-aws.md).
