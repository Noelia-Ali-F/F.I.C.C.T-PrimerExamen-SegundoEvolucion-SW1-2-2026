# Matriz de cumplimiento del examen

Esta matriz relaciona las condiciones de evaluación con evidencia verificable del repositorio ArqNova.

| Condición | Estado | Evidencia funcional | Evidencia técnica |
|---|---|---|---|
| Sistema completo | Cumple | Acceso, usuarios, proyectos, editor UML, colaboración, IA, XMI, generadores y aplicación Android | `backend/`, `frontend/`, `mobile/`, pruebas automatizadas y `docker-compose.yml` |
| Colaboración en tiempo real | Cumple | Sesiones, invitaciones, anfitrión, participantes, cursores, sincronización, locks y bitácora | WebSocket/STOMP, `UmlCollaboration*`, `uml-collaboration.service.ts` y migración V8 |
| Disponible en la nube | Cumple | Frontend público y API protegida desplegados en AWS EC2 | Docker Compose, Nginx y `scripts/deploy-cloud-aws.sh` |
| Documentación y estándares | Cumple | README, matriz, guías de uso y videotutoriales | `README.md`, `docs/ESTANDARES_DE_PROGRAMACION.md` y `docs/tutoriales/` |
| Generación de código | Cumple | Backend Spring Boot y frontend Flutter descargables | `UmlBackendGeneratorServiceImpl` y `UmlFlutterGeneratorServiceImpl` |
| Importación/exportación Enterprise Architect | Cumple con alcance declarado | XMI 2.1/UML 2, clases, atributos, PK, relaciones y multiplicidades | `uml-xmi.service.ts` y sus pruebas |

## Alcance real

- XMI soporta las versiones declaradas por el servicio (`2.1`, `2.4`, `2.4.1`, `2.5` y `2.5.1`) y exporta XMI 2.1 orientado a Enterprise Architect.
- No se importan perfiles propietarios, diagramas gráficos ni extensiones ajenas al modelo de clases interno.
- El generador produce proyectos CRUD ejecutables; las reglas de negocio específicas dependen del dominio modelado.
- La aplicación móvil tiene Android como plataforma objetivo.
- La nube depende de que la instancia AWS EC2 permanezca encendida y conserve su configuración Docker.

## Verificación recomendada para la defensa

1. Iniciar sesión con dos usuarios en navegadores distintos.
2. Abrir el mismo proyecto e iniciar una sesión colaborativa.
3. Mostrar anfitrión, participantes, cursores y sincronización de una clase.
4. Exportar XMI, volver a importarlo y editar el resultado.
5. Generar el backend y comprobar el ZIP, la colección Postman y el proyecto Flutter.
6. Ejecutar ArqNova Mobile en Android y demostrar una operación online y otra offline.
