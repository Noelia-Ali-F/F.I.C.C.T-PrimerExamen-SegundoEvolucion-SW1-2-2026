# Refuerzos de estabilidad: 2 de noviembre de 2026

Se corrigieron tres situaciones concretas sin cambiar los contratos de la API ni el modelo de datos:

- **Chat web:** si el usuario empieza un borrador mientras espera la confirmación de un envío anterior, esa confirmación ya no borra el texto nuevo. El texto se limpia solamente cuando todavía corresponde al envío confirmado.
- **Móvil:** los cambios del identificador de instalación Firebase se procesan en orden. Una respuesta lenta no puede retirar el identificador más reciente. Las operaciones que ya pertenecen a una sesión anterior se descartan antes de guardar; los errores del stream de registro se manejan sin convertirse en errores no controlados de navegación.
- **Backend:** un fallo al reclamar o liberar una entrega push para un destinatario ya no interrumpe el procesamiento de los otros dispositivos. Se conserva la deduplicación existente y no se añaden bucles de reintentos.

## Validación

- Web: 133 pruebas correctas y compilación de producción correcta.
- Backend: 236 pruebas correctas y una opcional omitida; JAR construido. Las pruebas de integración usaron PostgreSQL y DynamoDB temporales en Docker, sin levantar la web local ni consultar los datos publicados en AWS.
- Móvil: 32 pruebas correctas, análisis sin incidencias y APK debug construido con dirección del visor apuntando al backend AWS.
- Se añadieron casos de confirmación tardía, borrador sin cambios, rotaciones concurrentes, recuperación del stream y fallos por destinatario.
- Cero solicitudes a proveedores de IA en esta ronda.

Los refuerzos reducen estos riesgos específicos; las pruebas no garantizan ausencia de todos los errores futuros. El APK compilado está en `mobile/arqnova-mobile/build/app/outputs/flutter-apk/app-debug.apk`. Esta ronda guarda el código y las pruebas en GitHub; el despliegue web/backend y la app previamente instalada requieren su actualización para ejecutar estos refuerzos.
