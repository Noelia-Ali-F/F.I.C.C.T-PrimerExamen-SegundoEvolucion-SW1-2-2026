# ArqNova Mobile

Aplicación Flutter del sistema ArqNova para interactuar con un backend Spring
Boot generado desde un modelo UML.

## Funcionalidad

- asistente IA ejecutado localmente en el dispositivo;
- comandos por texto o voz;
- transcripción mediante `speech_to_text`;
- interpretación cerrada de las intenciones registrar, consultar, actualizar y
  eliminar;
- confirmación antes de operaciones destructivas;
- consumo de los endpoints REST generados por CU9;
- mensajes controlados cuando no existe conexión.

La interpretación local no depende de servicios de IA en la nube. Las
operaciones CRUD sí requieren conectividad con el backend Spring Boot.

## Configuración del backend

La URL se configura sin modificar código mediante `API_BASE_URL`:

```bash
flutter run -d android \\
  --dart-define=API_BASE_URL=http://TU_IP_O_DOMINIO:8080
```

## Verificación

```bash
flutter pub get
flutter analyze
flutter test
flutter build apk --debug
```

La aplicación Android necesita los permisos de Internet y micrófono para las
operaciones REST y la entrada por voz, respectivamente.
