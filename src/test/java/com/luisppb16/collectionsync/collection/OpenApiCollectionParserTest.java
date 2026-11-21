/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */

package com.luisppb16.collectionsync.collection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;

import com.luisppb16.collectionsync.domain.model.ApiRequest;
import com.luisppb16.collectionsync.domain.model.HttpMethod;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("OpenApiCollectionParser")
class OpenApiCollectionParserTest {

  private static final OpenApiCollectionParser PARSER = new OpenApiCollectionParser();

  @TempDir Path tempDir;

  @Test
  @DisplayName(
      "Given an OpenAPI JSON document, when it is parsed, then every operation becomes a request")
  void parsesJsonOperations() throws IOException {
    Path file =
        write(
            "openapi.json",
            """
            {"openapi": "3.0.0",
             "info": {"title": "Carro API", "version": "1"},
             "paths": {
               "/carro": {"get": {"operationId": "getCarro"}},
               "/users/{id}": {"put": {"summary": "Update user"}},
               "/reports": {"delete": {}}
             }}
            """);

    List<ApiRequest> requests = PARSER.parse(file.toFile());

    assertThat(requests)
        .extracting(
            ApiRequest::method, ApiRequest::rawUrl, ApiRequest::name, ApiRequest::collectionName)
        .containsExactly(
            tuple(HttpMethod.GET, "/carro", "getCarro", "Carro API"),
            tuple(HttpMethod.PUT, "/users/{id}", "Update user", "Carro API"),
            tuple(HttpMethod.DELETE, "/reports", "DELETE /reports", "Carro API"));
  }

  @Test
  @DisplayName(
      "Given an OpenAPI YAML document without a title, when it is parsed, then the file base name is the collection")
  void parsesYamlAndFallsBackToFileBaseName() throws IOException {
    Path file =
        write(
            "carro-api.yaml",
            """
            openapi: 3.0.0
            paths:
              /carro:
                get:
                  operationId: getCarro
            """);

    assertThat(PARSER.parse(file.toFile()))
        .singleElement()
        .satisfies(request -> {
          assertThat(request.method()).isEqualTo(HttpMethod.GET);
          assertThat(request.collectionName()).isEqualTo("carro-api");
        });
  }

  @Test
  @DisplayName("Given a document without paths, when it is parsed, then the request list is empty")
  void parsesEmptyDocument() throws IOException {
    Path file = write("empty.json", "{\"openapi\": \"3.0.0\", \"paths\": {}}");

    assertThat(PARSER.parse(file.toFile())).isEmpty();
  }

  @Test
  @DisplayName("Given a document that is not OpenAPI, when it is parsed, then it fails fast")
  void rejectsNonOpenApiDocument() throws IOException {
    Path file = write("postman.json", "{\"info\": {\"name\": \"Users\"}, \"item\": []}");

    assertThatIllegalArgumentException().isThrownBy(() -> PARSER.parse(file.toFile()));
  }

  private Path write(String fileName, String content) throws IOException {
    Path path = tempDir.resolve(fileName);
    Files.writeString(path, content);
    return path;
  }
}
