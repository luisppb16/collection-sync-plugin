/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */

package com.luisppb16.collectionsync.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import com.intellij.openapi.project.Project;

import com.luisppb16.collectionsync.domain.model.ApiEndpoint;
import com.luisppb16.collectionsync.domain.model.HttpMethod;

@DisplayName("OpenApiEndpointSource")
class OpenApiEndpointSourceTest {

    private static final String JSON_SPEC = """
            {
              "openapi": "3.0.3",
              "info": {"title": "Demo API", "version": "1.0.0"},
              "paths": {
                "/users": {
                  "summary": "Users collection",
                  "parameters": [{"name": "q", "in": "query", "schema": {"type": "string"}}],
                  "get": {"operationId": "listUsers", "responses": {"200": {"description": "ok"}}},
                  "post": {"operationId": "createUser", "responses": {"201": {"description": "created"}}}
                },
                "/users/{id}": {
                  "description": "Single user",
                  "servers": [{"url": "https://api.example.com"}],
                  "$ref": "#/components/pathItems/UserItem",
                  "get": {"operationId": "getUser", "responses": {"200": {"description": "ok"}}},
                  "delete": {"operationId": "deleteUser", "responses": {"204": {"description": "deleted"}}},
                  "patch": {"operationId": "patchUser", "responses": {"200": {"description": "ok"}}}
                },
                "/metrics": {
                  "summary": "Only shared data, no operation",
                  "parameters": [{"name": "range", "in": "query"}]
                }
              }
            }
            """;

    private static final String YAML_SPEC = """
            openapi: 3.1.0
            info:
              title: Demo API
              version: 1.0.0
            paths:
              /health:
                get:
                  operationId: health
                head:
                  operationId: healthHead
              /users/{id}:
                put:
                  operationId: updateUser
                options:
                  operationId: userOptions
            """;

    private final Project project = mock(Project.class);

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Given an OpenAPI 3.x JSON document, when it is collected, then every operation becomes an endpoint")
    void extractsEndpointsFromJsonDocument() throws IOException {
        Path specFile = write("openapi.json", JSON_SPEC);
        OpenApiEndpointSource source = new OpenApiEndpointSource(specFile.toString());

        List<ApiEndpoint> endpoints = source.collect(project);

        assertThat(endpoints).extracting(ApiEndpoint::method, ApiEndpoint::pathTemplate).containsExactlyInAnyOrder(
                tuple(HttpMethod.GET, "/users"),
                tuple(HttpMethod.POST, "/users"),
                tuple(HttpMethod.GET, "/users/{id}"),
                tuple(HttpMethod.DELETE, "/users/{id}"),
                tuple(HttpMethod.PATCH, "/users/{id}"));
        assertThat(endpoints).allSatisfy(endpoint -> {
            assertThat(endpoint.ownerClass()).isEqualTo("<openapi> openapi.json");
            assertThat(endpoint.moduleName()).isEmpty();
            assertThat(endpoint.psiMethod()).isNull();
        });
    }

    @Test
    @DisplayName("Given an OpenAPI 3.x YAML document, when it is collected, then every operation becomes an endpoint")
    void extractsEndpointsFromYamlDocument() throws IOException {
        Path specFile = write("openapi.yaml", YAML_SPEC);
        OpenApiEndpointSource source = new OpenApiEndpointSource(specFile.toString());

        List<ApiEndpoint> endpoints = source.collect(project);

        assertThat(endpoints).extracting(ApiEndpoint::method, ApiEndpoint::pathTemplate).containsExactlyInAnyOrder(
                tuple(HttpMethod.GET, "/health"),
                tuple(HttpMethod.HEAD, "/health"),
                tuple(HttpMethod.PUT, "/users/{id}"),
                tuple(HttpMethod.OPTIONS, "/users/{id}"));
    }

    @ParameterizedTest(name = "extension-less file written as {0}")
    @ValueSource(strings = {"json", "yaml"})
    @DisplayName("Given a file with an unknown extension, when it is collected, then the content is detected as JSON or YAML")
    void detectsFormatByContentWhenExtensionIsUnknown(String format) throws IOException {
        Path specFile = write("spec.txt", "json".equals(format) ? JSON_SPEC : YAML_SPEC);
        OpenApiEndpointSource source = new OpenApiEndpointSource(specFile.toString());

        List<ApiEndpoint> endpoints = source.collect(project);

        assertThat(endpoints).isNotEmpty();
        assertThat(endpoints.getFirst().ownerClass()).isEqualTo("<openapi> spec.txt");
    }

    @Test
    @DisplayName("Given a path that does not exist, when it is collected, then no endpoint is produced")
    void returnsEmptyWhenFileDoesNotExist() {
        OpenApiEndpointSource source =
                new OpenApiEndpointSource(tempDir.resolve("missing-openapi.json").toString());

        List<ApiEndpoint> endpoints = source.collect(project);

        assertThat(endpoints).isEmpty();
    }

    @ParameterizedTest(name = "path [{0}] yields no endpoints")
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("Given a null, empty or blank configured path, when it is collected, then no endpoint is produced")
    void returnsEmptyWhenPathIsNullOrBlank(String configuredPath) {
        OpenApiEndpointSource source = new OpenApiEndpointSource(configuredPath);

        List<ApiEndpoint> endpoints = source.collect(project);

        assertThat(endpoints).isEmpty();
    }

    @Test
    @DisplayName("Given an unparseable document, when it is collected, then no endpoint is produced")
    void returnsEmptyWhenDocumentIsUnparseable() throws IOException {
        Path specFile = write("broken.json", "{ this is not a document ]");
        OpenApiEndpointSource source = new OpenApiEndpointSource(specFile.toString());

        List<ApiEndpoint> endpoints = source.collect(project);

        assertThat(endpoints).isEmpty();
    }

    private Path write(String fileName, String content) throws IOException {
        Path file = tempDir.resolve(fileName);
        Files.writeString(file, content);
        return file;
    }
}