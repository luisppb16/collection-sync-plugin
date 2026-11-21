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
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("Segment")
class SegmentTest {

  @Test
  @DisplayName("Given two literal segments with the same text, when compared, then they are equal")
  void comparesLiteralsByValue() {
    assertThat(new Segment.Literal("users")).isEqualTo(new Segment.Literal("users"));
    assertThat(new Segment.Literal("users")).hasSameHashCodeAs(new Segment.Literal("users"));
    assertThat(new Segment.Literal("users")).isNotEqualTo(new Segment.Literal("orders"));
  }

  @Test
  @DisplayName("Given a literal and a variable with the same text, when compared, then they differ")
  void literalsAndVariablesAreDifferentKinds() {
    assertThat((Segment) new Segment.Literal("id")).isNotEqualTo(new Segment.Variable("id"));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  @DisplayName("Given a null or blank literal value, when it is created, then it fails fast")
  void rejectsBlankLiteral(String value) {
    assertThatIllegalArgumentException().isThrownBy(() -> new Segment.Literal(value));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  @DisplayName("Given a null or blank variable syntax, when it is created, then it fails fast")
  void rejectsBlankVariable(String syntax) {
    assertThatIllegalArgumentException().isThrownBy(() -> new Segment.Variable(syntax));
  }

  @Test
  @DisplayName("Given a segment, when it is pattern matched, then its concrete kind is preserved")
  void preservesTheConcreteKind() {
    Segment literal = new Segment.Literal("users");
    Segment variable = new Segment.Variable("{id}");

    assertThat(literal).isInstanceOf(Segment.Literal.class);
    assertThat(variable).isInstanceOf(Segment.Variable.class);
  }
}
