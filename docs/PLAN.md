# Plan: Plugin IntelliJ "CollectionSync" (Postman/Insomnia ↔ endpoints)

## Contexto

El usuario quiere un nuevo plugin de IntelliJ que haga seguimiento entre collections de Postman/Insomnia y los endpoints REST definidos en el código del proyecto. El objetivo: saber en todo momento qué endpoints del código no tienen request en la collection (cobertura), diseñar las requests que faltan (happy path, bodies desde los DTOs, params/headers), completar collections importadas sin duplicados, y exportar/importar en Postman v2.1 e Insomnia v4.

Decisiones ya tomadas con el usuario:
- **Nombre**: **CollectionSync**, id `com.luisppb16.collectionsync`.
- **Frameworks**: Spring MVC/Boot, JAX-RS (Quarkus), Micronaut.
- **Origen de collections**: importar archivo existente Y crear desde cero escaneando el proyecto; además completar lo que falte de un archivo dado (merge).
- **Cobertura**: tool window por endpoint + generación automática de requests faltantes.
- **Formatos**: Postman v2.1 e Insomnia v4 (import + export). Sin ejecutor de requests.
- **Compatibilidad**: Java 25, `since-build 262`, target IDEA 2026.2 (como VulnSpotter).
- **Entrega**: fases en un solo proyecto, cada fase verificable.

## Stack y estructura (replicado de los otros 3 plugins, revisados a fondo)

Proyecto nuevo en `/Users/luispepe/Proyectos/IntelliJ IDEA/Plugins IntelliJ/collection-sync-plugin/` (git propio, remoto GitHub como los demás).

- **build.gradle** (Groovy): `id 'java'`, `id 'org.jetbrains.intellij.platform' version '2.18.1'`, `id 'jacoco'`; group `com.luisppb16`, version `1.0.0`; repos `mavenCentral()` + `intellijPlatform.defaultRepositories()`.
- **Dependencias**: Jackson `jackson-databind` + `jackson-dataformat-yaml` 2.21.5 (mismo pin que VulnSpotter), JUnit 4.13.2 (classpath plataforma) + JUnit 5 + Mockito + AssertJ + Instancio.
- **`intellijPlatform`**: `intellijIdeaCommunity '2026.2'` + `bundledPlugin 'com.intellij.java'`; `signing/publishing` por env vars; `pluginVerification { recommended() }`; `buildSearchableOptions = false`.
- **Toolchain Java 25**, `-Xlint:all -Werror`, `gradle.properties` con `-Xmx2g -XX:MaxMetaspaceSize=512m` + parallel + caching, copyright header 2026 Luis Paolo Pepe Barra (@LuisPPB16) en todos los archivos, Google Format + PMD.
- **CI**: copiar `ci.yml`, `gradle.yml`, `release.yml` de VulnSpotter (JDK 25 temurin, build+test+JaCoCo+verifyPlugin, release con secrets de firma).
- **plugin.xml**: `since-build="262"`, vendor igual, `notificationGroup` BALLOON, toolWindow, actions en Tools, y dependencia opcional degradable:

```xml
<depends>com.intellij.modules.platform</depends>
<depends optional="true" config-file="withJava.xml">com.intellij.modules.java</depends>
```
El plugin carga en toda la familia JetBrains; en IDEs sin soporte Java (WebStorm, PyCharm…) quedan activos import/export/editor de requests y el descubrimiento de endpoints queda oculto con aviso. Todo el código que toca PSI Java vive en `infrastructure.psi` + acciones Java; `domain`/`application` no referencian PSI (records puros).

**Paquetes** (hexagonal como OpenAPI-Generator/VulnSpotter):
```
com.luisppb16.collectionsync
├── domain
│   ├── model        // records: EndpointDescriptor, RequestDescriptor, CollectionModel, CoverageReport...
│   └── service      // MatchEngine, PathTemplateNormalizer, MergePlanner (puro, sin IntelliJ API)
├── application
│   └── service      // ScanProjectUseCase, ImportCollectionUseCase, ComputeCoverageUseCase,
│                    // GenerateRequestsUseCase, MergeCollectionUseCase, ExportCollectionUseCase
├── infrastructure
│   ├── psi          // escáneres por framework + DtoShapeResolver (spring/jaxrs/micronaut/common)
│   ├── postman      // DTOs Jackson v2.1 + mapper
│   ├── insomnia     // DTOs Jackson v4 + mapper
│   ├── io           // lectura/escritura, detección de formato, VFS helpers
│   └── gen          // ExampleValueGenerator, JsonBodyGenerator
├── ui               // toolwindow / action / dialog / settings / icons
└── util
```

## Modelo de dominio (records, sin IntelliJ API)

- `HttpMethod` enum; `EndpointDescriptor` (controlador, método Java, httpMethods, basePath, pathTemplate normalizado, `List<ParamDescriptor>`, `DtoShape requestBody`, consumes/produces, framework, fileUrl+line para navegación); `ParamDescriptor` (name, location PATH/QUERY/HEADER, tipo, required); `DtoShape`/`DtoProperty` (árbol recursivo de propiedades); `RequestDescriptor` (name, method, rawUrl con `{{baseUrl}}`/`_.base_url`, normalizedTemplate, query/headers, `JsonNode body`, folderPath, source POSTMAN|INSOMNIA|GENERATED, `rawExtra` passthrough); `CollectionModel` (id, name, árbol de `CollectionFolder`, variables, format).
- Cobertura: `CoverageReport` con `covered` (nivel), `missing` (endpoints sin request), `orphans` (requests sin endpoint, con top-3 candidatos) y stats (% global, por método, por controlador). `CoverageLevel: FULL | PARTIAL | BARE`. Cacheado en un `@Service(Project)` tras recalcular bajo demanda.

## Descubrimiento de endpoints (PSI propio, no la API de Endpoints de JetBrains)

**Por qué PSI propio**: la API `com.intellij.microservices.endpoints` es Ultimate-only (no existe en Community), experimental y con superficie cambiante; además no resuelve DTOs ni params tipados. PSI propio funciona en Community y Ultimate. El modelo es agnóstico del framework, así que un adaptador a esa API puede añadirse después como mejora opcional.

- **Detección**: `JavaAnnotationIndex`/`StubIndex` sobre anotaciones ancla (`@RestController`, `@RequestMapping`, `@Path`, `@Controller`) restringido al proyecto; fallback estable `FileTypeIndex` + `JavaRecursiveElementWalkingVisitor`. Servicio de proyecto `EndpointIndexService` con cache por `VirtualFile`.
- **Ejecución**: nada en modo dumb (`DumbService.runWhenSmart`); `ReadAction.nonBlocking`/`Task.Backgroundable` con progreso y cancelación; escaneo incremental por fichero.
- **Spring**: `@RestController`/`@Controller`; `@RequestMapping` de clase → basePath; `@GetMapping/@PostMapping/...` de método; `@RequestMapping` sin method → ALL (se expande a GET como happy path, marcado ambiguo); `@PathVariable` (soporta `{id:\d+}`), `@RequestParam` (required/defaultValue), `@RequestHeader`, `@RequestBody`; ignora `@FeignClient`.
- **JAX-RS/Quarkus**: `@Path` clase+método, `@GET/@POST/...`, `@PathParam/@QueryParam/@HeaderParam`, `@Consumes/@Produces`; extras baratos de Quarkus: `@RestPath/@RestQuery/@RestHeader`; params sin anotación simples → query implícita (legacy RESTEasy).
- **Micronaut**: `@Controller` + `@Get/@Post/@Put/@Delete/@Patch/@Trace/@Head/@Options` (`io.micronaut.http.annotation`), `@PathVariable/@QueryParam/@Header/@Body`.
- **Normalización de templates** (`PathTemplateNormalizer`, unidad clave de tests): `{id}`, `{id:\d+}`, `:id` → VARIABLE(name); segments LITERAL/VARIABLE; concatenación clase+método; wildcards `/**`.
- **Resolución de DTOs** (`DtoShapeResolver`): `PsiType` del `@RequestBody`/`@Body` → POJO (campos de instancia no estáticos en orden de declaración + superclases), records (componentes), enums (primer valor); **Lombok funciona sin resolver métodos generados** (los campos están en PSI); excluir `@JsonIgnore`/transient; colecciones/arrays → 1 elemento; `Map` → objeto sintético; `Optional<T>` unwrap; tipos no resueltos → placeholder `"string"`. Guardas: profundidad máx. 4 + set de clases visitadas (corta ciclos), configurable en settings.
- **Cache/invalidación**: `PsiModificationTracker` + listener ligero con debounce (~500 ms) para marcar ficheros sucios y recomputar solo los cambiados. Sin `FileBasedIndex` propio en v1.

## Import/export (Jackson)

Mapeo `Postman v2.1 ⇄ CollectionModel ⇄ Insomnia v4` con `CollectionImporter`/`CollectionExporter` por formato.

- **Postman v2.1** (`infrastructure/postman`): `info` con schema v2.1.0, `item[]` anidado, `request.{method,url,header,body(mode:raw)}`, `variable[]`; DTOs Jackson con `@JsonAnyGetter/@JsonAnySetter` para passthrough fiel de scripts/auth/responses/`protocolProfileBehavior` (round-trip fiel de lo que no tocamos).
- **Insomnia v4** (`infrastructure/insomnia`): documento con `__export_format: 4`, recursos `workspace/request/request_group/environment` con `_id`/`parentId` (generar IDs estables y mantener referencias — punto más delicado, fixtures reales desde el día 1); import acepta **JSON y YAML**, export por defecto YAML con opción JSON.
- **Política de features sin equivalencia**: scripts y auth → passthrough en `rawExtra`, nunca generados; responses/examples de Postman → passthrough puro; bodies formdata/urlencoded → representación simple en v1, no generados desde DTOs (limitación documentada).
- **Detección de formato** al importar: `__export_format` → Insomnia; `info._postman_id`/`info.schema` → Postman; extensión `.yaml/.yml` → YAML.

## Motor de matching (puro, 100 % JUnit)

`domain/service/MatchEngine`:
1. Normalización de ambos lados con `PathTemplateNormalizer`.
2. **Match duro**: método compatible (request ∈ endpoint methods o endpoint ALL) **y** template idéntico segmento a segmento (literal↔literal, variable↔variable). Niveles: `FULL` (además query params required + headers + body cuando el endpoint espera uno), `PARTIAL` (falta params/headers/body), `BARE` (solo método+path).
3. **Matching flexible** para orphans/sugerencias: score 0–1 ponderado (template 0.6, método 0.15, query 0.15, headers 0.05, body presence 0.05); threshold 0.85; por debajo → orphan con top-3 candidatos.
4. Un request solo se asigna a un endpoint (mayor score); duplicados de requests se marcan en el reporte. Limpiar `{{...}}` de la URL antes de normalizar.

## Generación de requests

`GenerateRequestsUseCase` + `infrastructure/gen`:
- **Naming**: `"{controller} · {httpMethod} {pathTemplate}"` (estilo configurable en settings).
- **URL**: variable de entorno `{{baseUrl}}` (Postman) / `_.base_url` (Insomnia) + template; path params → variables; query params con defaultValue o marcados `disabled` si opcionales; headers `Content-Type`/`Accept` desde consumes/produces y ejemplos para `@RequestHeader`.
- **Body**: `JsonBodyGenerator` recorre `DtoShape` con `ExampleValueGenerator` (reutilizando ideas del generador de ejemplos de OpenAPI-Generator — `ExampleGenerationDomainService`): enums → primer valor, String → valores realistas por nombre del campo (name/email/uuid), números → 1/10.5, fechas ISO, listas → 1 elemento. **Idempotente** (sin aleatoriedad) para merges estables.
- **Merge sin duplicados**: dedupe por `(method + normalizedTemplate)`; inserción en carpeta existente por nombre de controlador comparado, si no carpeta nueva.

## UI

- **Tool window "CollectionSync"** (ancla right, registrado en `withJava.xml`, patrón Swing de VulnSpotter): toolbar (Scan Now, Import, Export, Generate Missing, filtro) + tres vistas: **Endpoints** (árbol por controlador, badges verde FULL / amarillo PARTIAL / gris BARE / rojo MISSING), **Collection** (árbol importado, MATCHED/ORPHAN), **Coverage** (resumen %, orphans). Doble-click → `OpenFileDescriptor` al método Java. Refresco por `PsiModificationTracker` con debounce.
- **Acciones**: grupo en menú Tools; contextual en editor/Project View sobre controlador (*Design Request for this Endpoint*, *Generate Requests for this Controller*); contextual sobre `.json/.yaml` (*Import Collection*, *Export Collection*).
- **Diálogos**: Import (file chooser → detección formato → selección de entorno → resumen → "compare coverage now"); Export (formato, destino, variables); **Merge preview** (tabla con checkboxes: ADD/método/path/preview body/duplicados; solo se escriben los marcados).
- **Settings** (Tools | CollectionSync): variable baseUrl, formato por defecto, paquetes excluidos, profundidad máx. DTO, estilo de naming, headers de seguridad off por defecto.
- Notificaciones BALLOON con acción "Open coverage panel".

## Fases de entrega

| # | Fase | Contenido | Verificación | Esf. |
|---|---|---|---|---|
| 0 | Scaffold | Repo nuevo (`collection-sync-plugin`), build.gradle, plugin.xml + `withJava.xml`, copyright, PMD, icono, CI copiado de VulnSpotter | `./gradlew build` verde; `runIde` carga en IDEA; degradación en IDE sin Java | S |
| 1 | Dominio + matcher | Records, `PathTemplateNormalizer`, `MatchEngine`, `MergePlanner` | Tests Given-When-Then puros (`{id}` vs `:id` vs regex, ALL, orphans, scoring) | S/M |
| 2 | Postman v2.1 | Importador + exportador + passthrough + variables | Round-trip con fixtures en `src/test/resources` | M |
| 3 | Insomnia v4 | Importador/exportador (JSON+YAML), `_id`/`parentId`, environments | Round-trip con exports reales de Insomnia | M |
| 4 | Discovery Spring | Escáner PSI + `EndpointIndexService` + cache + `DtoShapeResolver` | `BasePlatformTestCase` con fixtures (herencia, Lombok, records, generics); sandbox con proyecto Spring real | L |
| 5 | Generación | `GenerateRequestsUseCase` + `ExampleValueGenerator` + `JsonBodyGenerator` | Tests de idempotencia y tipos (Optional, enums, colecciones, depth limit) | S/M |
| 6 | Tool window | Panel, árbol, colores, resumen, refresco | Sandbox: importar collection de prueba y verificar árbol | M |
| 7 | Merge + export | `MergeCollectionUseCase`, merge preview, crear collection desde cero | Tests del planner + sandbox: importar→generar→exportar→reimportar sin duplicados | M |
| 8 | JAX-RS + Micronaut | Escáneres adicionales reutilizando el core | Fixtures por framework; sandbox Quarkus y Micronaut | M |
| 9 | Polish | Acciones contextuales, settings, notificaciones, README, marketplace | `pluginVerification` (IC + IU), QA manual | S/M |
| 10 | Opcional | Inlays de cobertura, adaptador `com.intellij.microservices.endpoints` (Ultimate), soporte formdata | — | S |

El orden pone lo más testeable y estable (matching, formatos) antes de la parte más dependiente de la plataforma (PSI).

## Riesgos

1. **Churn de APIs PSI**: mitigado con since-build acotado + `pluginVerification recommended()` + fallback PSI-walk.
2. **Rendimiento en proyectos grandes**: scan incremental + cache por fichero + background; objetivo < 2 s en ~1 000 clases.
3. **DTOs no resolubles**: placeholder `"string"` + warning, nunca crash.
4. **Round-trip Insomnia** (resource graph `_id`/`parentId`): fixtures reales desde el día 1.
5. **Tests PSI lentos**: fixtures mínimos; el grueso de tests en dominio/infra puro.
6. **Degradación familia JetBrains**: probar import/export en un IDE sin Java en el QA de cada fase.

## Referencias para copiar el patrón

- `OpenAPI-Generator/build.gradle` — dependencias Jackson, `bundledPlugin 'com.intellij.java'`, verifier.
- `OpenAPI-Generator/.../domain/service/ExampleGenerationDomainService.java` — base para `ExampleValueGenerator`/`JsonBodyGenerator`.
- `VulnSpotter/src/main/resources/META-INF/plugin.xml` — registro de toolWindow Swing, actions, notificationGroup.
- `VulnSpotter/build.gradle`, `VulnSpotter/.github/workflows/*` — CI/release con firma y publicación.
- `VulnSpotter/gradle.properties`, `settings.gradle`, `gradle/wrapper` — configuración básica idéntica.

## Verificación

1. Cada fase: `./gradlew test` verde (unit Given-When-Then) + `./gradlew jacocoTestReport`.
2. Fases 0/9: `./gradlew verifyPlugin` contra IDEs recomendados.
3. Sandbox (`./gradlew runIde`) con proyecto de ejemplo: escanear un controlador Spring real, importar una collection Postman e Insomnia de prueba, revisar cobertura en el tool window, generar faltantes con merge preview, exportar a ambos formatos y reimportar el export sin duplicados ni pérdida de datos (round-trip).
4. QA de degradación: abrir en un IDE sin Java (p. ej. WebStorm) y comprobar que import/export funcionan y el descubrimiento se degrada con aviso.