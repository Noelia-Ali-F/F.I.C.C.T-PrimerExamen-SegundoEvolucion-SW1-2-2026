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

## Segundo examen: consulta de proyectos UML

**Ver mis proyectos UML** permite iniciar sesión con la cuenta del sistema web,
consultar los proyectos propios o compartidos y abrir sus diagramas en modo de
solo lectura. Incluye zoom, desplazamiento, clases, atributos, métodos,
multiplicidades, composición y clases de asociación. La pestaña **Actividad**
reúne cambios manuales e intervenciones de IA, identifica a su autor y permite
recuperar entradas anteriores.

Este módulo utiliza `UML_API_BASE_URL`; la configuración `API_BASE_URL` del
asistente local anterior se conserva de manera independiente:

```bash
flutter run -d android --dart-define=UML_API_BASE_URL=http://10.0.2.2:8080
```

La API del backend del segundo examen debe estar desplegada para consultar el
historial ampliado. La integración de notificaciones CU11 está preparada con Firebase Cloud Messaging; su activación y la comprobación en Android se completarán en local.
Las pruebas del módulo usan HTTP simulado y no consumen APIs de IA.

## Notificaciones y compilación local

Firebase: `arqnova-segundo-examen`, paquete Android `com.arqnova.mobile`.
Los identificadores públicos de la app están configurados. La credencial de envío
se proporciona únicamente al backend local. El SDK utiliza Firebase Installation
IDs (FID), permiso de notificaciones, avisos en primer plano y apertura del
proyecto después de verificar el acceso.

Requiere Android 7.0/API 24 o superior. No se instaló Android SDK ni se compiló
un APK en esta etapa, de acuerdo con la decisión de continuar desde un clon
local. Se verificaron 30 pruebas Flutter y el análisis estático. La recepción
real de push en un teléfono sigue pendiente.

Ver la [guía de continuidad local](../../docs/puesta-en-marcha-local-segundo-examen.md)
para los comandos de backend, emulador, teléfono físico y credenciales Firebase.


## Android fisico y AWS

El primer APK debug ya se compilo e instalo por ADB inalambrico en Android 13. Para usar el visor con AWS: `fvm flutter run -d <dispositivo> --dart-define=UML_API_BASE_URL=https://18-116-164-204.sslip.io`. Requiere Java 17. Ver [resultado y preparacion Windows](../../docs/pruebas-movil-android-2026-11-02.md). Push sigue pendiente de activacion en el servidor.


Actualizacion push: ya esta habilitado en AWS y probado en primer y segundo plano en Android 13. Iniciar sesion en Ver mis proyectos UML y pulsar Activar notificaciones para registrar esta instalacion con la cuenta actual. Ver [CU11](../../docs/notificaciones-push-aws.md).
