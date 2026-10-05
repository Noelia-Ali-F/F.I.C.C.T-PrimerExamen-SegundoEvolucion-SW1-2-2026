# Estándares de programación de ArqNova

## Principios generales

- Mantener responsabilidades separadas entre presentación, aplicación, dominio y persistencia.
- Reutilizar servicios existentes antes de crear implementaciones paralelas.
- Evitar secretos, credenciales y URLs privadas dentro del código versionado.
- Validar toda entrada en los límites del sistema y presentar errores comprensibles.
- Mantener compatibilidad con los contratos HTTP y STOMP existentes.
- Agregar pruebas para cada corrección o comportamiento nuevo.

## Java y Spring Boot

- Java 17 como versión del proyecto.
- Paquetes bajo `com.arqnova`, en minúsculas y organizados por responsabilidad.
- Clases en `PascalCase`; métodos y variables en `camelCase`; constantes en `UPPER_SNAKE_CASE`.
- Controladores limitados a transporte, validación y delegación.
- Lógica de negocio en servicios e interfaces de servicio cuando corresponda.
- Acceso a datos mediante repositorios Spring Data JPA.
- DTO de request/response para contratos externos; no exponer entidades JPA directamente.
- Validación declarativa con Jakarta Validation y errores controlados por el manejador global.
- Migraciones Flyway inmutables: una corrección de esquema se agrega en una migración nueva.
- Pruebas con JUnit/Mockito y pruebas de integración para persistencia, seguridad y endpoints críticos.

## Angular y TypeScript

- Componentes standalone y servicios inyectables según la arquitectura actual.
- Tipado estricto; evitar `any` salvo integración externa justificada.
- Clases y tipos en `PascalCase`; propiedades, funciones y signals en `camelCase`.
- Componentes responsables de presentación y coordinación; llamadas HTTP en servicios.
- Estado reactivo con signals/RxJS sin duplicar fuentes de verdad.
- Accesibilidad: etiquetas, estados visibles, navegación por teclado y mensajes claros.
- Formato definido por `.editorconfig` y `.prettierrc`.
- Pruebas DOM y de servicios con Vitest; no validar solo estado interno cuando el comportamiento es visual.

## Flutter y Dart

- Flutter fijado mediante `.fvmrc`; ejecutar siempre con `fvm flutter`.
- Arquitectura por feature con capas `domain`, `data` y `presentation`.
- Archivos y carpetas en `snake_case`; clases en `PascalCase`; miembros en `camelCase`.
- Modelos inmutables cuando sea posible y serialización explícita.
- Comunicación REST encapsulada en servicios.
- Operaciones offline idempotentes, persistentes y con errores temporales/permanentes diferenciados.
- `flutter analyze` sin observaciones y pruebas con `flutter_test` antes de generar el APK.

## Base de datos

- PostgreSQL administrado mediante Flyway.
- Nombres de tablas y columnas consistentes en `snake_case`.
- Restricciones e índices definidos en migraciones.
- JSONB reservado para el modelo UML flexible; no confundirlo con exportación JSON de interfaz.
- No editar migraciones aplicadas ni eliminar datos desde el código de aplicación sin autorización funcional.

## Git y revisión

- No versionar `node_modules`, builds, SDK de FVM, APK, secretos, bases locales ni respaldos comprimidos.
- Commits enfocados y descriptivos.
- Antes de integrar ejecutar:

```bash
# Backend
cd backend/software-parcial-backend-main && ./mvnw clean test

# Frontend
cd frontend/arqnova-frontend && npm test -- --watch=false && npm run build

# Android
cd mobile/arqnova-mobile && fvm flutter analyze && fvm flutter test && fvm flutter build apk --debug
```

## Definición de terminado

Una funcionalidad se considera terminada cuando compila, tiene validaciones, maneja errores, posee cobertura proporcional al riesgo, mantiene los CU relacionados y está documentada.
