/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */

package com.luisppb16.collectionsync.source;

import static org.assertj.core.api.Assertions.assertThat;

import com.luisppb16.collectionsync.domain.model.HttpMethod;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("KotlinEndpointSource")
class KotlinEndpointSourceTextTest {

  @ParameterizedTest
  @CsvSource({
    "Get, GET",
    "GetMapping, GET",
    "Post, POST",
    "PostMapping, POST",
    "Put, PUT",
    "Delete, DELETE",
    "Patch, PATCH",
    "Head, HEAD",
    "Options, OPTIONS",
    "Trace, TRACE"
  })
  @DisplayName(
      "Given a Kotlin annotation short name, when its verb is read, then the mapping suffix is dropped")
  void mapsAnnotationNamesToVerbs(String annotationName, HttpMethod expected) {
    assertThat(KotlinEndpointSource.verbOf(annotationName)).isEqualTo(expected);
  }

  @ParameterizedTest
  @ValueSource(strings = {"Request", "RequestMapping", "Controller", "Path", ""})
  @DisplayName("Given a non-verb annotation short name, when its verb is read, then there is none")
  void returnsNoVerbForNonVerbAnnotations(String annotationName) {
    assertThat(KotlinEndpointSource.verbOf(annotationName)).isNull();
  }

  @Test
  @DisplayName(
      "Given annotation text with string literals, when they are read, then every literal is returned")
  void readsEveryQuotedString() {
    assertThat(KotlinEndpointSource.quotedStrings("@GetMapping(\"/api\", path = \"/users\")"))
        .containsExactly("/api", "/users");
  }

  @Test
  @DisplayName("Given an escaped quote, when literals are read, then the escape is preserved")
  void readsEscapedQuotes() {
    assertThat(KotlinEndpointSource.quotedStrings("@Path(\"/a\\\"b\")")).containsExactly("/a\\\"b");
  }

  @Test
  @DisplayName("Given annotation text without literals, when they are read, then the list is empty")
  void readsNoLiteralsFromBareAnnotation() {
    assertThat(KotlinEndpointSource.quotedStrings("@Get")).isEmpty();
  }

  @Test
  @DisplayName(
      "Given several annotations, when literal lists are combined, then no literal is lost")
  void readsLiteralsFromSeveralTexts() {
    List<String> literals =
        List.of("@Get(\"/a\")", "@Post(\"/b\")").stream()
            .flatMap(text -> KotlinEndpointSource.quotedStrings(text).stream())
            .toList();

    assertThat(literals).containsExactly("/a", "/b");
  }
}
