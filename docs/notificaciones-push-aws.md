# Notificaciones Android en AWS

Firebase Cloud Messaging está habilitado para `arqnova-segundo-examen`. El backend recibe la credencial desde un archivo externo al repositorio y lo monta como solo lectura. La configuración se activa con `docker-compose.push-aws.yml`, aplicado después de los overrides del segundo examen y del chat.

```bash
sudo env SECOND_EXAM_RELEASE=second-exam-d64432a docker compose \
  -f docker-compose.yml -f docker-compose.second-exam-aws.yml \
  -f /home/ubuntu/arqnova-chat.runtime.json -f docker-compose.push-aws.yml \
  up -d --no-deps --no-build backend
```

La credencial reside en `/home/ubuntu/.config/arqnova/firebase-service-account.json`, con permiso 600 y propietario igual al usuario del backend (UID 100, GID 101). No se añade a Git ni se sirve desde la web. El archivo se monta en `/run/secrets/firebase-service-account.json`. Para desactivar push se recrea el backend con el mismo comando omitiendo el último override; las conversaciones y el diagrama se conservan.

## Comprobaciones del 2 de noviembre de 2026

- Backend saludable y `GET /api/v1/push/status` anuncia `enabled=true`.
- El Samsung SM-N986U1 con Android 13 autorizó las notificaciones de ArqNova y registró su instalación, asociada a una cuenta temporal.
- Un evento MODEL determinista produjo un aviso visible dentro de la app, en primer plano.
- Otro evento MODEL determinista produjo una notificación Android en segundo plano, con título y texto de ArqNova.
- Dos registros de entrega confirmados. Reenviar el identificador de la operación completada conservó el modelo y el número de entregas, sin duplicar el aviso.

Estas comprobaciones no consumieron APIs de IA. El botón de la aplicación permite activar/desactivar el registro para la cuenta que inició sesión. La entrega sigue siendo de mejor esfuerzo y depende de conectividad, permisos y restricciones Android; no es una garantía de entrega universal.

El usuario confirmó que al pulsar la notificación se abrió el diagrama; se observó después el proyecto temporal en la pantalla del teléfono. El primer intento automatizado de pulsación coincidió con cambios de pantalla y no se utilizó como evidencia de éxito.

Se comprobó además desactivar las notificaciones, volver a activarlas y cerrar sesión explícitamente: el servidor terminó con cero dispositivos registrados para la cuenta temporal. Se eliminaron la cuenta, el proyecto y sus registros de memoria de prueba; los nueve diagramas originales conservaron su huella de contenido y versión. La app queda instalada y en la pantalla de inicio de sesión para usar una cuenta real.

CU11 tiene una primera validación real en el dispositivo indicado. No se probaron todas las restricciones de batería de otros fabricantes ni todos los escenarios con la app terminada; la entrega observada fue con la app abierta y en segundo plano.
