/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */

package com.luisppb16.collectionsync.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("HttpMethod")
class HttpMethodTest {

  @ParameterizedTest
  @CsvSource({
    "get, GET",
    "Post, POST",
    "'  put  ', PUT",
    "delete, DELETE",
    "PATCH, PATCH",
    "head, HEAD",
    "options, OPTIONS",
    "trace, TRACE"
  })
  @DisplayName(
      "Given a raw method, when it is parsed, then it is normalized to the matching constant")
  void parsesKnownMethods(String raw, HttpMethod expected) {
    assertThat(HttpMethod.of(raw)).isEqualTo(expected);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   ", "\t"})
  @DisplayName("Given a null or blank raw method, when it is parsed, then it fails fast")
  void rejectsBlankMethods(String raw) {
    assertThatIllegalArgumentException().isThrownBy(() -> HttpMethod.of(raw));
  }

  @Test
  @DisplayName("Given the ANY wildcard, when it is parsed as a method, then it is rejected")
  void rejectsAnyWildcard() {
    assertThatIllegalArgumentException().isThrownBy(() -> HttpMethod.of("any"));
  }

  @Test
  @DisplayName("Given an unsupported method name, when it is parsed, then it is rejected")
  void rejectsUnknownMethod() {
    assertThatIllegalArgumentException().isThrownBy(() -> HttpMethod.of("BREW"));
  }
}
