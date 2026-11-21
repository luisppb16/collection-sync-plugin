/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */

package com.luisppb16.collectionsync.io;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("OpenApiDocument")
class OpenApiDocumentTest {

  private static JsonNode parse(String content) throws IOException {
    return new ObjectMapper().readTree(content);
  }

  @Test
  @DisplayName(
      "Given a document with paths, when operations are read, then only HTTP verbs are returned")
  void readsOnlyOperations() throws IOException {
    JsonNode root =
        parse(
            """
            {"paths": {
              "/users": {
                "get": {"operationId": "listUsers"},
                "post": {"operationId": "createUser"},
                "parameters": [],
                "summary": "ignored"
              },
              "/health": {"get": {}}
            }}
            """);

    assertThat(OpenApiDocument.operations(root))
        .extracting(OpenApiDocument.Operation::path, OpenApiDocument.Operation::methodKey)
        .containsExactly(tuple("/users", "get"), tuple("/users", "post"), tuple("/health", "get"));
  }

  @Test
  @DisplayName("Given a document without paths, when operations are read, then the stream is empty")
  void readsNoOperationsWithoutPaths() throws IOException {
    assertThat(OpenApiDocument.operations(parse("{\"openapi\": \"3.0.0\"}"))).isEmpty();
  }

  @Test
  @DisplayName(
      "Given an object with openapi and paths, when it is checked, then it is an OpenAPI document")
  void detectsOpenApiDocuments() throws IOException {
    assertThat(OpenApiDocument.isOpenApi(parse("{\"openapi\": \"3.0.0\", \"paths\": {}}")))
        .isTrue();
  }

  @Test
  @DisplayName(
      "Given null, non-object or incomplete roots, when they are checked, then they are rejected")
  void rejectsIncompleteDocuments() throws IOException {
    assertThat(OpenApiDocument.isOpenApi(null)).isFalse();
    assertThat(OpenApiDocument.isOpenApi(parse("[1, 2]"))).isFalse();
    assertThat(OpenApiDocument.isOpenApi(parse("{\"paths\": {}}"))).isFalse();
    assertThat(OpenApiDocument.isOpenApi(parse("{\"openapi\": \"3.0.0\"}"))).isFalse();
  }
}
