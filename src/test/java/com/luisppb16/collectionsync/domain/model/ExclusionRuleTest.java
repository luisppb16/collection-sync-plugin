/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */

package com.luisppb16.collectionsync.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("ExclusionRule")
class ExclusionRuleTest {

  @Test
  @DisplayName("Given a literal path pattern, when its segments are read, then it is normalized")
  void normalizesLiteralPatterns() {
    ExclusionRule rule = new ExclusionRule(HttpMethod.GET, "/actuator/health/");

    assertThat(rule.segments())
        .containsExactly(new Segment.Literal("actuator"), new Segment.Literal("health"));
  }

  @Test
  @DisplayName("Given a variable path pattern, when its segments are read, then variables are kept")
  void normalizesVariablePatterns() {
    ExclusionRule rule = new ExclusionRule(HttpMethod.GET, "/actuator/{name}");

    assertThat(rule.segments())
        .containsExactly(new Segment.Literal("actuator"), new Segment.Variable("{name}"));
  }

  @Test
  @DisplayName("Given a null method, when the rule is created, then it fails fast")
  void rejectsNullMethod() {
    assertThatIllegalArgumentException().isThrownBy(() -> new ExclusionRule(null, "/actuator"));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  @DisplayName("Given a null or blank pattern, when the rule is created, then it fails fast")
  void rejectsBlankPattern(String pathPattern) {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new ExclusionRule(HttpMethod.GET, pathPattern));
  }

  @Test
  @DisplayName(
      "Given two rules with the same method and pattern, when compared, then they are equal")
  void comparesByValue() {
    ExclusionRule first = new ExclusionRule(HttpMethod.GET, "/actuator");
    ExclusionRule second = new ExclusionRule(HttpMethod.GET, "/actuator");

    assertThat(first).isEqualTo(second);
    assertThat(List.of(first)).containsExactly(second);
  }
}
