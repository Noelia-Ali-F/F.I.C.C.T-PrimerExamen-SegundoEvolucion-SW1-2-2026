# Continuación local del segundo examen

El código del segundo examen está integrado en la rama principal `main`. La web y el backend del segundo examen están publicados en AWS; por indicación del usuario, su continuación se realiza allí. Esta guía conserva el entorno local opcional y los pasos del móvil. La compilación del APK y la comprobación de notificaciones en un teléfono se realizarán después de clonar en local.

## 1. Clonar

```powershell
git clone https://github.com/Noelia-Ali-F/F.I.C.C.T-PrimerExamen-SegundoEvolucion-SW1-2-2026.git arqnova
cd arqnova
Copy-Item .env.example .env
```

Para la ejecución local se necesitan Docker Compose y Flutter. La compilación Android requiere además Java 17, Android SDK y un dispositivo o emulador con Google Play; el móvil con Firebase requiere Android 7.0/API 24 o posterior. Esas herramientas Android no se instalaron en esta etapa.

### Flutter con FVM en Windows

El archivo `mobile/arqnova-mobile/.fvmrc` fija Flutter **3.29.3** (Dart **3.7.2**). Instalar [FVM](https://fvm.app/documentation/getting-started/installation) y preparar esa versión desde el directorio móvil:

```powershell
cd mobile/arqnova-mobile
fvm install 3.29.3
fvm use 3.29.3
fvm flutter --version
cd ../..
```

Si Windows informa que no puede crear enlaces simbólicos, habilitar el modo de desarrollador de Windows o configurar una unión de directorio local para `.fvm/flutter_sdk` hacia la versión instalada. La carpeta `.fvm` es local y no se sube a Git. En VS Code, `dart.flutterSdkPath` debe apuntar a `.fvm/flutter_sdk`.

FVM administra Flutter; Android SDK se instala por separado y requiere aceptar la licencia de Google. No se necesita instalar estas herramientas en la instancia AWS.

## 2. Backend, web y memoria NoSQL locales

```powershell
docker compose -f docker-compose.yml -f docker-compose.second-exam-local.yml up --build -d postgres backend frontend
```

- Web: `http://localhost:8081`.
- Backend: `http://localhost:8080`.
- PostgreSQL y las migraciones V1–V10 quedan en la instalación Docker local.
- Las conversaciones se guardan en DynamoDB Local, en un volumen local persistente. El contenedor de inicialización crea únicamente la tabla local `arqnova-ai-memory`.
- La configuración local no requiere credenciales de AWS y no consulta las tablas de la cuenta AWS.
- Solo se inician los servicios indicados y sus dependencias. El gateway con el dominio público de AWS queda fuera de este comando.

Crear una cuenta y un proyecto desde la web local antes de entrar desde el móvil. Los datos existentes de AWS no se copian ni se alteran. Las claves de los proveedores de IA se configuran en la `.env` local cuando se quiera probar consultas o reconocimiento de imágenes. Las pruebas automatizadas utilizan proveedores simulados.

## 3. Ejecutar el visor móvil

```powershell
cd mobile/arqnova-mobile
fvm flutter pub get
fvm flutter analyze
fvm flutter test
fvm flutter run --dart-define=UML_API_BASE_URL=http://10.0.2.2:8080
```

`10.0.2.2` corresponde al equipo anfitrión desde el emulador Android. En un teléfono físico sustituirlo por la IP local del equipo donde corre el backend, accesible desde la misma red.

**Ver mis proyectos UML** usa la cuenta de la web local y permite consultar diagramas y actividad. El asistente local anterior conserva su variable `API_BASE_URL` para consumir backends generados; es independiente de `UML_API_BASE_URL`.

## 4. Activar push en el entorno local

El proyecto Firebase `arqnova-segundo-examen` ya está creado en el plan Spark, con la app Android `com.arqnova.mobile` registrada y la API Cloud Messaging V1 habilitada. Analytics y la asistencia adicional de Gemini no se habilitaron. La configuración pública de Android está incorporada en `google-services.json` y `firebase_options.dart`.

Para enviar desde el backend falta proporcionar su credencial de ejecución local:

1. Abrir [Cuentas de servicio de Firebase](https://console.firebase.google.com/project/arqnova-segundo-examen/settings/serviceaccounts/adminsdk).
2. Generar el archivo JSON de la cuenta de servicio y guardarlo fuera del repositorio. No se generó una clave privada en esta etapa.
3. En la `.env` de la raíz indicar la ruta absoluta, por ejemplo:

   ```dotenv
   FIREBASE_CREDENTIALS_HOST_FILE=C:/arqnova-config/firebase-service-account.json
   ```

4. Volver a la raíz del repositorio y ejecutar:

   ```powershell
   docker compose -f docker-compose.yml -f docker-compose.second-exam-local.yml -f docker-compose.push-local.yml up --build -d postgres backend frontend
   ```

El último archivo habilita push y monta la credencial únicamente en el backend local. Para ejecutar Spring Boot sin Docker, las variables equivalentes son `PUSH_NOTIFICATIONS_ENABLED=true`, `FIREBASE_PROJECT_ID=arqnova-segundo-examen` y `FIREBASE_CREDENTIALS_FILE=<ruta-del-json>`.

El móvil registra su Firebase Installation ID (FID), asociado a la cuenta autenticada. El servidor selecciona únicamente dispositivos de integrantes del proyecto. Los cambios manuales registrados y las respuestas de IA completadas producen notificaciones después del commit. En primer plano se muestra un aviso con acceso al proyecto; al abrir una notificación, el acceso se comprueba de nuevo después de iniciar sesión en el visor.

El envío es de mejor esfuerzo: un fallo de Firebase no revierte un diagrama ni una respuesta guardada. Se evitan duplicados del mismo evento/dispositivo y no se promete entrega garantizada durante cortes o reinicios del proceso. Los permisos de Android y las restricciones del dispositivo también intervienen en la recepción.

## 5. Comprobación pendiente en Android

Cuando el entorno local esté preparado:

1. Ejecutar o compilar la app: `fvm flutter build apk --debug --dart-define=UML_API_BASE_URL=http://IP_LOCAL:8080`.
2. Iniciar sesión en el visor y activar el permiso de notificaciones.
3. Modificar un proyecto compartido desde la web local y comprobar autor, diagrama e historial en el móvil.
4. Comprobar una notificación en primer plano, otra con la app en segundo plano y el cierre de sesión.

Todavía no se construyó un APK ni se comprobó la recepción real en un teléfono. La validación realizada incluye pruebas Flutter, pruebas del backend, PostgreSQL real desechable, DynamoDB Local y simulaciones de OAuth/FCM; esas pruebas no consumen APIs de IA ni envían notificaciones reales.

Referencias de configuración: [Firebase para Flutter](https://firebase.google.com/docs/flutter/setup), [FCM en Flutter](https://firebase.google.com/docs/cloud-messaging/flutter/get-started) y [envío de mensajes con FID](https://firebase.google.com/docs/cloud-messaging/send/admin-sdk).
