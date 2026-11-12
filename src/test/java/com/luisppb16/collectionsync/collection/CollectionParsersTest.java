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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("CollectionParsers")
class CollectionParsersTest {

  @TempDir Path tempDir;

  private static String fixtureContent(String name) throws IOException, URISyntaxException {
    URL url = CollectionParsersTest.class.getResource("/collections/" + name);
    assertThat(url).as("Fixture %s must exist", name).isNotNull();
    return Files.readString(Path.of(url.toURI()));
  }

  @Test
  @DisplayName(
      "Given the Postman fixture content, when the format is detected, then POSTMAN is returned")
  void detectsPostmanFormatByContent() throws IOException, URISyntaxException {
    String content = fixtureContent("postman-v21-sample.json");

    CollectionParsers.Format format = CollectionParsers.detectFormat(content);

    assertThat(format).isEqualTo(CollectionParsers.Format.POSTMAN);
  }

  @Test
  @DisplayName(
      "Given the Insomnia v4 fixture content, when the format is detected, then INSOMNIA is returned")
  void detectsInsomniaV4FormatByContent() throws IOException, URISyntaxException {
    String content = fixtureContent("insomnia-v4-sample.yaml");

    CollectionParsers.Format format = CollectionParsers.detectFormat(content);

    assertThat(format).isEqualTo(CollectionParsers.Format.INSOMNIA);
  }

  @Test
  @DisplayName(
      "Given the Insomnia v5 fixture content, when the format is detected, then INSOMNIA is returned")
  void detectsInsomniaV5FormatByContent() throws IOException, URISyntaxException {
    String content = fixtureContent("insomnia-v5-sample.yaml");

    CollectionParsers.Format format = CollectionParsers.detectFormat(content);

    assertThat(format).isEqualTo(CollectionParsers.Format.INSOMNIA);
  }

  @Test
  @DisplayName(
      "Given content with neither a Postman nor an Insomnia shape, when the format is detected, then it fails fast")
  void failsFastOnUnsupportedShape() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> CollectionParsers.detectFormat("{\"foo\": 1}"));
  }

  @Test
  @DisplayName("Given empty content, when the format is detected, then it fails")
  void failsOnEmptyContent() {
    assertThatThrownBy(() -> CollectionParsers.detectFormat(""))
        .isInstanceOfAny(IOException.class, IllegalArgumentException.class);
  }

  @Test
  @DisplayName(
      "Given content that is neither valid JSON nor valid YAML, when the format is detected, then an IOException is thrown")
  void throwsIOExceptionOnInvalidContent() {
    assertThatThrownBy(() -> CollectionParsers.detectFormat("{invalid: ["))
        .isInstanceOf(IOException.class);
  }

  @Test
  @DisplayName(
      "Given YAML content with a .json extension, when the one-shot parse is used, then it is parsed as Insomnia")
  void parsesYamlContentWithJsonExtension() throws IOException, URISyntaxException {
    String content = fixtureContent("insomnia-v4-sample.yaml");
    Path path = tempDir.resolve("insomnia.json");
    Files.writeString(path, content);

    List<ApiRequest> requests = CollectionParsers.parse(path.toFile());

    assertThat(requests).hasSize(2);
    assertThat(requests.getFirst().collectionName()).isEqualTo("Users API Workspace");
  }

  @Test
  @DisplayName(
      "Given an Insomnia v5 API spec document, when the format is detected, then it fails fast")
  void failsFastOnInsomniaV5SpecShape() {
    String content = "type: \"spec.insomnia.rest/5.0\"\nname: \"An API spec\"\n";

    assertThatIllegalArgumentException().isThrownBy(() -> CollectionParsers.detectFormat(content));
  }

  @ParameterizedTest(name = "\"{0}\" -> \"{1}\"")
  @CsvSource({
    "collection.json, collection",
    "collection, collection",
    "a.b.c, a.b",
    ".hidden, .hidden"
  })
  @DisplayName(
      "Given a file name, when the base name is computed, then the last extension is stripped")
  void computesFileBaseName(String fileName, String expected) {
    assertThat(CollectionParsers.fileBaseName(fileName)).isEqualTo(expected);
  }

  @Test
  @DisplayName(
      "Given the Postman fixture file, when the one-shot parse is used, then the requests are collected")
  void parsesPostmanFixtureEndToEnd() throws IOException, URISyntaxException {
    URL url = CollectionParsersTest.class.getResource("/collections/postman-v21-sample.json");
    assertThat(url).isNotNull();
    File file = Path.of(url.toURI()).toFile();

    List<ApiRequest> requests = CollectionParsers.parse(file);

    assertThat(requests).hasSize(4);
    assertThat(requests.getFirst().collectionName()).isEqualTo("Users API");
  }

  @Test
  @DisplayName(
      "Given a Postman .json file, when the one-shot parse is used, then its requests are parsed")
  void parsesPostmanCollectionFile() throws IOException {
    Path path = tempDir.resolve("collection.json");
    Files.writeString(
        path,
        """
        {"info": {"name": "X"}, "item": [
          {"name": "Ping", "request": {"method": "GET", "url": "/ping"}}
        ]}\
        """);

    List<ApiRequest> requests = CollectionParsers.parse(path.toFile());

    assertThat(requests).hasSize(1);
    assertThat(requests.getFirst().name()).isEqualTo("Ping");
  }

  @Test
  @DisplayName(
      "Given an Insomnia .yaml file, when the one-shot parse is used, then its requests are parsed")
  void parsesInsomniaFile() throws IOException {
    Path path = tempDir.resolve("collection.yaml");
    Files.writeString(
        path, "resources:\n  - _type: request\n    name: Ping\n    method: GET\n    url: /ping\n");

    List<ApiRequest> requests = CollectionParsers.parse(path.toFile());

    assertThat(requests).hasSize(1);
    assertThat(requests.getFirst().collectionName()).isEqualTo("collection");
  }

  @Test
  @DisplayName(
      "Given the Insomnia v5 fixture file, when the one-shot parse is used, then the requests are collected")
  void parsesInsomniaV5FixtureEndToEnd() throws IOException, URISyntaxException {
    URL url = CollectionParsersTest.class.getResource("/collections/insomnia-v5-sample.yaml");
    assertThat(url).isNotNull();
    File file = Path.of(url.toURI()).toFile();

    List<ApiRequest> requests = CollectionParsers.parse(file);

    assertThat(requests).hasSize(3);
    assertThat(requests.getFirst().name()).isEqualTo("List users");
    assertThat(requests.getFirst().collectionName()).isEqualTo("Users API v5");
  }

  @Test
  @DisplayName("Given an unsupported file, when the one-shot parse is used, then it fails fast")
  void failsFastOnUnsupportedFile() throws IOException {
    Path path = tempDir.resolve("collection.json");
    Files.writeString(path, "{\"foo\": 1}");

    assertThatIllegalArgumentException().isThrownBy(() -> CollectionParsers.parse(path.toFile()));
  }

  @Test
  @DisplayName(
      "Given the Insomnia v5 fixture file, when the one-shot parse is used, then the requests are collected in a single read")
  void parsesFileInSingleRead() throws IOException, URISyntaxException {
    URL url = CollectionParsersTest.class.getResource("/collections/insomnia-v5-sample.yaml");
    assertThat(url).isNotNull();
    File file = Path.of(url.toURI()).toFile();

    List<ApiRequest> requests = CollectionParsers.parse(file);

    assertThat(requests).hasSize(3);
    assertThat(requests.getFirst().name()).isEqualTo("List users");
    assertThat(requests.getFirst().collectionName()).isEqualTo("Users API v5");
  }

  @Test
  @DisplayName(
      "Given an OpenAPI 3.x document, when it is used as a collection, then every operation becomes a request")
  void parsesOpenApiDocumentAsCollection() throws IOException {
    Path path = tempDir.resolve("openapi.json");
    Files.writeString(
        path,
        """
        {"openapi": "3.0.0", "info": {"title": "OpenAPI API", "version": "1.0"},
         "paths": {
           "/users": {"get": {"operationId": "listUsers"}, "post": {"summary": "Create user"}},
           "/health": {"trace": {}}}}\
        """);

    List<ApiRequest> requests = CollectionParsers.parse(path.toFile());

    assertThat(requests)
        .extracting(ApiRequest::name, ApiRequest::rawUrl, ApiRequest::method)
        .containsExactly(
            tuple("listUsers", "/users", HttpMethod.GET),
            tuple("Create user", "/users", HttpMethod.POST),
            tuple("TRACE /health", "/health", HttpMethod.TRACE));
    assertThat(requests)
        .allSatisfy(request -> assertThat(request.collectionName()).isEqualTo("OpenAPI API"));
    assertThat(CollectionParsers.detectFormat(Files.readString(path)))
        .isEqualTo(CollectionParsers.Format.OPEN_API);
  }

  @Test
  @DisplayName(
      "Given a Postman collection with variables, when it is parsed, then known variables are resolved")
  void resolvesPostmanCollectionVariables() throws IOException {
    Path path = tempDir.resolve("postman-vars.json");
    Files.writeString(
        path,
        """
        {"info": {"name": "Vars"}, "variable": [
           {"key": "baseUrl", "value": "https://api.example.com"},
           {"key": "version", "value": "v1"}],
         "item": [
           {"name": "Ping", "request": {"method": "GET", "url": "{{baseUrl}}/{{version}}/ping"}},
           {"name": "Unknown", "request": {"method": "GET", "url": "{{baseUrl}}/{{response.body.id}}"}}
         ]}\
        """);

    List<ApiRequest> requests = CollectionParsers.parse(path.toFile());

    assertThat(requests)
        .extracting(ApiRequest::name, ApiRequest::rawUrl)
        .containsExactly(
            tuple("Ping", "https://api.example.com/v1/ping"),
            tuple("Unknown", "https://api.example.com/{{response.body.id}}"));
  }

  @Test
  @DisplayName(
      "Given Insomnia environments with one or several values, when parsed, then only unique variables are resolved")
  void resolvesOnlyUnambiguousInsomniaEnvironmentVariables() throws IOException {
    Path unique = tempDir.resolve("insomnia-unique.yaml");
    Files.writeString(
        unique,
        """
        resources:
          - _type: environment
            data: {base_url: "https://api.example.com"}
          - _type: request
            name: Ping
            method: GET
            url: "{{ _.base_url }}/ping"
        """);
    Path ambiguous = tempDir.resolve("insomnia-ambiguous.yaml");
    Files.writeString(
        ambiguous,
        """
        resources:
          - _type: environment
            data: {base_url: "https://a.example.com"}
          - _type: environment
            data: {base_url: "https://b.example.com"}
          - _type: request
            name: Ping
            method: GET
            url: "{{ _.base_url }}/ping"
        """);

    assertThat(CollectionParsers.parse(unique.toFile()))
        .extracting(ApiRequest::rawUrl)
        .containsExactly("https://api.example.com/ping");
    assertThat(CollectionParsers.parse(ambiguous.toFile()))
        .extracting(ApiRequest::rawUrl)
        .containsExactly("{{ _.base_url }}/ping");
  }

  @Test
  @DisplayName("Given a null file, when the one-shot parse is used, then it fails fast")
  void parseFailsFastOnNull() {
    assertThatIllegalArgumentException().isThrownBy(() -> CollectionParsers.parse(null));
  }
}
