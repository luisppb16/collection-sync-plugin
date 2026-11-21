/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */

package com.luisppb16.collectionsync.io;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIOException;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("JsonYaml")
class JsonYamlTest {

  @TempDir Path tempDir;

  @Test
  @DisplayName("Given JSON content, when it is parsed, then the tree keeps the JSON values")
  void parsesJson() throws IOException {
    JsonNode root = JsonYaml.parse("{\"openapi\": \"3.0.0\", \"paths\": {}}");

    assertThat(root.path("openapi").asText()).isEqualTo("3.0.0");
    assertThat(root.path("paths").isObject()).isTrue();
  }

  @Test
  @DisplayName(
      "Given YAML content, when it is parsed, then the JSON attempt fails and YAML takes over")
  void fallsBackToYaml() throws IOException {
    JsonNode root = JsonYaml.parse("paths:\n  /users:\n    get:\n      summary: list\n");

    assertThat(root.path("paths").path("/users").path("get").path("summary").asText())
        .isEqualTo("list");
  }

  @Test
  @DisplayName(
      "Given a YAML document with aliases and merge keys, when it is parsed, then they are expanded")
  void expandsAnchorsAndMergeKeys() throws IOException {
    String content =
        """
        defaults: &defaults
          method: GET
          host: api.example.com
        request:
          <<: *defaults
          path: /users
        """;

    JsonNode root = JsonYaml.parse(content);

    assertThat(root.path("request").path("method").asText()).isEqualTo("GET");
    assertThat(root.path("request").path("host").asText()).isEqualTo("api.example.com");
    assertThat(root.path("request").path("path").asText()).isEqualTo("/users");
  }

  @Test
  @DisplayName("Given an empty document, when it is parsed, then a missing node is returned")
  void returnsMissingNodeForEmptyDocument() throws IOException {
    assertThat(JsonYaml.parse("---\n").isMissingNode()).isTrue();
  }

  @Test
  @DisplayName(
      "Given content that is neither JSON nor YAML, when it is parsed, then an IOException is thrown")
  void failsOnUnparseableContent() {
    assertThatIOException().isThrownBy(() -> JsonYaml.parse("key: [unclosed"));
  }

  @Test
  @DisplayName("Given a YAML file on disk, when it is read, then the YAML fallback is used")
  void readsYamlFile() throws IOException {
    Path file = tempDir.resolve("collection.yaml");
    Files.writeString(file, "info:\n  name: Users API\n");

    assertThat(JsonYaml.readTree(file.toFile()).path("info").path("name").asText())
        .isEqualTo("Users API");
  }

  @Test
  @DisplayName("Given a missing file, when it is read, then an IOException is thrown")
  void failsOnMissingFile() {
    assertThatIOException()
        .isThrownBy(() -> JsonYaml.readTree(tempDir.resolve("missing.json").toFile()));
  }
}
