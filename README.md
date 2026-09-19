# CollectionSync — Endpoint Coverage Tracker

IntelliJ IDEA plugin that compares the **real REST endpoints** of your Java project (an OpenAPI
3.x document or Spring MVC / JAX-RS/Quarkus controllers) against the requests in your **Postman
(v2.1) and Insomnia (v4/v5) collections**, and tells you, endpoint by endpoint, what is covered,
what is missing and which requests have been left orphaned.

## What it does

- **Discovers endpoints**: scans Spring controllers (`@RestController`, `@RequestMapping`…) and
  JAX-RS/Quarkus (`@Path`, `@GET`…), or reads a local OpenAPI 3.x document, to build the project's
  endpoint list.
- **Reads collections**: parses Postman v2.1 or Insomnia v4/v5 collections (JSON and YAML,
  including the nested v5 format). A broken file does not abort the report: the error is recorded
  and the rest keeps contributing.
- **Computes coverage**: classifies each endpoint as `COVERED`, `UNCOVERED`, `ORPHAN` or
  `EXCLUDED` through structural path matching (variables match by position; strict mode keeps
  `/users/{id}` and `/users/me` apart).
- **Exports the report**: Markdown or CSV, with a summary and every row (excluded ones included).

## Usage

### 1. Configuration

`Settings/Preferences → Tools → Endpoint Coverage`:

- **Scan coverage when the project opens**: enabled by default (`true`); the first time the tool
  window is opened in the project, an automatic scan is launched. When disabled, the tool window
  starts in its empty state and only manual rescans (the *Rescan* button, the *Tools* menu,
  right-click) recompute coverage.
- **Source type**: `CONTROLLER_ANNOTATIONS` (Spring/JAX-RS) or `OPEN_API` (local document).
- **OpenAPI file**: path of the OpenAPI 3.x document (only when the source is `OPEN_API`).
- **Collection files**: Postman/Insomnia collection files to compare against (add them here or
  from the tool window itself).
- **Exclusions**: exclusion rules (method + path pattern, e.g. `GET /actuator/{name}`).

### 2. "Endpoint Coverage" tool window

Docked on the right, available in projects with Java modules:

- **Rescan**: recomputes coverage in the background.
- **Select collections…**: adds collection files to the list and rescans.
- **Export report**: saves the current report as Markdown (`.md`) or CSV (`.csv`).
- **Filters**: text field (path/owner/module, case-insensitive) and a status combo (All / Covered /
  Uncovered / Orphan / Excluded). The table sorts by any column.
- **Summary**: `X covered · Y uncovered · Z orphan · W excluded · N collections`.
- **Scan errors**: if a collection cannot be read or no longer exists, the failure is reported;
  when the file no longer exists, the notification includes the **"Remove collections that no
  longer exist"** action, which removes them from the settings and rescans. If the scan finds no
  endpoints in the project (0 covered, 0 uncovered and 0 excluded), that is reported too, so an
  empty scan is not mistaken for zero coverage.

### 3. Navigation

Double-click a row: if the endpoint was discovered from code, the controller method opens in the
editor; when the source is `OPEN_API`, the configured document opens.

### 4. Exclusions

- Right-click an endpoint (Covered/Uncovered) → **"Exclude from coverage"**: adds the
  `method + path` rule to the settings and rescans. The exclusion is strict: excluding
  `/users/{id}` does not drag along the sibling endpoint `/users/me`.
- Right-click an `EXCLUDED` row → **"Remove exclusion"**: removes the matching rule and rescans.

### 5. Menu and right-click

- **Tools → Analyze Endpoints Coverage**: focuses the tool window and launches a background rescan
  (equivalent to "Rescan", available without opening the tool window).
- **Right-click** (in the Project view or the editor) a file the IDE recognizes as JSON, YAML or
  plain text (the extension does not matter):
  - **Endpoint Coverage: Check Coverage with This Collection**: validates that the file is a
    Postman v2.1 / Insomnia v4/v5 collection with at least one request, adds it to *Collection
    files* (no duplicates), rescans and opens the tool window. If the file no longer exists, or is
    not a valid collection (or is empty), shows an error and leaves the settings untouched.
  - **Endpoint Coverage: Use as OpenAPI Source**: validates that the document contains a `paths`
    object, sets it as the *OpenAPI file* with source type `OPEN_API`, rescans and opens the tool
    window. If the file is not a valid OpenAPI 3.x document, shows an error and leaves the
    settings untouched.

## Building and testing

```bash
./gradlew buildPlugin   # builds the plugin zip
./gradlew test          # test suite (domain, parsers, settings, PSI)
./gradlew runIde        # launches a sandbox IDE with the plugin installed
```

Requires JDK 21+ (the project compiles with the Java 25 toolchain) and IntelliJ IDEA 2026.2+.

## Languages

All of the plugin UI (actions, tool window, settings page, notifications and scan messages) is
served from a single standard IntelliJ resource bundle:

- `src/main/resources/messages/EndpointCoverageBundle.properties` — English (default bundle, no
  suffix).
- `src/main/resources/messages/EndpointCoverageBundle_es.properties` — Spanish.

The IDE resolves the bundle according to the IDE locale. Menu action texts use the standard
`action.<actionId>.text` / `action.<actionId>.description` convention declared with
`<actions resource-bundle="messages.EndpointCoverageBundle">` in `withJava.xml`; the remaining
strings are resolved from code through the
`com.luisppb16.collectionsync.i18n.EndpointCoverageBundle` class.

**Adding another language**: create `src/main/resources/messages/EndpointCoverageBundle_xx.properties`
(e.g. `_fr`, `_de`) with the same keys as the default bundle and the translations for that
language. The `.properties` files are read as UTF-8 (direct accents, no `\uXXXX` escapes). The
`EndpointCoverageBundleTest` test automatically validates that the new bundle has key parity with
the default one: missing or extra keys fail the test.

Not translated, by design: the `description` and `change-notes` of `plugin.xml` (marketplace,
English) and the exported Markdown/CSV report (stable format consumable by other tools). Internal
exception messages (fail-fast) are developer-facing and stay in English as well.

## Out of scope

- **Generating** OpenAPI specifications (only existing documents are read).
- **Cloud sync**: Postman Cloud / Insomnia Git Sync.
- JAX-RS **sub-resource locators** (`@Path` on methods returning another resource).
- **Micronaut** (`@Controller`, `@Get`…).
- **Swagger 2.0** (OpenAPI 3.x only).

---

Copyright (c) 2026 Luis Paolo Pepe Barra (@LuisPPB16). All rights reserved.