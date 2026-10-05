# Integración OpenAI de ArqNova

El backend utiliza la API Responses para interpretar texto y reconocer diagramas UML en imágenes.

- `OPENAI_API_KEY`: clave configurada en el entorno del backend.
- `OPENAI_MODEL`: `gpt-6.1-sol` por defecto.
- `OPENAI_IMAGE_MODEL`: `gpt-6.1-sol` por defecto.
- `OPENAI_REASONING_EFFORT`: `low` por defecto.
- `OPENAI_TIMEOUT_SECONDS`: 45 segundos por defecto.

Las solicitudes usan salida JSON con schema estricto y `store=false`. El backend valida las operaciones, resuelve referencias a elementos existentes y mantiene el contrato del editor. Las respuestas identifican al proveedor como `OPENAI`.

Los comandos simples conservan el parser determinista existente. Si OpenAI falla, el texto puede recurrir a ese respaldo; las imágenes devuelven un error controlado. No se reintentan errores de cuota agotada ni se cambia automáticamente a otro proveedor.

La cuenta de OpenAI debe tener saldo disponible para realizar llamadas. La suscripción de ChatGPT no sustituye el saldo de API.

Documentación: https://developers.openai.com/api/docs/guides/structured-outputs
