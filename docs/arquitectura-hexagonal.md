# Arquitectura hexagonal de ArqNova

La persistencia también dispone de adaptadores NoSQL en `repositorios/dynamodb`. El perfil `dynamodb` reemplaza JPA/PostgreSQL por el SDK de DynamoDB sin cambiar los puertos de los servicios. La activación y sus límites se documentan en [DynamoDB](dynamodb.md). Durante la transición se conserva PostgreSQL para una reversión controlada.

La separación usa puertos y adaptadores conservando los contratos existentes. El backend mantiene las cuatro capas obligatorias del examen; Angular y Flutter separan dominio, aplicación, presentación y adaptadores sin cambiar sus funcionalidades.

## Backend: cuatro capas obligatorias

- **modelo:** entidades y valores del dominio, DTO y excepciones.
- **servicios:** casos de uso y contratos de entrada/salida.
- **controladores:** REST, WebSocket, traducción de archivos y composición de Spring.
- **repositorios:** JPA, archivos, IA, criptografía, serialización y generadores.

La dependencia se invierte: los adaptadores implementan interfaces del núcleo. Un servicio puede funcionar con puertos falsos sin cargar Spring ni conectarse a PostgreSQL. Las anotaciones ORM están únicamente en las entidades del adaptador. Los POJOs que recibe el caso de uso no son entidades administradas por Hibernate.

Las políticas de transacción se configuran en `HexagonalConfiguration`. El rollback se comprueba forzando un fallo de membresía en una base de pruebas desechable: el proyecto tampoco debe quedar persistido. Las migraciones y las tablas de producción se conservan.

Detalles: [backend/ARQUITECTURA.md](../backend/software-parcial-backend-main/ARQUITECTURA.md).

## Angular

| Ubicación | Responsabilidad |
|---|---|
| `src/app/domain` | Modelos UML y reglas de nombres, multiplicidad, clases de asociación e importación desde imagen |
| `src/app/application` | Casos de uso de proyectos, sesión, IA, exportación, colaboración y voz; contratos en `ports` |
| `src/app/infrastructure/adapters` | HttpClient, almacenamiento del navegador, signals, STOMP, reconocimiento de voz y XML/XMI de EA |
| Entradas existentes en `auth`, `admin`, `pages` y `shared/services` | Composición Angular y presentación; conservan los nombres públicos usados por componentes y pruebas |

Los antiguos servicios Angular son entradas compatibles que ensamblan los adaptadores y delegan en casos de uso. Los casos de uso no importan Angular, STOMP ni el navegador. Se conserva el flujo RxJS para mantener cancelaciones, suscripciones, caché, guardado y restauración de sesión. Las claves de localStorage y el encabezado `X-Model-Version` no cambian.

El componente del lienzo conserva estado visual, formularios, avisos y coordinación. Las reglas reutilizables están en `domain/uml/diagram-rules.ts`; XMI permanece en el adaptador de Enterprise Architect, con sus mismas reglas de interoperabilidad y colores.

`npm run check:architecture` comprueba las dependencias. Se ejecuta automáticamente antes de `npm test` y `npm run build`.

## Flutter

- `features/*/domain`: comandos, resultados, estados y puertos de backend, conectividad, voz y operaciones pendientes.
- `features/*/application`: interpretación/ejecución del asistente y sincronización offline, con reloj, IDs y notificaciones inyectados.
- `features/*/data`: adaptadores HTTP, SQLite, conectividad y speech_to_text.
- `features/*/presentation`: widgets y puente ChangeNotifier.
- `core/composition/mobile_dependencies.dart`: construcción de las dependencias de la aplicación.

`SyncService` conserva una entrada compatible para consumidores existentes. Las reglas están en `OfflineSyncUseCases`; el núcleo no importa Flutter, HTTP, SQLite ni plugins. Se mantienen los tres intentos automáticos, la clave de idempotencia, los estados, los datos offline, la base SQLite versión 2 y el permiso de voz solicitado desde la interfaz.

No se cambian las versiones de dependencias ni se requiere migrar datos por esta reorganización.

## Pruebas reproducibles

```sh
# Backend: requiere PostgreSQL de pruebas, nunca la base de producción
cd backend/software-parcial-backend-main
mvn test

# Angular: incluye la comprobación de arquitectura
cd frontend/arqnova-frontend
npm test -- --watch=false
npm run build

# Flutter: SDK compatible con pubspec.lock; Dart 3.7.2 / Flutter 3.29.3
cd mobile/arqnova-mobile
flutter test
flutter analyze
```

Las pruebas de proveedores utilizan servidores HTTP locales o puertos falsos y no consumen cuotas de IA. Las pruebas de UI de voz emplean simulaciones; no sustituyen una prueba de micrófono en un dispositivo físico.

Esta migración es técnica y no se registra como una nueva funcionalidad del segundo examen.
