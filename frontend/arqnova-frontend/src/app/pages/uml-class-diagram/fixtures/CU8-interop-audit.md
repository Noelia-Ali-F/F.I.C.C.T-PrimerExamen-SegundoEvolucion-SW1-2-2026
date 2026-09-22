# CU8: auditoría y prueba real de interoperabilidad

Fecha: 2026-09-18. Enterprise Architect instalado en Wine, LibraryVersion 1604.
Se utilizó exclusivamente una copia del repositorio en /tmp/cu8-ea-isolated.qea.
No se modificó el repositorio EA original ni PostgreSQL. No hubo llamadas Gemini.

## Evidencia real

1. Se crearon en EA, mediante EA.Repository, Paciente y Turno, cuatro atributos,
   reservar(turnoId: int): Boolean y la asociación reserva con cardinalidades 1 y 0..*.
2. EA.ExportPackageXMI exportó el paquete como XMI 2.1 (xmiEA21=11).
3. El parser ArqNova recuperó 2 clases, 4 atributos, un método con un argumento
   normal y retorno Boolean, y una asociación. Los extremos se verificaron por ID
   y clase, no por el orden incidental de memberEnd.
4. El exportador ArqNova generó arqnova-paciente-turno.xmi a partir del modelo.
5. EA.ImportPackageXMI importó realmente ese archivo en la copia aislada.
6. Se consultaron los elementos importados mediante EA.Repository. Evidencia:

```text
CLASS: EA_Model / Paciente
ATTRIBUTE: id : int
ATTRIBUTE: nombre : String
METHOD: reservar : Boolean / PARAMETERS=2
PARAMETER: turnoId : int / KIND=in
PARAMETER: return : Boolean / KIND=return
RELATION: reserva / Association / Paciente 1..1 / Turno 0..*
CLASS: EA_Model / Turno
ATTRIBUTE: fecha : DateTime
ATTRIBUTE: id : int
```

PARAMETERS=2 incluye el retorno UML: hay un argumento normal, no dos.
1..1 es equivalente a 1. Se comprobó la importación real por automatización COM,
no mediante inspección manual de un diagrama dibujado en la interfaz EA.

## Auditoría A–N

| Punto | Hallazgo / resultado |
|---|---|
| A. Error exacto | Los métodos dependían de arq:parameters/arq:returnType, sin ownedParameter estándar. Los metadatos gráficos EA repiten IDs; no son definiciones UML duplicadas. El orden de extremos podía colocar mal el diamante de composición. |
| B. Diferencias | EA usa memberEnd hijos con xmi:idref, tipos referenciados, parámetros direction=in/return y primitivos en xmi:Extension. ArqNova usaba memberEnd como atributo y metadatos propios para métodos. |
| C. Namespaces | XMI http://schema.omg.org/spec/XMI/2.1; UML http://schema.omg.org/spec/UML/2.1; versión XMI 2.1. exporterVersion=6.5 es el valor escrito por EA; el ejecutable real reportó build 1604. |
| D. Clases | packagedElement xmi:type=uml:Class, xmi:id, name y visibility=public. IDs se conservan internamente; visibility no tiene campo en el modelo actual y no se preserva. |
| E. Atributos | ownedAttribute con type hijo xmi:idref o referencia directa. PrimitiveType resuelto por ID; properties/type EA como apoyo. No se elimina un atributo por carecer de tipo; se advierte. |
| F. Métodos | ownedOperation y ownedParameter estándar, separando direction=return. Parámetros se convierten a la cadena nombre: tipo existente, y el retorno a returnType. Exportación estándar con name=return. |
| G. Relaciones | Asociaciones binarias por memberEnd; agregación/composición por aggregation; generalización por general y dependencia por client/supplier, incluidos nodos idref. Extremos inexistentes se omiten con advertencia. |
| H. Multiplicidades | lowerValue/upperValue, incluyendo -1 de EA como ilimitado. Se conservan por extremo/clase. |
| I. Extensiones | Se ignoran repeticiones gráficas; se consultan primitivas y metadatos de tipos/estereotipo/tags explícitos. El modelo básico no exige una extensión. |
| J. Cambios | Métodos y retornos estándar; resolución de metadatos; versión sin prefijo literal; general/dependency idref; PK explícita isID; composición orientada al extremo agregado; lectura según encoding XML/BOM. |
| K. EA → ArqNova | APROBADA para el modelo solicitado, exportado realmente en EA16. Fixture sanitizado incluido. |
| L. ArqNova → EA | APROBADA para el mismo modelo: importación real y consulta de datos vía COM, con evidencia anterior. |
| M. Límites | No garantiza todo el catálogo UML/EA, gráficos propietarios, perfiles ni visibilidad. XMI 1.x no soportado. PK universal y posiciones exactas EA no garantizadas. |
| N. Regresión | Frontend 56/56, spec XMI 13/13, build Angular correcto. Advertencias previas NG8107, presupuestos y CommonJS. |

## Fixtures

- ea16-paciente-turno.xmi: exportación real EA, sanitizada sin cambiar definiciones UML.
- ea16-paciente-turno.fixture.ts: mismo XML como literal para los tests Angular.
- arqnova-paciente-turno.xmi: salida del exportador ArqNova importada realmente en EA.

El fixture EA conserva metadata técnica necesaria; se quitaron autor y timestamps.
La metadata arq es opcional: clases, atributos, métodos, relaciones y cardinalidades
se expresan en XML/UML estándar.

## PK y layout

No se infiere PK por nombre id. Se conserva únicamente información explícita:
arq:isPrimaryKey, tag isPrimaryKey=true o propiedad UML isID=true.
El fixture EA no contiene PK explícita, por lo que sus cuatro atributos quedan false.
No se garantiza que EA preserve arq:isPrimaryKey ni que UML isID sea equivalente a
una PK de base de datos para todos los perfiles. Esa interoperabilidad específica
queda pendiente de un fixture EA que utilice el perfil de PK requerido.

Se preservan posiciones ArqNova en arq:x/y. Sin ellas se aplica un layout de cuadrícula.
Las coordenadas gráficas propietarias EA no se reconstruyen.

## Comandos de regresión

```sh
npm test -- --watch=false
npm test -- --watch=false --include="src/app/pages/uml-class-diagram/uml-xmi.service.spec.ts"
npm run build
```

Ejecutados en Node 20 mediante Docker con las dependencias existentes.
Backend no modificado: no se ejecutó mvn test. CU3/CU5 no se probaron de nuevo
en dos navegadores reales; sus integraciones no fueron modificadas.

Referencia de automatización:
https://www.sparxsystems.com/enterprise_architect_user_guide/16.1/add-ins___scripting/project_2.html

## Corrección visual Hospital

La prueba posterior reprodujo el modelo completo Paciente, Turno, Profesional y
Agenda. El fallo visual tenía dos causas diferentes:

1. El XMI canónico transportaba el modelo, pero EA no crea un diagrama
   automáticamente sin reconocer una extensión Sparx completa.
2. La extensión anterior no incluía xmi:Documentation de EA ni IDs con forma
   GUID EAID_/EAPK_; EA la ignoraba.

El exportador incluye ahora la metadata mínima probada: Documentation EA, IDs
estables con forma GUID, elements/connectors, diagrama Logical, geometría,
Feature Visibility (HideAtts=0, HideOps=0) y referencias al modelo UML
estándar. Los tipos primitivos están en primitivetypes de la extensión para no
aparecer como elementos adicionales en el Browser.

Resultado de la importación real anterior a ese último ajuste de primitivos:

- cuatro clases visibles;
- 17 atributos y un método;
- tres connectors;
- un diagrama Hospital con cuatro DiagramObjects y tres DiagramLinks;
- atributos y operaciones habilitados visualmente;
- etiquetas y cardinalidades visibles.

EA renderizó el propio diagrama mediante PutDiagramImageToFile. La imagen
confirmó las cuatro cajas y las relaciones reserva, atendido por y posee.
Se detectó {bag} en atributos y se corrigió exportando multiplicidad 1..1 en
cada ownedAttribute.

El último ajuste que mueve tipos primitivos fuera del Browser queda cubierto por
el fixture y tests automáticos. La repetición COM final quedó impedida porque el
servidor Wine dejó de crear el objeto ActiveX; no se reinició ni cerró por la
fuerza ninguna sesión EA del usuario.
