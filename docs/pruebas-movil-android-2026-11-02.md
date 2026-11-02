# Primera prueba Android física: 2 de noviembre de 2026

Se generó el APK debug con FVM/Flutter 3.29.3 y se instaló por ADB inalámbrico en un Samsung SM-N986U1 con Android 13 (API 33). La app se compiló con `UML_API_BASE_URL=https://18-116-164-204.sslip.io`, sin cambiar la dirección del asistente local anterior.

## Preparación y cambio de compilación

El SDK Android ya estaba instalado y tenía licencia registrada. Gradle comprobó que las licencias de los paquetes requeridos estaban aceptadas e instaló NDK 27.0.12077973, plataformas 34/35, Build Tools 34 y CMake 3.22.1. Firebase Core 5.1.0 utiliza `kotlin.compilerOptions`, incompatible con el plugin Kotlin 1.8.22 anterior: se actualizó a **2.1.20**, conservando Gradle 8.10.2 y AGP 8.7.0.

Se seleccionó Java 17 para Flutter. En este equipo Windows Java necesitó `JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=C:/Users/Public` para evitar un error de conexión loopback asociado a su ruta temporal. Esta variable se aplicó a los comandos de compilación, sin cambiar la lógica de la aplicación.

```powershell
cd mobile/arqnova-mobile
$env:JAVA_TOOL_OPTIONS='-Djdk.net.unixdomain.tmpdir=C:/Users/Public'
fvm flutter build apk --debug --dart-define=UML_API_BASE_URL=https://18-116-164-204.sslip.io
fvm flutter devices
# Con el dispositivo conectado:
fvm flutter run -d <identificador-adb> --dart-define=UML_API_BASE_URL=https://18-116-164-204.sslip.io
```

El APK local queda en `mobile/arqnova-mobile/build/app/outputs/flutter-apk/app-debug.apk`; es un artefacto debug y no se incorpora a Git.

## Comprobado en el teléfono

- Instalación por ADB y arranque de ArqNova Mobile correctos.
- Acceso a **Ver mis proyectos UML** e inicio de sesión real contra AWS con una cuenta temporal.
- Listado del proyecto temporal y diagrama de solo lectura con Catálogo, Reserva y Editorial.
- Composición Catálogo–Reserva, rombo relleno, multiplicidades, posiciones y atributos visibles.
- Pestaña Actividad: intervención MODEL con autor, fecha, instrucción y respuesta.
- `flutter analyze` sin incidencias; 30 pruebas Flutter correctas.

La creación de Editorial utilizó el intérprete determinista: **cero llamadas a proveedores IA**. Se eliminaron únicamente la cuenta, el proyecto temporal y sus cuatro registros DynamoDB. Los nueve diagramas originales conservaron su huella exacta de contenido y versión. Las capturas se guardaron fuera del repositorio.

## Pendiente

CU11 todavía no está validado de extremo a extremo: Firebase existe, pero el envío push permanece desactivado en AWS y falta proporcionar la credencial de servicio del backend, habilitar el envío y comprobar entrega en primer/segundo plano. Esta prueba no activa permisos de notificación ni afirma recepción real de push. Tampoco sustituye toda la matriz de regresión de CU9/CU10 (paginación extensa, interrupciones de red y varios dispositivos).


CU11 se activo y probo posteriormente en este mismo telefono: [resultados de notificaciones](notificaciones-push-aws.md). La seccion Pendiente anterior corresponde al estado de la primera prueba del visor.
