/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */

package com.luisppb16.collectionsync.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.instancio.Select.field;

import org.instancio.Instancio;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("CoverageRow")
class CoverageRowTest {

  @Test
  @DisplayName("Given null owner and module, when the row is created, then both become empty")
  void normalizesNullOwnerAndModule() {
    CoverageRow row =
        new CoverageRow(CoverageStatus.COVERED, HttpMethod.GET, "/users", null, null, null);

    assertThat(row.owner()).isEmpty();
    assertThat(row.module()).isEmpty();
  }

  @Test
  @DisplayName("Given a null status, when the row is created, then it fails fast")
  void rejectsNullStatus() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new CoverageRow(null, HttpMethod.GET, "/users", "Users", "app", null));
  }

  @Test
  @DisplayName("Given a null method, when the row is created, then it fails fast")
  void rejectsNullMethod() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () -> new CoverageRow(CoverageStatus.COVERED, null, "/users", "Users", "app", null));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  @DisplayName("Given a null or blank path, when the row is created, then it fails fast")
  void rejectsBlankPath(String path) {
    assertThatIllegalArgumentException()
        .isThrownBy(() ->
            new CoverageRow(CoverageStatus.COVERED, HttpMethod.GET, path, "Users", "app", null));
  }

  @Test
  @DisplayName("Given generated data, when the row is created, then the values are kept")
  void keepsGeneratedValues() {
    CoverageRow row =
        Instancio.of(CoverageRow.class)
            .set(field(CoverageRow::status), CoverageStatus.ORPHAN)
            .set(field(CoverageRow::method), HttpMethod.POST)
            .set(field(CoverageRow::path), "/reports")
            .create();

    assertThat(row.status()).isEqualTo(CoverageStatus.ORPHAN);
    assertThat(row.method()).isEqualTo(HttpMethod.POST);
    assertThat(row.path()).isEqualTo("/reports");
  }
}
