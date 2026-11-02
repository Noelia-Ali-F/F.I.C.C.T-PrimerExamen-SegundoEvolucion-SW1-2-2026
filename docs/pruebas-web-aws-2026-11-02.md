# Pruebas web en AWS: 2 de noviembre de 2026

Se verificó la aplicación publicada con Chrome, en dos sesiones aisladas de usuarios distintos, y proyectos temporales. No se modificó el código del sistema durante esta ronda.

## Resultados

| Comprobación | Resultado |
| --- | --- |
| Inicio de sesión real de dos integrantes y acceso al chat | Correcto |
| MODEL: crear Libro con id y titulo | Se aplicó una vez; clase y respuesta aparecieron en ambos navegadores sin recargar |
| MODEL: asociación Cliente–Libro muchos a muchos | Conservó la asociación original, multiplicidades y DetalleClienteLibro con conector discontinuo |
| Composición manual Libro–Venta | Rombo de composición visible y sincronizado en ambos usuarios |
| Instrucción IA de composición sin multiplicidades | Solicitó aclaración; no cambió el diagrama |
| Consulta IA sobre la primera clase creada | Recuperó Libro y sus atributos usando la conversación; no cambió el modelo |
| Recarga y nuevo inicio de sesión | Recuperaron el diagrama y las respuestas persistidas |
| Historial paginado | Cursores sin duplicados entre páginas |
| Reenvío del identificador de una operación completada | Conservó contenido y versión; no ejecutó otra llamada IA |
| Acceso anónimo y usuario ajeno al proyecto | Rechazados con 401 y 403 respectivamente |
| Exportación XMI desde el navegador | Incluyó composición y clase de asociación |
| Importación completa del XMI en un proyecto vacío | Recuperó cuatro clases y tres relaciones, incluida composición y clase de asociación |
| Reimportación en el proyecto original | Vista previa correcta; insertar no duplica clases ya existentes, conforme al comportamiento actual |
| Generadores backend, Flutter y Postman | ZIP válidos y colección Postman válida, sin consumo de IA |
| Archivo de imagen inválido | Rechazado con INVALID_IMAGE antes de invocar al proveedor |
| Panel de voz | Accesible desde el asistente anterior |
| Regresión automatizada del frontend | 131 pruebas correctas; comprobación de arquitectura correcta |

Las verificaciones automatizadas se ejecutaron sobre el mismo código frontend que el desplegado. Un primer script de navegador consideró oculta una línea SVG vertical por su ancho nulo; la captura confirmó el conector y se corrigió la comprobación. Otro supuesto del script esperaba duplicar clases al reimportarlas en el mismo proyecto; el importador evita esos duplicados, por lo que se comprobó la importación completa en un proyecto vacío. Ninguno de estos ajustes requirió cambiar la aplicación.

## Consumo y alcance

Esta ronda produjo **dos solicitudes reales a Groq**: una aclaración de composición y una consulta breve de memoria. Las otras dos instrucciones MODEL se resolvieron mediante el intérprete determinista. El reenvío no generó una solicitud adicional. No se dispone del costo monetario exacto de esas dos respuestas.

No se envió una fotografía a la IA ni se capturó/transcribió audio real. Las pruebas automáticas anteriores de imágenes/proveedores usan simulaciones. La comprobación XMI se hizo dentro de la aplicación web; **no se abrió Enterprise Architect de escritorio en esta ronda**. Estos resultados no sustituyen esas pruebas de extremo a extremo ni la validación Android pendiente.

## Limpieza

Se eliminaron únicamente los dos proyectos temporales, las tres cuentas de prueba y sus trece registros DynamoDB. Después se verificó que los nueve diagramas originales conservaban exactamente la huella de contenido y versión previa. Las capturas y los logs quedaron fuera de Git, en la computadora de desarrollo.
