# CollectionSync

> Keep your Postman and Insomnia collections in sync with your code.

CollectionSync is an IntelliJ IDEA plugin that discovers the REST endpoints declared in your
Java project (Spring, JAX-RS/Quarkus and Micronaut), imports your existing **Postman (v2.1)** or
**Insomnia (v4)** collections and shows you, endpoint by endpoint, which requests are missing,
incomplete or orphaned. It designs the missing requests for you — happy path, method, path,
body built from your DTOs, query/path parameters and headers — and lets you merge them into the
collection and export it back to Postman or Insomnia.

## Requirements

| Requirement | Version |
|-------------|---------|
| IntelliJ IDEA | 2026.2+ (Community or Ultimate) |
| Java | 21+ (bundled with the IDE) |

Endpoint discovery requires Java support in the IDE. In other JetBrains IDEs (WebStorm, PyCharm,
…), collection import/export remains available and endpoint tracking is gracefully disabled.

## Status

Under active development — see the [plan](docs/PLAN.md) for the phase roadmap.

## Building

```bash
./gradlew build          # build + test
./gradlew runIde         # launch a sandbox IDE with the plugin
./gradlew verifyPlugin   # verify against recommended IDEs
```

---

Copyright (c) 2026 Luis Paolo Pepe Barra (@LuisPPB16). All rights reserved.