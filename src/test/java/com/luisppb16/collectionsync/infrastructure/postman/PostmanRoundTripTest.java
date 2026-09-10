/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */
package com.luisppb16.collectionsync.infrastructure.postman;

import static org.assertj.core.api.Assertions.assertThat;

import com.luisppb16.collectionsync.domain.model.CollectionModel;
import com.luisppb16.collectionsync.domain.model.RequestDescriptor;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PostmanRoundTripTest {

  @TempDir
  static Path tempDirectory;

  private static PostmanImporter importer;
  private static PostmanExporter exporter;

  @BeforeAll
  static void givenTheImporters() {
    importer = new PostmanImporter();
    exporter = new PostmanExporter();
  }

  @Test
  @DisplayName("Given a Postman collection, when exported and re-imported, then the model is equivalent")
  void roundTripPreservesModel() throws IOException {
    CollectionModel original = importer.importFile(Path.of("src/test/resources/collections/postman-v21-sample.json"));

    Path exported = tempDirectory.resolve("roundtrip.json");
    exporter.exportFile(original, exported);
    CollectionModel reimported = importer.importFile(exported);

    assertThat(reimported.name()).isEqualTo(original.name());
    assertThat(reimported.variables()).isEqualTo(original.variables());
    RequestDescriptor createOriginal = original.root().findChild("Users").getRequests().get(2);
    RequestDescriptor createReimported = reimported.root().findChild("Users").getRequests().get(2);
    assertThat(createReimported.method()).isEqualTo(createOriginal.method());
    assertThat(createReimported.rawUrl()).isEqualTo(createOriginal.rawUrl());
    assertThat(createReimported.normalizedTemplate()).isEqualTo(createOriginal.normalizedTemplate());
    assertThat(createReimported.body()).isEqualTo(createOriginal.body());
    assertThat(createReimported.headers().get(0).name()).isEqualTo("X-Auth");
  }

  @Test
  @DisplayName("Given a request with event scripts, when exported, then the scripts survive the export")
  void roundTripPreservesScripts() throws IOException {
    CollectionModel original = importer.importFile(Path.of("src/test/resources/collections/postman-v21-sample.json"));

    Path exported = tempDirectory.resolve("scripts-roundtrip.json");
    exporter.exportFile(original, exported);
    CollectionModel reimported = importer.importFile(exported);

    RequestDescriptor listReimported = reimported.root().findChild("Users").getRequests().get(0);
    assertThat(listReimported.rawExtra().path("event").isArray()).isTrue();
    assertThat(listReimported.rawExtra().path("event").get(0).path("listen").asText()).isEqualTo("test");
  }

  @Test
  @DisplayName("Given a generated request with body, when exported, then it becomes a full Postman item")
  void exportsGeneratedRequestsAsItems() throws IOException {
    com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
    RequestDescriptor generated = new RequestDescriptor(
        "UserController · POST /users", com.luisppb16.collectionsync.domain.model.HttpMethod.POST,
        "{{baseUrl}}/users", "users", java.util.List.of(), java.util.List.of(),
        mapper.readTree("{\"name\": \"Ada\"}"), "UserController",
        com.luisppb16.collectionsync.domain.model.RequestSource.GENERATED, null);
    com.luisppb16.collectionsync.domain.model.CollectionFolder root = new com.luisppb16.collectionsync.domain.model.CollectionFolder("gen");
    root.addRequest(generated);
    com.luisppb16.collectionsync.domain.model.CollectionModel collection =
        new com.luisppb16.collectionsync.domain.model.CollectionModel(
            "id-1", "gen", root, java.util.Map.of(), com.luisppb16.collectionsync.domain.model.RequestSource.POSTMAN.name());

    com.fasterxml.jackson.databind.JsonNode rootJson = exporter.toJson(collection);
    com.fasterxml.jackson.databind.JsonNode item = rootJson.path("item").get(0);

    assertThat(item.path("name").asText()).isEqualTo("UserController · POST /users");
    assertThat(item.path("request").path("method").asText()).isEqualTo("POST");
    assertThat(item.path("request").path("url").path("raw").asText()).isEqualTo("{{baseUrl}}/users");
    assertThat(item.path("request").path("body").path("raw").asText()).contains("\"name\"");
  }
}