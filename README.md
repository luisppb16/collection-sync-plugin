# CollectionSync — Endpoint Coverage Tracker

Plugin de IntelliJ IDEA que compara los **endpoints REST reales** de tu proyecto Java (documento
OpenAPI 3.x o controladores Spring MVC / JAX-RS/Quarkus) contra las peticiones de tus **colecciones Postman (v2.1) e
Insomnia (v4/v5)**, y te dice, endpoint a endpoint, qué está cubierto,
qué falta y qué peticiones se han quedado huérfanas.

## Qué hace

- **Descubre endpoints**: escanea controladores Spring (`@RestController`, `@RequestMapping`…) y
  JAX-RS/Quarkus (`@Path`, `@GET`…), o lee un documento OpenAPI 3.x local, para construir la lista
  de endpoints del proyecto.
- **Lee colecciones**: parsea colecciones Postman v2.1 o Insomnia v4/v5 (JSON y YAML, incluido el
  formato anidado v5). Un fichero roto no aborta el informe: se registra el error y el resto
  sigue contribuyendo.
- **Calcula cobertura**: clasifica cada endpoint como `COVERED`, `UNCOVERED`, `ORPHAN` o
  `EXCLUDED` mediante matching estructural de paths (las variables casan por posición; el modo
  estricto mantiene separados `/users/{id}` y `/users/me`).
- **Exporta el informe**: Markdown o CSV, con resumen y todas las filas (incluidas las
  excluidas).

## Uso

### 1. Configuración

`Settings/Preferences → Tools → Endpoint Coverage`:

- **Escanear cobertura al abrir el proyecto**: activado por defecto (`true`); la primera vez que se
  abre la tool window en el proyecto se lanza un escaneo automático. Al desactivarlo, la tool
  window arranca en su estado vacío y solo los reescaneos manuales (botón *Reescanear*, menú *Tools*, clic derecho)
  recalculan la cobertura.
- **Source type**: `CONTROLLER_ANNOTATIONS` (Spring/JAX-RS) o `OPEN_API` (documento local).
- **OpenAPI file path**: ruta del documento OpenAPI 3.x (solo si el origen es `OPEN_API`).
- **Collection files**: ficheros de colección Postman/Insomnia a comparar (añádelos aquí o desde
  la propia tool window).
- **Exclusions**: reglas de exclusión (método + patrón de path, p. ej. `GET /actuator/{name}`).

### 2. Tool window "Endpoint Coverage"

Anclada a la derecha, disponible en proyectos con módulos Java:

- **Reescanear**: recalcula la cobertura en segundo plano.
- **Seleccionar colecciones…**: añade ficheros de colección al listado y reescanea.
- **Exportar informe**: guarda el informe actual en Markdown (`.md`) o CSV (`.csv`).
- **Filtros**: campo de texto (path/owner/module, insensible a mayúsculas) y combo de estado (All / Covered /
  Uncovered / Orphan / Excluded). La tabla ordena por cualquier columna.
- **Resumen**: `X covered · Y uncovered · Z orphan · W excluded · N collections`.

### 3. Navegación

Doble clic sobre una fila: si el endpoint se descubrió desde el código, abre el método
controlador en el editor; si el origen es `OPEN_API`, abre el documento configurado.

### 4. Exclusiones

- Clic derecho sobre un endpoint (Covered/Uncovered) → **"Excluir de la cobertura"**: añade la
  regla `método + path` a la configuración y reescanea. La exclusión es estricta: excluir
  `/users/{id}` no arrastra al endpoint hermano `/users/me`.
- Clic derecho sobre una fila `EXCLUDED` → **"Quitar exclusión"**: elimina la regla
  correspondiente y reescanea.

### 5. Menú y clic derecho

- **Tools → Analyze Endpoints Coverage**: activa la tool window y lanza un reescaneo en segundo
  plano (equivalente a "Reescanear", disponible sin abrir la tool window).
- **Clic derecho** (en el Project view o en el editor) sobre un fichero `.json` / `.yaml` /
  `.yml`:
    - **Endpoint Coverage: Check Coverage with This Collection**: valida que el fichero es una
      colección Postman v2.1 / Insomnia v4/v5, la añade a *Collection files* (sin duplicados),
      reescanea y abre la tool window. Si no es una colección válida, muestra un error y no toca
      la configuración.
    - **Endpoint Coverage: Use as OpenAPI Source**: valida que el documento contiene un objeto
      `paths`, lo fija como *OpenAPI file path* con source type `OPEN_API`, reescanea y abre la
      tool window. Si el fichero no es un OpenAPI 3.x válido, muestra un error y no toca la
      configuración.

## Construcción y prueba

```bash
./gradlew buildPlugin   # construye el zip del plugin
./gradlew test          # suite de tests (dominio, parsers, settings, PSI)
./gradlew runIde        # lanza un IDE sandbox con el plugin instalado
```

Requiere JDK 21+ (el proyecto compila con toolchain Java 25) e IntelliJ IDEA 2026.2+.

## Idiomas

Toda la UI del plugin (acciones, tool window, página de configuración, notificaciones y mensajes
del escaneo) se sirve desde un único resource bundle estándar de IntelliJ:

- `src/main/resources/messages/EndpointCoverageBundle.properties` — inglés (bundle por defecto, sin
  sufijo).
- `src/main/resources/messages/EndpointCoverageBundle_es.properties` — español.

El IDE resuelve el bundle según el locale del IDE. Los textos de las acciones del menú usan la
convención estándar `action.<actionId>.text` / `action.<actionId>.description` declarada con
`<actions resource-bundle="messages.EndpointCoverageBundle">` en `withJava.xml`; el resto de
cadenas se resuelven desde código con la clase
`com.luisppb16.collectionsync.i18n.EndpointCoverageBundle`.

**Añadir otro idioma**: crea `src/main/resources/messages/EndpointCoverageBundle_xx.properties`
(p. ej. `_fr`, `_de`) con las mismas claves del bundle por defecto y las traducciones del idioma.
Los ficheros `.properties` se leen como UTF-8 (acentos directos, sin escapes `\uXXXX`). El test
`EndpointCoverageBundleTest` valida automáticamente que el nuevo bundle tenga paridad de claves con
el por defecto: si le faltan claves o sobran, el test falla.

No se traducen, por decisión de diseño: la `description` y los `change-notes` de `plugin.xml`
(marketplace, en inglés) y el informe Markdown/CSV exportado (formato estable consumible por
otras herramientas). Los mensajes de excepción internos (fail-fast) son para desarrolladores y
también permanecen en inglés.

## Fuera de alcance

- **Generar** especificaciones OpenAPI (solo se leen documentos existentes).
- **Sincronización en la nube**: Postman Cloud / Insomnia Git Sync.
- **Sub-resource locators** de JAX-RS (`@Path` en métodos que devuelven otro resource).
- **Micronaut** (`@Controller`, `@Get`…).
- **Swagger 2.0** (solo OpenAPI 3.x).

---

Copyright (c) 2026 Luis Paolo Pepe Barra (@LuisPPB16). All rights reserved.