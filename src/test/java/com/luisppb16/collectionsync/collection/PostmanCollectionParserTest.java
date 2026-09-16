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

@DisplayName("PostmanCollectionParser")
class PostmanCollectionParserTest {

  private static final PostmanCollectionParser PARSER = new PostmanCollectionParser();

  @TempDir Path tempDir;

  private static File fixture(String name) throws URISyntaxException {
    URL url = PostmanCollectionParserTest.class.getResource("/collections/" + name);
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
      "Given the Postman v2.1 fixture, when it is parsed, then requests inside folders and at top level are collected")
  void parsesFixtureRequestsAcrossFolders() throws IOException, URISyntaxException {
    File file = fixture("postman-v21-sample.json");

    List<ApiRequest> requests = PARSER.parse(file);

    assertThat(requests)
        .extracting(
            ApiRequest::name, ApiRequest::rawUrl, ApiRequest::method, ApiRequest::collectionName)
        .containsExactly(
            tuple("List users", "{{baseUrl}}/users?include=roles", HttpMethod.GET, "Users API"),
            tuple("Get user", "{{baseUrl}}/users/{{id}}", HttpMethod.GET, "Users API"),
            tuple("Create user", "{{baseUrl}}/users", HttpMethod.POST, "Users API"),
            tuple("Ping", "{{baseUrl}}/ping", HttpMethod.GET, "Users API"));
  }

  @Test
  @DisplayName(
      "Given a collection with an empty folder and no requests, when it is parsed, then an empty list is returned")
  void returnsEmptyListForCollectionWithoutRequests() throws IOException {
    File file =
        writeFile(
            "empty.json",
            """
                {"info": {"name": "Empty"}, "item": [{"name": "Empty folder", "item": []}]}""");

    List<ApiRequest> requests = PARSER.parse(file);

    assertThat(requests).isEmpty();
  }

  @Test
  @DisplayName(
      "Given items with neither request nor nested items, when it is parsed, then they are ignored")
  void ignoresItemsWithoutRequestOrNestedItems() throws IOException {
    File file =
        writeFile(
            "ignored.json",
            """
                {"info": {"name": "Mixed"}, "item": [
                  {"name": "just a label"},
                  {"name": "Ping", "request": {"method": "GET", "url": "/ping"}}
                ]}""");

    List<ApiRequest> requests = PARSER.parse(file);

    assertThat(requests).hasSize(1);
    assertThat(requests.getFirst().name()).isEqualTo("Ping");
  }

  @Test
  @DisplayName(
      "Given a request whose url object has no raw field, when it is parsed, then the request is skipped")
  void skipsRequestWithUrlObjectWithoutRaw() throws IOException {
    File file =
        writeFile(
            "no-raw.json",
            """
                {"info": {"name": "X"}, "item": [
                  {"name": "No raw", "request": {"method": "GET", "url": {"path": ["users"]}}}
                ]}""");

    assertThat(PARSER.parse(file)).isEmpty();
  }

  @Test
  @DisplayName(
      "Given requests without method or with blank url, when it is parsed, then they are skipped")
  void skipsRequestsWithoutUsableMethodOrUrl() throws IOException {
    File file =
        writeFile(
            "no-method.json",
            """
                {"info": {"name": "X"}, "item": [
                  {"name": "No method", "request": {"url": "/ping"}},
                  {"name": "Blank url", "request": {"method": "GET", "url": "   "}}
                ]}""");

    assertThat(PARSER.parse(file)).isEmpty();
  }

  @Test
  @DisplayName(
      "Given a collection without info.name, when it is parsed, then the file base name is used as collection name")
  void fallsBackToFileBaseNameWhenInfoNameIsMissing() throws IOException {
    File file =
        writeFile(
            "fallback-collection.json",
            """
                {"info": {"_postman_id": "abc"}, "item": [
                  {"name": "Ping", "request": {"method": "GET", "url": "/ping"}}
                ]}""");

    List<ApiRequest> requests = PARSER.parse(file);

    assertThat(requests).hasSize(1);
    assertThat(requests.getFirst().collectionName()).isEqualTo("fallback-collection");
  }

  @ParameterizedTest(name = "method \"{0}\" is parsed as {1}")
  @CsvSource({"GET, GET", "post, POST", "delete, DELETE"})
  @DisplayName(
      "Given a request with any method casing, when it is parsed, then the method is normalized")
  void normalizesMethodCasing(String rawMethod, HttpMethod expected) throws IOException {
    File file =
        writeFile(
            "method.json",
            """
                {"info": {"name": "X"}, "item": [
                  {"name": "Ping", "request": {"method": "%s", "url": "/ping"}}
                ]}"""
                .formatted(rawMethod));

    List<ApiRequest> requests = PARSER.parse(file);

    assertThat(requests).hasSize(1);
    assertThat(requests.getFirst().method()).isEqualTo(expected);
  }

  @Test
  @DisplayName("Given a request with an unsupported method, when it is parsed, then it fails fast")
  void failsFastOnUnsupportedMethod() throws IOException {
    File file =
        writeFile(
            "bad-method.json",
            """
                {"info": {"name": "X"}, "item": [
                  {"request": {"method": "FOO", "url": "/x"}}
                ]}""");

    assertThatIllegalArgumentException().isThrownBy(() -> PARSER.parse(file));
  }

  @Test
  @DisplayName(
      "Given a file whose root has neither info nor item, when it is parsed, then it fails fast")
  void failsFastOnFileWithoutInfoOrItem() throws IOException {
    File file = writeFile("not-postman.json", "{\"resources\": []}");

    assertThatIllegalArgumentException().isThrownBy(() -> PARSER.parse(file));
  }

  @Test
  @DisplayName("Given an array root, when it is parsed, then it fails fast")
  void failsFastOnArrayRoot() throws IOException {
    File file = writeFile("array.json", "[]");

    assertThatIllegalArgumentException().isThrownBy(() -> PARSER.parse(file));
  }

  @Test
  @DisplayName("Given an empty file, when it is parsed, then it fails")
  void failsOnEmptyFile() throws IOException {
    File file = writeFile("blank.json", "");

    assertThatThrownBy(() -> PARSER.parse(file))
        .isInstanceOfAny(IOException.class, IllegalArgumentException.class);
  }

  @Test
  @DisplayName("Given a missing file, when it is parsed, then an IOException is thrown")
  void throwsIOExceptionOnMissingFile() {
    File file = tempDir.resolve("missing.json").toFile();

    assertThatThrownBy(() -> PARSER.parse(file)).isInstanceOf(IOException.class);
  }

  @Test
  @DisplayName("Given a null file, when it is parsed, then it fails fast")
  void failsFastOnNullFile() {
    assertThatIllegalArgumentException().isThrownBy(() -> PARSER.parse(null));
  }
}
