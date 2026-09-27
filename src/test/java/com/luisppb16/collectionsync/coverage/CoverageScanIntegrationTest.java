/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */

package com.luisppb16.collectionsync.coverage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiMethod;
import com.luisppb16.collectionsync.domain.model.CoverageResult;
import com.luisppb16.collectionsync.domain.model.CoverageRow;
import com.luisppb16.collectionsync.domain.model.CoverageStatus;
import com.luisppb16.collectionsync.domain.model.HttpMethod;
import com.luisppb16.collectionsync.settings.EndpointCoverageSettings;
import com.luisppb16.collectionsync.settings.EndpointCoverageSettings.ExclusionEntry;
import com.luisppb16.collectionsync.test.PsiTestCase;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Integration tests of the whole coverage pipeline: real controllers compiled in the fixture PSI
 * (Spring and JAX-RS), real Postman/Insomnia collection files written to disk and the persisted
 * settings (base path, exclusions) fed through {@link CoverageService#scan()}.
 */
@DisplayName("Coverage pipeline (controllers + collections + settings)")
class CoverageScanIntegrationTest extends PsiTestCase {

  private static final String SPRING_PACKAGE = "package org.springframework.web.bind.annotation;";

  private static final String JAX_RS_PACKAGE = "package jakarta.ws.rs;";

  private static final List<String> SPRING_STUBS =
      List.of(
          SPRING_PACKAGE
              + " public enum RequestMethod { GET, HEAD, POST, PUT, PATCH, DELETE, OPTIONS, TRACE }",
          SPRING_PACKAGE
              + " public @interface RequestMapping { String[] value() default {}; String[] path() default {};"
              + " RequestMethod[] method() default {}; }",
          SPRING_PACKAGE
              + " public @interface GetMapping { String[] value() default {}; String[] path() default {}; }",
          SPRING_PACKAGE
              + " public @interface PostMapping { String[] value() default {}; String[] path() default {}; }",
          SPRING_PACKAGE
              + " public @interface PutMapping { String[] value() default {}; String[] path() default {}; }",
          SPRING_PACKAGE
              + " public @interface DeleteMapping { String[] value() default {}; String[] path() default {}; }",
          SPRING_PACKAGE
              + " public @interface PatchMapping { String[] value() default {}; String[] path() default {}; }");

  private static final List<String> JAX_RS_STUBS =
      List.of(
          JAX_RS_PACKAGE + " public @interface Path { String value(); }",
          JAX_RS_PACKAGE + " public @interface GET { }",
          JAX_RS_PACKAGE + " public @interface POST { }",
          JAX_RS_PACKAGE + " public @interface PUT { }",
          JAX_RS_PACKAGE + " public @interface DELETE { }",
          JAX_RS_PACKAGE + " public @interface PATCH { }",
          JAX_RS_PACKAGE + " public @interface HEAD { }",
          JAX_RS_PACKAGE + " public @interface OPTIONS { }");

  /** The exact scenario the base path setting was built for: bare code, prefixed collection. */
  private static final String CARRO_CONTROLLER =
      """
            package com.example;

            @org.springframework.web.bind.annotation.RestController
            public class CarroController {

                @org.springframework.web.bind.annotation.GetMapping("/carro")
                public java.lang.String getCarro() {
                    return null;
                }
            }
            """;

  private static final String COCHE_CONTROLLER =
      """
            package com.example;

            @org.springframework.web.bind.annotation.RestController
            @org.springframework.web.bind.annotation.RequestMapping("/api/test/v1")
            public class CocheController {

                @org.springframework.web.bind.annotation.GetMapping("/coche")
                public java.lang.String getCoche() {
                    return null;
                }
            }
            """;

  private static final String MOTO_CONTROLLER =
      """
            package com.example;

            @org.springframework.web.bind.annotation.RestController
            @org.springframework.web.bind.annotation.RequestMapping("/api/test/v1")
            public class MotoController {

                @org.springframework.web.bind.annotation.PostMapping("/moto")
                public java.lang.String createMoto() {
                    return null;
                }
            }
            """;

  private static final String ORDER_RESOURCE =
      """
            package com.example;

            @jakarta.ws.rs.Path("/api/v1")
            public class OrderResource {

                @jakarta.ws.rs.GET
                @jakarta.ws.rs.Path("/orders/{orderId}")
                public java.lang.String getOrder(long orderId) {
                    return null;
                }
            }
            """;

  private static final String POSTMAN_CARRO_COLLECTION =
      """
            {"info": {"name": "Carro API"}, "item": [
              {"name": "Get carro", "request": {"method": "GET", "url": "{{baseUrl}}/api/test/v1/carro"}}
            ]}""";

  private static final String POSTMAN_COCHE_COLLECTION =
      """
            {"info": {"name": "Coche API"}, "item": [
              {"name": "Get coche", "request": {"method": "GET", "url": "{{baseUrl}}/coche"}}
            ]}""";

  private static final String POSTMAN_VEHICLES_COLLECTION =
      """
            {"info": {"name": "Vehicles API"}, "item": [
              {"name": "Get coche", "request": {"method": "GET", "url": "{{baseUrl}}/api/test/v1/coche"}},
              {"name": "Create moto", "request": {"method": "POST", "url": "{{baseUrl}}/api/test/v1/moto"}}
            ]}""";

  private static final String INSOMNIA_ORDERS_COLLECTION =
      """
            _type: export
            __export_format: 4
            __export_date: 2026-01-01T00:00:00.000Z
            __export_source: collection-sync-plugin-tests
            resources:
              - _id: wrk_1
                _type: workspace
                name: Orders API
              - _id: req_1
                _type: request
                name: Get order
                method: GET
                url: "{{ _.base_url }}/orders/42"
                parentId: wrk_1
            """;

  private static final String INSOMNIA_MOTO_COLLECTION =
      """
            _type: export
            __export_format: 4
            __export_date: 2026-01-01T00:00:00.000Z
            __export_source: collection-sync-plugin-tests
            resources:
              - _id: wrk_2
                _type: workspace
                name: Moto API
              - _id: req_2
                _type: request
                name: Create moto
                method: POST
                url: "{{ _.base_url }}/moto"
                parentId: wrk_2
            """;

  @TempDir Path tempDir;

  @BeforeEach
  void addAnnotationStubs() {
    List.of(SPRING_STUBS, JAX_RS_STUBS).forEach(stubs -> stubs.forEach(this::addClass));
  }

  @Test
  @DisplayName(
      "Given a controller declaring only /carro and a collection prefixing the base path, "
          + "when a scan runs with that base path, then the endpoint is covered end to end")
  void coversCollectionRequestsUnderConfiguredBasePath() throws IOException {
    addClass(CARRO_CONTROLLER);
    Path collection = write("carro.json", POSTMAN_CARRO_COLLECTION);
    configureSettings("/api/test/v1", List.of(collection.toString()));
    CoverageService service = new CoverageService(getFixture().getProject());

    CoverageResult result = service.scan();

    assertThat(result.coveredCount()).isEqualTo(1);
    assertThat(result.uncoveredCount()).isZero();
    assertThat(result.orphanCount()).isZero();
    assertThat(result.rows())
        .singleElement()
        .satisfies(
            row -> {
              assertThat(row.status()).isEqualTo(CoverageStatus.COVERED);
              assertThat(row.method()).isEqualTo(HttpMethod.GET);
              assertThat(row.path()).isEqualTo("/carro");
              assertThat(row.owner()).isEqualTo("com.example.CarroController");
              assertThat(row.module()).isEqualTo(getFixture().getModule().getName());
              assertThat(row.psiMethod()).isNotNull();
              assertThat(psiMethodName(row.psiMethod())).isEqualTo("getCarro");
            });
    assertThat(service.lastErrors()).isEmpty();
  }

  @Test
  @DisplayName(
      "Given the same fixtures with an empty base path, when a scan runs, "
          + "then the endpoint stays uncovered and the request stays orphaned")
  void keepsPairUncoveredWithoutConfiguredBasePath() throws IOException {
    addClass(CARRO_CONTROLLER);
    Path collection = write("carro.json", POSTMAN_CARRO_COLLECTION);
    configureSettings("", List.of(collection.toString()));
    CoverageService service = new CoverageService(getFixture().getProject());

    CoverageResult result = service.scan();

    assertThat(result.coveredCount()).isZero();
    assertThat(result.uncoveredCount()).isEqualTo(1);
    assertThat(result.orphanCount()).isEqualTo(1);
    assertThat(result.rows())
        .extracting(CoverageRow::status, CoverageRow::path, CoverageRow::owner)
        .containsExactlyInAnyOrder(
            tuple(CoverageStatus.UNCOVERED, "/carro", "com.example.CarroController"),
            tuple(CoverageStatus.ORPHAN, "{{baseUrl}}/api/test/v1/carro", "Carro API"));
  }

  @Test
  @DisplayName(
      "Given a controller whose class mapping carries the base path and a collection that does not, "
          + "when a scan runs with that base path, then the endpoint is covered and keeps its full path")
  void coversRequestsWhenPrefixLivesOnlyInTheCode() throws IOException {
    addClass(COCHE_CONTROLLER);
    Path collection = write("coche.json", POSTMAN_COCHE_COLLECTION);
    configureSettings("/api/test/v1", List.of(collection.toString()));
    CoverageService service = new CoverageService(getFixture().getProject());

    CoverageResult result = service.scan();

    assertThat(result.coveredCount()).isEqualTo(1);
    assertThat(result.orphanCount()).isZero();
    assertThat(result.rows())
        .singleElement()
        .satisfies(
            row -> {
              assertThat(row.status()).isEqualTo(CoverageStatus.COVERED);
              assertThat(row.path()).isEqualTo("/api/test/v1/coche");
              assertThat(row.owner()).isEqualTo("com.example.CocheController");
            });
  }

  @Test
  @DisplayName(
      "Given endpoints of the same root spread across several controllers and a collection with all of them, "
          + "when a scan runs, then every endpoint is covered with its own owner")
  void coversEndpointsWhoseRootIsSplitAcrossControllers() throws IOException {
    addClass(COCHE_CONTROLLER);
    addClass(MOTO_CONTROLLER);
    Path collection = write("vehicles.json", POSTMAN_VEHICLES_COLLECTION);
    configureSettings("/api/test/v1", List.of(collection.toString()));
    CoverageService service = new CoverageService(getFixture().getProject());

    CoverageResult result = service.scan();

    assertThat(result.coveredCount()).isEqualTo(2);
    assertThat(result.orphanCount()).isZero();
    assertThat(result.rows())
        .extracting(CoverageRow::method, CoverageRow::path, CoverageRow::owner)
        .containsExactlyInAnyOrder(
            tuple(HttpMethod.GET, "/api/test/v1/coche", "com.example.CocheController"),
            tuple(HttpMethod.POST, "/api/test/v1/moto", "com.example.MotoController"));
  }

  @Test
  @DisplayName(
      "Given an exclusion rule persisted with the displayed path, when a scan runs, "
          + "then the endpoint is excluded, its request is hidden and the sibling stays covered")
  void excludesEndpointsConfiguredInSettings() throws IOException {
    addClass(COCHE_CONTROLLER);
    addClass(MOTO_CONTROLLER);
    Path collection = write("vehicles.json", POSTMAN_VEHICLES_COLLECTION);
    configureSettingsWithExclusions(
        "/api/test/v1",
        List.of(collection.toString()),
        List.of(new ExclusionEntry("GET", "/api/test/v1/coche")));
    CoverageService service = new CoverageService(getFixture().getProject());

    CoverageResult result = service.scan();

    assertThat(result.excludedCount()).isEqualTo(1);
    assertThat(result.excludedRows())
        .singleElement()
        .satisfies(row -> assertThat(row.path()).isEqualTo("/api/test/v1/coche"));
    assertThat(result.coveredCount()).isEqualTo(1);
    assertThat(result.orphanCount()).isZero();
    assertThat(result.rows())
        .singleElement()
        .satisfies(
            row -> {
              assertThat(row.status()).isEqualTo(CoverageStatus.COVERED);
              assertThat(row.path()).isEqualTo("/api/test/v1/moto");
            });
  }

  @Test
  @DisplayName(
      "Given a JAX-RS resource with the base path on the class and an Insomnia collection using a variable host, "
          + "when a scan runs with that base path, then the endpoint is covered")
  void coversJaxRsEndpointsFromInsomniaCollections() throws IOException {
    addClass(ORDER_RESOURCE);
    Path collection = write("orders.yaml", INSOMNIA_ORDERS_COLLECTION);
    configureSettings("/api/v1", List.of(collection.toString()));
    CoverageService service = new CoverageService(getFixture().getProject());

    CoverageResult result = service.scan();

    assertThat(result.coveredCount()).isEqualTo(1);
    assertThat(result.orphanCount()).isZero();
    assertThat(result.collectionCount()).isEqualTo(1);
    assertThat(result.rows())
        .singleElement()
        .satisfies(
            row -> {
              assertThat(row.status()).isEqualTo(CoverageStatus.COVERED);
              assertThat(row.method()).isEqualTo(HttpMethod.GET);
              assertThat(row.path()).isEqualTo("/api/v1/orders/{orderId}");
              assertThat(row.owner()).isEqualTo("com.example.OrderResource");
            });
  }

  @Test
  @DisplayName(
      "Given Postman and Insomnia files covering different endpoints, when a scan runs, "
          + "then every collection file contributes to the report")
  void computesCoverageAcrossSeveralCollectionFiles() throws IOException {
    addClass(COCHE_CONTROLLER);
    addClass(MOTO_CONTROLLER);
    Path postman = write("vehicles.json", POSTMAN_VEHICLES_COLLECTION);
    Path insomnia = write("moto.yaml", INSOMNIA_MOTO_COLLECTION);
    configureSettings("/api/test/v1", List.of(postman.toString(), insomnia.toString()));
    CoverageService service = new CoverageService(getFixture().getProject());

    CoverageResult result = service.scan();

    assertThat(result.coveredCount()).isEqualTo(2);
    assertThat(result.orphanCount()).isZero();
    assertThat(result.collectionCount()).isEqualTo(2);
    assertThat(service.lastErrors()).isEmpty();
    assertThat(result.rows())
        .extracting(CoverageRow::method, CoverageRow::path)
        .containsExactlyInAnyOrder(
            tuple(HttpMethod.GET, "/api/test/v1/coche"),
            tuple(HttpMethod.POST, "/api/test/v1/moto"));
  }

  private void addClass(String source) {
    getFixture().addClass(source);
  }

  private void configureSettings(String basePath, List<String> collectionPaths) {
    configureSettingsWithExclusions(basePath, collectionPaths, List.of());
  }

  private void configureSettingsWithExclusions(
      String basePath, List<String> collectionPaths, List<ExclusionEntry> exclusions) {
    EndpointCoverageSettings.State state = new EndpointCoverageSettings.State();
    state.basePath = basePath;
    state.collectionFilePaths.addAll(collectionPaths);
    state.exclusions.addAll(exclusions);
    EndpointCoverageSettings.getInstance(getFixture().getProject()).setState(state);
  }

  private Path write(String fileName, String content) throws IOException {
    Path path = tempDir.resolve(fileName);
    Files.writeString(path, content);
    return path;
  }

  private String psiMethodName(PsiMethod psiMethod) {
    // PSI access from a background thread requires a read action.
    return ApplicationManager.getApplication()
        .runReadAction((Computable<String>) psiMethod::getName);
  }
}
