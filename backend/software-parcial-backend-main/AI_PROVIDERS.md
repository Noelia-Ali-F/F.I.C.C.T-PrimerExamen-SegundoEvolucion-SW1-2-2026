# Gemini y GroqCloud en ArqNova

El backend usa Gemini como proveedor principal y GroqCloud como respaldo para comandos y reconocimiento de imágenes. El editor, los métodos, las relaciones, las clases de asociación y el XMI mantienen su validación existente.

Variables en el `.env` privado del servidor:

- `GEMINI_API_KEY_1`, `GEMINI_API_KEY_2`, `GEMINI_API_KEY_3`: tres credenciales del mismo proyecto.
- `GEMINI_MODEL=gemini-3.8-flash`
- `GROQ_API_KEY`
- `GROQ_MODEL=openai/gpt-oss-120b`
- `GROQ_IMAGE_MODEL=qwen/qwen3.8-27b`
- `AI_PROVIDER_TIMEOUT_SECONDS=22`

No se deben versionar claves reales. Los archivos `.env.example` solo contienen valores vacíos y nombres de modelos.

## Respaldo automático

1. Una solicitud correcta a Gemini termina el recorrido; no se llama a GroqCloud.
2. Ante una credencial rechazada (401/403), se prueba la siguiente de las tres, con un máximo de una solicitud por credencial. Las credenciales rechazadas quedan suspendidas durante cinco minutos.
3. La cuota pertenece al proyecto: un 429 NO prueba las otras claves de Gemini. Se respeta `Retry-After` y se pasa a GroqCloud. El proyecto permanece en enfriamiento al menos un minuto.
4. Una caída, timeout o respuesta JSON ilegible de Gemini también permite usar GroqCloud. No se hace una segunda ejecución del editor: se interpreta y valida el resultado antes de aplicarlo.
5. Si ambos proveedores fallan, el reconocimiento informa el error. Los comandos mantienen el parser determinista existente cuando corresponde. No hay bucles de reintentos.

El resultado incluye el proveedor/modelo real; Groq usa un modelo visual para imágenes y uno de texto para comandos. El código no ejecuta las instrucciones devueltas como JavaScript, SQL ni comandos del servidor.

## Voz y Architect

Los comandos por micrófono conservan la captura de voz existente del navegador y envían el texto al asistente. Este cambio no añade transcripción de audio facturada ni síntesis de voz. La importación y exportación XMI siguen a cargo del código del editor.

## Pruebas

`UmlAiProviderPipelineTest` prueba selección, rotación de credenciales rechazadas, cuota compartida, enfriamiento, JSON inválido, fallo total, imágenes y validación de composición. Las solicitudes de esas pruebas usan servidores HTTP locales y claves ficticias.
