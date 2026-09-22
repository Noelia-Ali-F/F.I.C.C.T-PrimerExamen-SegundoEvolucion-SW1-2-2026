# Integración Gemini de ArqNova

ArqNova utiliza Google Gemini desde el backend para interpretar instrucciones UML y analizar imágenes de diagramas. La clave nunca se expone al frontend.

## Configuración

- `GEMINI_API_KEY`: credencial proporcionada exclusivamente por el entorno.
- `GEMINI_MODEL`: identificador configurable del modelo.

## Responsabilidades

- interpretar instrucciones de texto o transcripciones de voz;
- producir operaciones UML estructuradas;
- reconocer clases, atributos, relaciones y multiplicidades desde imágenes;
- solicitar aclaración cuando una instrucción sea ambigua.

Gemini no modifica directamente la persistencia. El backend valida la respuesta estructurada y el editor aplica las operaciones respetando autorización, bloqueos y colaboración en tiempo real.

## Tolerancia a fallos

Los errores transitorios utilizan reintentos limitados con espera exponencial. Cuando el proveedor no está disponible, la interpretación textual puede utilizar el respaldo determinista existente e informa explícitamente que hubo fallback. El análisis de imágenes devuelve errores controlados y no modifica el proyecto.

Nunca deben registrarse la API key, JWT, imágenes completas ni contenido base64.
