/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */
package com.luisppb16.collectionsync.infrastructure.insomnia;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.luisppb16.collectionsync.domain.model.CollectionModel;
import com.luisppb16.collectionsync.domain.model.RequestDescriptor;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InsomniaRoundTripTest {

  @TempDir
  static Path tempDirectory;

  private static InsomniaImporter importer;
  private static InsomniaExporter exporter;

  @BeforeAll
  static void givenTheMappers() {
    importer = new InsomniaImporter();
    exporter = new InsomniaExporter(java.time.Instant.parse("2026-09-10T10:00:00Z"));
  }

  @Test
  @DisplayName("Given an Insomnia export, when exported as YAML and re-imported, then the model is equivalent")
  void roundTripPreservesModel() throws IOException {
    CollectionModel original = importer.importFile(Path.of("src/test/resources/collections/insomnia-v4-sample.yaml"));

    Path exported = tempDirectory.resolve("roundtrip.yaml");
    exporter.exportFile(original, exported);
    CollectionModel reimported = importer.importFile(exported);

    assertThat(reimported.name()).isEqualTo(original.name());
    assertThat(reimported.variables()).isEqualTo(original.variables());
    assertThat(reimported.root().findChild("Users").getRequests()).hasSize(2);
  }

  @Test
  @DisplayName("Given an imported collection, when exported twice, then resource ids are stable")
  void keepsStableResourceIds() throws IOException {
    CollectionModel original = importer.importFile(Path.of("src/test/resources/collections/insomnia-v4-sample.yaml"));

    Path first = tempDirectory.resolve("first.yaml");
    Path second = tempDirectory.resolve("second.yaml");
    exporter.exportFile(original, first);
    exporter.exportFile(original, second);

    com.fasterxml.jackson.dataformat.yaml.YAMLFactory yamlFactory = new com.fasterxml.jackson.dataformat.yaml.YAMLFactory();
    JsonNode firstDocument = new com.fasterxml.jackson.databind.ObjectMapper(yamlFactory).readTree(first.toFile());
    JsonNode secondDocument = new com.fasterxml.jackson.databind.ObjectMapper(new com.fasterxml.jackson.dataformat.yaml.YAMLFactory()).readTree(second.toFile());
    assertThat(firstDocument.path("resources")).hasSameSizeAs(secondDocument.path("resources"));
    assertThat(firstDocument.path("resources").get(0).path("_id").asText())
        .isEqualTo(secondDocument.path("resources").get(0).path("_id").asText());
    assertThat(firstDocument.path("resources").get(0).path("_id").asText())
        .isEqualTo("wrk_5f4c3b2a111122223333444455556666");
  }

  @Test
  @DisplayName("Given an imported collection, when exported, then the request parentIds still resolve")
  void parentIdsResolveAfterExport() throws IOException {
    CollectionModel original = importer.importFile(Path.of("src/test/resources/collections/insomnia-v4-sample.yaml"));

    Path exported = tempDirectory.resolve("parents.yaml");
    exporter.exportFile(original, exported);
    CollectionModel reimported = importer.importFile(exported);

    assertThat(reimported.root().findChild("Users").getRequests()).hasSize(2);
    RequestDescriptor create = reimported.root().findChild("Users").getRequests().get(1);
    assertThat(create.body().get("email").asText()).isEqualTo("ada@example.com");
  }

  @Test
  @DisplayName("Given the same model, when exported twice as JSON, then the documents are identical")
  void repeatedExportsAreIdentical() throws IOException {
    CollectionModel original = importer.importFile(Path.of("src/test/resources/collections/insomnia-v4-sample.yaml"));

    Path first = tempDirectory.resolve("a.json");
    Path second = tempDirectory.resolve("b.json");
    exporter.exportFile(original, first);
    exporter.exportFile(original, second);

    assertThat(new String(java.nio.file.Files.readAllBytes(first))).isEqualTo(new String(java.nio.file.Files.readAllBytes(second)));
  }
}