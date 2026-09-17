/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */

package com.luisppb16.collectionsync.collection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.luisppb16.collectionsync.domain.model.ApiRequest;
import com.luisppb16.collectionsync.domain.model.HttpMethod;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("InsomniaCollectionParser")
class InsomniaCollectionParserTest {

  private static final InsomniaCollectionParser PARSER = new InsomniaCollectionParser();

  @TempDir Path tempDir;

  private static File fixture(String name) throws URISyntaxException {
    URL url = InsomniaCollectionParserTest.class.getResource("/collections/" + name);
    assertThat(url).as("Fixture %s must exist", name).isNotNull();
    return Path.of(url.toURI()).toFile();
  }

  private File writeFile(String fileName, String content) throws IOException {
    Path path = tempDir.resolve(fileName);
    Files.writeString(path, content);
    return path.toFile();
  }

  @Test
  @DisplayName(
      "Given the Insomnia v4 fixture, when it is parsed, then only request resources are collected")
  void parsesFixtureRequestsIgnoringOtherResourceTypes() throws IOException, URISyntaxException {
    File file = fixture("insomnia-v4-sample.yaml");

    List<ApiRequest> requests = PARSER.parse(file);

    assertThat(requests)
        .extracting(
            ApiRequest::name, ApiRequest::rawUrl, ApiRequest::method, ApiRequest::collectionName)
        .containsExactly(
            tuple(
                "List users",
                "{{ _.base_url }}/users?include=roles",
                HttpMethod.GET,
                "Users API Workspace"),
            tuple("Create user", "{{ _.base_url }}/users", HttpMethod.POST, "Users API Workspace"));
  }

  @Test
  @DisplayName(
      "Given a bare resource array without workspace, when it is parsed, then requests are collected and the file base name is used")
  void parsesBareResourceArrayWithFileBaseNameFallback() throws IOException {
    File file =
        writeFile(
            "bare-array.yaml",
            """
                - _type: request
                  name: Ping
                  method: GET
                  url: /ping
                - _type: environment
                  name: dev
                  data:
                    base_url: https://api.example.com
                """);

    List<ApiRequest> requests = PARSER.parse(file);

    assertThat(requests).hasSize(1);
    assertThat(requests.getFirst().name()).isEqualTo("Ping");
    assertThat(requests.getFirst().collectionName()).isEqualTo("bare-array");
  }

  @Test
  @DisplayName(
      "Given an Insomnia export written as JSON, when it is parsed, then requests are collected with the workspace name")
  void parsesJsonExport() throws IOException {
    File file =
        writeFile(
            "insomnia.json",
            """
                {"resources": [
                  {"_type": "workspace", "name": "JSON Workspace"},
                  {"_type": "request", "name": "Ping", "method": "GET", "url": "/ping"}
                ]}""");

    List<ApiRequest> requests = PARSER.parse(file);

    assertThat(requests).hasSize(1);
    assertThat(requests.getFirst().collectionName()).isEqualTo("JSON Workspace");
  }

  @Test
  @DisplayName(
      "Given resources without _type, method or url, when it is parsed, then they are skipped")
  void skipsNonRequestAndIncompleteResources() throws IOException {
    File file =
        writeFile(
            "mixed.yaml",
            """
                resources:
                  - _type: unit_test
                    name: a test
                  - _id: r1
                    name: missing method
                    url: /ping
                  - _id: r2
                    _type: request
                    name: missing url
                    method: GET
                  - _id: r3
                    _type: request
                    name: Ping
                    method: GET
                    url: /ping
                """);

    List<ApiRequest> requests = PARSER.parse(file);

    assertThat(requests).hasSize(1);
    assertThat(requests.getFirst().name()).isEqualTo("Ping");
  }

  @Test
  @DisplayName(
      "Given a collection with a request_group but no workspace, when it is parsed, then the file base name is used")
  void fallsBackToFileBaseNameWhenWorkspaceIsMissing() throws IOException {
    File file =
        writeFile(
            "no-workspace.yaml",
            """
                resources:
                  - _type: request_group
                    name: Users
                  - _type: request
                    name: Ping
                    method: GET
                    url: /ping
                """);

    List<ApiRequest> requests = PARSER.parse(file);

    assertThat(requests).hasSize(1);
    assertThat(requests.getFirst().collectionName()).isEqualTo("no-workspace");
  }

  @Test
  @DisplayName(
      "Given an export with an empty resources array, when it is parsed, then an empty list is returned")
  void returnsEmptyListForEmptyResources() throws IOException {
    File file = writeFile("empty-resources.yaml", "resources: []");

    assertThat(PARSER.parse(file)).isEmpty();
  }

  @Test
  @DisplayName(
      "Given the Insomnia v5 fixture, when it is parsed, then nested and top-level requests are collected")
  void parsesV5FixtureRequestsIgnoringFoldersAndEnvironments()
      throws IOException, URISyntaxException {
    File file = fixture("insomnia-v5-sample.yaml");

    List<ApiRequest> requests = PARSER.parse(file);

    assertThat(requests)
        .extracting(
            ApiRequest::name, ApiRequest::rawUrl, ApiRequest::method, ApiRequest::collectionName)
        .containsExactly(
            tuple(
                "List users",
                "{{ _.base_url }}/users?include=roles",
                HttpMethod.GET,
                "Users API v5"),
            tuple("Create user", "{{ _.base_url }}/users", HttpMethod.POST, "Users API v5"),
            tuple("Delete user", "{{ _.base_url }}/users/17", HttpMethod.DELETE, "Users API v5"));
  }

  @Test
  @DisplayName(
      "Given a v5 collection with folders nested at any depth, when it is parsed, then requests are collected")
  void parsesV5NestedChildrenAtAnyDepth() throws IOException {
    File file =
        writeFile(
            "nested-v5.yaml",
            """
                type: "collection.insomnia.rest/5.0"
                name: "Deep Collection"
                collection:
                  - name: "Level 1"
                    children:
                      - name: "Level 2"
                        children:
                          - name: "Deep ping"
                            method: GET
                            url: /deep/ping
                """);

    List<ApiRequest> requests = PARSER.parse(file);

    assertThat(requests).hasSize(1);
    assertThat(requests.getFirst().name()).isEqualTo("Deep ping");
    assertThat(requests.getFirst().collectionName()).isEqualTo("Deep Collection");
  }

  @Test
  @DisplayName(
      "Given an Insomnia v5 collection written as JSON, when it is parsed, then requests are collected with the root name")
  void parsesV5JsonExport() throws IOException {
    File file =
        writeFile(
            "insomnia-v5.json",
            """
                {"type": "collection.insomnia.rest/5.0", "name": "JSON v5",
                 "collection": [{"name": "Ping", "method": "GET", "url": "/ping"}]}""");

    List<ApiRequest> requests = PARSER.parse(file);

    assertThat(requests).hasSize(1);
    assertThat(requests.getFirst().name()).isEqualTo("Ping");
    assertThat(requests.getFirst().collectionName()).isEqualTo("JSON v5");
  }

  @Test
  @DisplayName(
      "Given a v5 collection without a root name, when it is parsed, then the file base name is used")
  void fallsBackToFileBaseNameWhenV5NameIsMissing() throws IOException {
    File file =
        writeFile(
            "v5-unnamed.yaml",
            """
                type: "collection.insomnia.rest/5.0"
                collection:
                  - name: "Ping"
                    method: GET
                    url: /ping
                """);

    List<ApiRequest> requests = PARSER.parse(file);

    assertThat(requests).hasSize(1);
    assertThat(requests.getFirst().collectionName()).isEqualTo("v5-unnamed");
  }

  @Test
  @DisplayName(
      "Given a v5 collection with an empty collection array, when it is parsed, then an empty list is returned")
  void returnsEmptyListForEmptyV5Collection() throws IOException {
    File file =
        writeFile(
            "empty-v5.yaml",
            """
                type: "collection.insomnia.rest/5.0"
                name: "Empty"
                collection: []
                """);

    assertThat(PARSER.parse(file)).isEmpty();
  }

  @Test
  @DisplayName(
      "Given a v5 request with an unsupported method, when it is parsed, then it fails fast")
  void failsFastOnV5UnsupportedMethod() throws IOException {
    File file =
        writeFile(
            "bad-method-v5.yaml",
            """
                type: "collection.insomnia.rest/5.0"
                collection:
                  - name: "Bad"
                    method: FOO
                    url: /x
                """);

    assertThatIllegalArgumentException().isThrownBy(() -> PARSER.parse(file));
  }

  @Test
  @DisplayName(
      "Given a v5 collection without a 'collection' array, when it is parsed, then it fails fast")
  void failsFastOnV5WithoutCollectionArray() throws IOException {
    File file =
        writeFile(
            "no-collection.yaml",
            """
                type: "collection.insomnia.rest/5.0"
                name: "No tree"
                """);

    assertThatIllegalArgumentException().isThrownBy(() -> PARSER.parse(file));
  }

  @Test
  @DisplayName("Given an Insomnia v5 API spec document, when it is parsed, then it fails fast")
  void failsFastOnNonCollectionV5Namespace() throws IOException {
    File file =
        writeFile(
            "spec-v5.yaml",
            """
                type: "spec.insomnia.rest/5.0"
                name: "An API spec"
                """);

    assertThatIllegalArgumentException().isThrownBy(() -> PARSER.parse(file));
  }

  @Test
  @DisplayName(
      "Given a request resource with an unsupported method, when it is parsed, then it fails fast")
  void failsFastOnUnsupportedMethod() throws IOException {
    File file =
        writeFile(
            "bad-method.yaml",
            """
                resources:
                  - _type: request
                    method: FOO
                    url: /x
                """);

    assertThatIllegalArgumentException().isThrownBy(() -> PARSER.parse(file));
  }

  @Test
  @DisplayName("Given a file that is not an Insomnia export, when it is parsed, then it fails fast")
  void failsFastOnRootWithoutResources() throws IOException, URISyntaxException {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> PARSER.parse(fixture("postman-v21-sample.json")));
  }

  @Test
  @DisplayName("Given a missing file, when it is parsed, then an IOException is thrown")
  void throwsIOExceptionOnMissingFile() {
    File file = tempDir.resolve("missing.yaml").toFile();

    assertThatThrownBy(() -> PARSER.parse(file)).isInstanceOf(IOException.class);
  }

  @Test
  @DisplayName("Given a null file, when it is parsed, then it fails fast")
  void failsFastOnNullFile() {
    assertThatIllegalArgumentException().isThrownBy(() -> PARSER.parse(null));
  }
}
