# CU11 — Asistente local

El texto se interpreta en el dispositivo mediante `LocalIntentEngine`. El
intérprete produce exclusivamente una de cuatro intenciones cerradas
(`create`, `query`, `update`, `delete`) y nunca envía la frase a un proveedor de
IA en la nube.

`GeneratedBackendApi` convierte esa intención en las rutas REST que genera CU9:
`/api/{entidad}` y `/api/{entidad}/{id}`. Para actualizar o eliminar por nombre,
primero consulta el recurso y exige una coincidencia única. Las eliminaciones
requieren confirmación en la interfaz.

La voz usa el reconocimiento disponible en Android mediante `speech_to_text`.
Los resultados parciales solo actualizan la transcripción; el resultado final
se interpreta una vez. La interpretación sigue disponible sin conexión. Las
operaciones CRUD requieren conectividad y, al no existir almacenamiento local
persistente previo en esta app, se informa al usuario sin simular que la acción
fue ejecutada.

La URL del backend continúa configurándose con `--dart-define=API_BASE_URL=...`.
