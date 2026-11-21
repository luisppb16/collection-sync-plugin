/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */

package com.luisppb16.collectionsync.settings;

import static org.assertj.core.api.Assertions.assertThat;

import com.luisppb16.collectionsync.settings.BasePathValidator.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("BasePathValidator")
class BasePathValidatorTest {

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  @DisplayName("Given a blank base path, when it is checked, then it is empty")
  void treatsBlankAsEmpty(String raw) {
    assertThat(BasePathValidator.check(raw)).isEqualTo(Result.EMPTY);
  }

  @ParameterizedTest
  @CsvSource({
    "/api/test/v1, VALID",
    "api/test/v1, VALID",
    "host:8080/api, VALID",
    "/api/{{version}}, VALID",
    "https://api.example.com, NO_SEGMENTS",
    "'/', NO_SEGMENTS",
    "{{baseUrl}}, VARIABLE_ONLY",
    "'/{id}', VARIABLE_ONLY"
  })
  @DisplayName("Given a non-blank base path, when it is checked, then it is classified")
  void classifiesNonBlankBasePaths(String raw, Result expected) {
    assertThat(BasePathValidator.check(raw)).isEqualTo(expected);
  }
}
