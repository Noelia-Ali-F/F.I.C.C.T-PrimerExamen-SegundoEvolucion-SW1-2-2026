# Descarga Android desde el login

El login publicado incluye **ArqNova en tu Android**, con descarga pública del APK e instrucciones desplegables. No solicita una cuenta para descargar. La app utiliza después la misma cuenta del sistema web para consultar los proyectos autorizados.

- Login: `https://18-116-164-204.sslip.io/login`.
- APK: `https://18-116-164-204.sslip.io/downloads/arqnova-android.apk`.
- Android mínimo: 7.0 / API 24. APK universal para las arquitecturas incluidas en el build Flutter.
- Archivo publicado el 2 de noviembre de 2026: 49 199 879 bytes (aproximadamente 47 MiB), versión 1.0.0.
- SHA-256: `2b624b3d8596fe0e1be90168f9a371e84c053d3b66b9d70fe3455e54b3d2ffaa`.

## Publicación

El APK se genera desde `mobile/arqnova-mobile`:

```powershell
$env:JAVA_TOOL_OPTIONS='-Djdk.net.unixdomain.tmpdir=C:/Users/Public'
fvm flutter build apk --release --dart-define=UML_API_BASE_URL=https://18-116-164-204.sslip.io
```

Se usa la configuración de firma académica existente del proyecto (keystore debug); este artefacto es para distribución directa de la práctica, no una publicación en Play Store. La firma del APK fue verificada antes de publicarlo.

En EC2 el archivo se conserva fuera de Git en `/home/ubuntu/arqnova-downloads/arqnova-android.apk`, con permiso 644. El directorio se monta como solo lectura en el frontend mediante `docker-compose.mobile-download-aws.yml`. El APK no se incorpora al repositorio ni a la imagen web. Para actualizarlo, transferir a un archivo temporal del mismo directorio, verificar su checksum y reemplazar el APK publicado con un renombrado, evitando servir una transferencia incompleta.

La imagen web publicada es `arqnova-frontend:mobile-download-20261102`. Para recrear el frontend actual desde el repositorio EC2:

```bash
sudo env SECOND_EXAM_RELEASE=second-exam-d64432a docker compose \
  -f docker-compose.yml -f docker-compose.second-exam-aws.yml \
  -f /home/ubuntu/arqnova-chat.runtime.json -f docker-compose.push-aws.yml \
  -f docker-compose.mobile-download-aws.yml \
  up -d --no-deps --no-build frontend
```

El archivo de descarga se entrega con MIME Android, encabezado attachment, soporte de rangos y caché revalidable. Un archivo inexistente en `/downloads/` devuelve 404, evitando entregar el HTML del login como si fuera un APK.

## Comprobaciones

- Compilación web de producción y 133 pruebas frontend correctas.
- APK release construido y firma Android verificada.
- Descarga completa anónima desde Chrome: tamaño y SHA-256 coinciden con el artefacto original.
- Login revisado en escritorio y viewport móvil de 390 px: enlace, ayuda, desplazamiento vertical y ausencia de desbordamiento horizontal.
- La descarga conserva la página de login y sus campos.
- Nginx validado; backend, PostgreSQL y gateway conservaron sus contenedores sin recreación.
- No se consumieron llamadas de IA.

El APK incorpora los refuerzos móviles de registro push y la imagen frontend incorpora la protección del borrador del chat. Esta actualización no despliega el cambio pendiente del backend que aísla fallos push entre destinatarios.
