/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */

package com.luisppb16.collectionsync.actions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIOException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@DisplayName("ActionFiles")
class ActionFilesTest {

    @TempDir
    Path tempDir;

    // ------------------------------------------------------------------ isSupportedFile

    @ParameterizedTest
    @ValueSource(strings = {"collection.json", "collection.yaml", "collection.yml", "COLLECTION.JSON",
            "open-api.Yml", "api.spec.yaml"})
    @DisplayName("Given a file name with a supported extension, when checked, then it is accepted")
    void acceptsSupportedExtension(String fileName) {
        assertThat(ActionFiles.isSupportedFile(fileName)).isTrue();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "collection", "archive.zip", "notes.json.txt", ".gitignore",
            "collection.postman_collection"})
    @DisplayName("Given a name with no extension, another extension or null, when checked, then it is rejected")
    void rejectsUnsupportedNames(String fileName) {
        assertThat(ActionFiles.isSupportedFile(fileName)).isFalse();
    }

    // ------------------------------------------------------------------ isOpenApiDocument

    @Test
    @DisplayName("Given an object with paths, when checked, then it is an OpenAPI document")
    void acceptsObjectWithPaths() {
        assertThat(ActionFiles.isOpenApiDocument(parse("{\"paths\": {\"/users\": {\"get\": {}}}}"))).isTrue();
    }

    @Test
    @DisplayName("Given an object without paths, when checked, then it is not an OpenAPI document")
    void rejectsObjectWithoutPaths() {
        assertThat(ActionFiles.isOpenApiDocument(parse("{\"openapi\": \"3.0.0\"}"))).isFalse();
    }

    @Test
    @DisplayName("Given a scalar or an array root, when checked, then it is not an OpenAPI document")
    void rejectsNonObjectRoots() {
        assertThat(ActionFiles.isOpenApiDocument(parse("\"a scalar\""))).isFalse();
        assertThat(ActionFiles.isOpenApiDocument(parse("[1, 2]"))).isFalse();
    }

    // ------------------------------------------------------------------ readOpenApiTree

    @Test
    @DisplayName("Given a JSON document, when read, then the root object is returned")
    void readsJsonDocument() throws IOException {
        Path file = write("openapi.json", "{\"paths\": {\"/users\": {\"get\": {}}}}");

        JsonNode root = ActionFiles.readOpenApiTree(file.toFile());

        assertThat(ActionFiles.isOpenApiDocument(root)).isTrue();
    }

    @Test
    @DisplayName("Given a YAML document, when read, then the root object is returned")
    void readsYamlDocument() throws IOException {
        Path file = write("openapi.yaml", "paths:\n  /users:\n    get:\n      summary: list\n");

        JsonNode root = ActionFiles.readOpenApiTree(file.toFile());

        assertThat(ActionFiles.isOpenApiDocument(root)).isTrue();
    }

    @Test
    @DisplayName("Given YAML content in a .json file, when read, then the JSON parser fails and the YAML fallback parses it")
    void fallsBackToYamlWhenJsonParsingFails() throws IOException {
        Path file = write("openapi.json", "paths:\n  /users:\n    get: {}\n");

        JsonNode root = ActionFiles.readOpenApiTree(file.toFile());

        assertThat(ActionFiles.isOpenApiDocument(root)).isTrue();
    }

    @Test
    @DisplayName("Given an unreadable file, when read, then an IOException is thrown")
    void failsOnUnreadableFile() {
        assertThatIOException()
                .isThrownBy(() -> ActionFiles.readOpenApiTree(tempDir.toFile()));
    }

    private JsonNode parse(String content) {
        try {
            return new ObjectMapper().readTree(content);
        } catch (IOException neverHappensOnInMemoryContent) {
            throw new AssertionError(neverHappensOnInMemoryContent);
        }
    }

    private Path write(String fileName, String content) throws IOException {
        Path path = tempDir.resolve(fileName);
        Files.writeString(path, content);
        return path;
    }
}