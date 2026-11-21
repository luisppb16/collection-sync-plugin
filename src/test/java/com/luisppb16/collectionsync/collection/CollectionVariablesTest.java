/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */

package com.luisppb16.collectionsync.collection;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("CollectionVariables")
class CollectionVariablesTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static JsonNode json(String content) throws IOException {
    return MAPPER.readTree(content);
  }

  @Test
  @DisplayName(
      "Given a null, empty or variable-free value, when it is resolved, then it is returned unchanged")
  void returnsValuesWithoutVariables() {
    Map<String, String> variables = Map.of("baseUrl", "https://api.example.com");

    assertThat(CollectionVariables.resolve(null, variables)).isNull();
    assertThat(CollectionVariables.resolve("", variables)).isEmpty();
    assertThat(CollectionVariables.resolve("/users", variables)).isEqualTo("/users");
    assertThat(CollectionVariables.resolve("{{baseUrl}}/users", Map.of()))
        .isEqualTo("{{baseUrl}}/users");
  }

  @ParameterizedTest
  @CsvSource(
      value = {
        "'{{baseUrl}}/users', 'https://api.example.com/users'",
        "'{{ baseUrl }}/users', 'https://api.example.com/users'",
        "'{{_.baseUrl}}/users', 'https://api.example.com/users'",
        "'{{baseUrl}}/{{baseUrl}}', 'https://api.example.com/https://api.example.com'"
      },
      quoteCharacter = '\'')
  @DisplayName(
      "Given a value with a known variable, when it is resolved, then every occurrence is replaced")
  void resolvesKnownVariables(String value, String expected) {
    Map<String, String> variables = Map.of("baseUrl", "https://api.example.com");

    assertThat(CollectionVariables.resolve(value, variables)).isEqualTo(expected);
  }

  @Test
  @DisplayName(
      "Given a value with an unknown variable, when it is resolved, then the expression is kept")
  void keepsUnknownVariables() {
    assertThat(CollectionVariables.resolve("{{baseUrl}}/{{missing}}", Map.of("baseUrl", "host")))
        .isEqualTo("host/{{missing}}");
  }

  @Test
  @DisplayName(
      "Given a replacement with a dollar sign, when it is resolved, then it is inserted literally")
  void escapesReplacementCharacters() {
    assertThat(CollectionVariables.resolve("{{price}}", Map.of("price", "$100"))).isEqualTo("$100");
    assertThat(CollectionVariables.resolve("{{path}}", Map.of("path", "a\\b"))).isEqualTo("a\\b");
  }

  @Test
  @DisplayName(
      "Given a null, non-array or empty variable list, when merged, then the parent map is returned")
  void keepsParentWhenNothingToMerge() throws IOException {
    Map<String, String> parent = Map.of("baseUrl", "host");

    assertThat(CollectionVariables.merge(parent, null)).isSameAs(parent);
    assertThat(CollectionVariables.merge(parent, json("{\"key\": \"value\"}"))).isSameAs(parent);
    assertThat(CollectionVariables.merge(parent, json("[]"))).isSameAs(parent);
  }

  @Test
  @DisplayName(
      "Given a variable list, when merged, then text values win and invalid entries are skipped")
  void mergesValidVariables() throws IOException {
    JsonNode variables =
        json(
            """
            [
              {"key": " baseUrl ", "value": "https://api.example.com"},
              {"key": "token", "value": "abc"},
              {"key": "  ", "value": "blank-key"},
              {"key": "count", "value": 42},
              {"key": "empty", "value": ""}
            ]
            """);

    Map<String, String> merged = CollectionVariables.merge(Map.of("baseUrl", "old"), variables);

    assertThat(merged)
        .containsEntry("baseUrl", "https://api.example.com")
        .containsEntry("token", "abc")
        .doesNotContainKeys("  ", "count", "empty");
  }

  @Test
  @DisplayName(
      "Given v4 environment resources, when their data is collected, then the data node is used")
  void collectsV4EnvironmentData() throws IOException {
    List<JsonNode> resources =
        List.of(
            json("{\"_type\": \"environment\", \"data\": {\"baseUrl\": \"host\"}}"),
            json("{\"_type\": \"environment\", \"baseUrl\": \"flat\"}"),
            json("{\"_type\": \"request\", \"data\": {\"ignored\": \"value\"}}"));

    List<JsonNode> data = CollectionVariables.environmentData(resources, json("{}"));

    assertThat(data).hasSize(2);
    assertThat(data.get(0).path("baseUrl").asText()).isEqualTo("host");
    assertThat(data.get(1).path("baseUrl").asText()).isEqualTo("flat");
  }

  @Test
  @DisplayName(
      "Given v5 environments as an array or an object, when they are collected, then both shapes are read")
  void collectsV5Environments() throws IOException {
    List<JsonNode> resources = List.of();

    assertThat(
            CollectionVariables.environmentData(
                resources, json("{\"environments\": [{\"baseUrl\": \"a\"}]}")))
        .singleElement()
        .satisfies(node -> assertThat(node.path("baseUrl").asText()).isEqualTo("a"));
    assertThat(
            CollectionVariables.environmentData(
                resources, json("{\"environments\": {\"dev\": {\"baseUrl\": \"b\"}}}")))
        .singleElement()
        .satisfies(node -> assertThat(node.path("baseUrl").asText()).isEqualTo("b"));
  }

  @Test
  @DisplayName(
      "Given environment data nodes, when unique values are read, then ambiguous keys are dropped")
  void keepsOnlyUnambiguousValues() throws IOException {
    List<JsonNode> data =
        List.of(
            json("{\"baseUrl\": \"host\", \"token\": \"a\", \"shared\": \"one\"}"),
            json("{\"shared\": \"two\", \"empty\": \"\", \"number\": 7}"),
            json("\"not an object\""));

    assertThat(CollectionVariables.uniqueValues(data))
        .containsEntry("baseUrl", "host")
        .containsEntry("token", "a")
        .doesNotContainKeys("shared", "empty", "number");
  }

  @Test
  @DisplayName("Given variables with a null key, when merged, then that entry is skipped")
  void skipsNullKeysWhenMerging() throws IOException {
    JsonNode variables =
        json(
            """
            [
              {"key": null, "value": "ignored"},
              {"value": "also ignored"},
              {"key": "token", "value": "abc"}
            ]
            """);

    assertThat(CollectionVariables.merge(Map.of(), variables))
        .containsExactly(Map.entry("token", "abc"));
  }

  @Test
  @DisplayName(
      "Given a null data node among environments, when unique values are read, then it is skipped")
  void skipsNullDataNodes() throws IOException {
    List<JsonNode> data = Arrays.asList(null, json("{\"token\": \"abc\"}"));

    assertThat(CollectionVariables.uniqueValues(data)).containsExactly(Map.entry("token", "abc"));
  }

  @Test
  @DisplayName("Given two variable lists, when they are merged, then the last value wins")
  void mergesRepeatedKeysWithTheLastValue() throws IOException {
    JsonNode first = json("[{\"key\": \"baseUrl\", \"value\": \"first\"}]");
    JsonNode second = json("[{\"key\": \"baseUrl\", \"value\": \"second\"}]");

    Map<String, String> merged =
        CollectionVariables.merge(CollectionVariables.merge(Map.of(), first), second);

    assertThat(merged).containsEntry("baseUrl", "second");
  }
}
