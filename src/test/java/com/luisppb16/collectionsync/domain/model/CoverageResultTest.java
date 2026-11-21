/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */

package com.luisppb16.collectionsync.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.tuple;
import static org.instancio.Select.field;

import java.util.List;
import java.util.function.Predicate;
import org.instancio.Instancio;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("CoverageResult")
class CoverageResultTest {

  private static CoverageRow row(CoverageStatus status, String path) {
    return Instancio.of(CoverageRow.class)
        .set(field(CoverageRow::status), status)
        .set(field(CoverageRow::method), HttpMethod.GET)
        .set(field(CoverageRow::path), path)
        .set(field(CoverageRow::owner), "owner")
        .set(field(CoverageRow::module), "app")
        .create();
  }

  private static CoverageResult result() {
    return new CoverageResult(
        List.of(
            row(CoverageStatus.COVERED, "/covered"),
            row(CoverageStatus.UNCOVERED, "/uncovered"),
            row(CoverageStatus.ORPHAN, "/orphan")),
        List.of(row(CoverageStatus.EXCLUDED, "/excluded")),
        1,
        1,
        1,
        1,
        3);
  }

  @Test
  @DisplayName(
      "Given a result, when counts are computed for every row, then the four statuses are tallied")
  void countsRowsOfEveryStatus() {
    CoverageResult.Counts counts = result().counts(row -> true);

    assertThat(counts)
        .extracting(
            CoverageResult.Counts::covered,
            CoverageResult.Counts::uncovered,
            CoverageResult.Counts::orphan,
            CoverageResult.Counts::excluded)
        .containsExactly(1, 1, 1, 1);
  }

  @Test
  @DisplayName("Given a predicate, when counts are computed, then only matching rows are tallied")
  void countsOnlyMatchingRows() {
    CoverageResult.Counts counts =
        result()
            .counts(
                row -> row.path().startsWith("/cov") || row.status() == CoverageStatus.EXCLUDED);

    assertThat(tuple(counts.covered(), counts.uncovered(), counts.orphan(), counts.excluded()))
        .isEqualTo(
            tuple(Integer.valueOf(1), Integer.valueOf(0), Integer.valueOf(0), Integer.valueOf(1)));
  }

  @Test
  @DisplayName(
      "Given a result, when all rows are streamed, then excluded rows follow the visible ones")
  void streamsVisibleAndExcludedRows() {
    assertThat(result().allRows())
        .extracting(CoverageRow::path)
        .containsExactly("/covered", "/uncovered", "/orphan", "/excluded");
  }

  @Test
  @DisplayName(
      "Given a predicate, when the result is filtered, then rows, counts and collection count follow")
  void filtersRowsAndRecalculatesCounts() {
    CoverageResult filtered = result().filtered(row -> row.status() != CoverageStatus.ORPHAN);

    assertThat(filtered.rows())
        .extracting(CoverageRow::path)
        .containsExactly("/covered", "/uncovered");
    assertThat(filtered.excludedRows()).extracting(CoverageRow::path).containsExactly("/excluded");
    assertThat(filtered.coveredCount()).isEqualTo(1);
    assertThat(filtered.uncoveredCount()).isEqualTo(1);
    assertThat(filtered.orphanCount()).isZero();
    assertThat(filtered.excludedCount()).isEqualTo(1);
    assertThat(filtered.collectionCount()).isEqualTo(3);
  }

  @Test
  @DisplayName(
      "Given a null predicate, when counts or filtering are requested, then they fail fast")
  void rejectsNullPredicate() {
    CoverageResult result = result();

    assertThatNullPointerException().isThrownBy(() -> result.counts(null));
    assertThatNullPointerException().isThrownBy(() -> result.filtered(null));
  }

  @Test
  @DisplayName("Given null row lists, when the result is created, then it fails fast")
  void rejectsNullRowLists() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new CoverageResult(null, List.of(), 0, 0, 0, 0, 0));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new CoverageResult(List.of(), null, 0, 0, 0, 0, 0));
  }

  @Test
  @DisplayName("Given a negative counter, when the result is created, then it fails fast")
  void rejectsNegativeCounters() {
    List<CoverageRow> rows = List.of();

    assertThatIllegalArgumentException()
        .isThrownBy(() -> new CoverageResult(rows, rows, -1, 0, 0, 0, 0));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new CoverageResult(rows, rows, 0, -1, 0, 0, 0));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new CoverageResult(rows, rows, 0, 0, -1, 0, 0));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new CoverageResult(rows, rows, 0, 0, 0, -1, 0));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new CoverageResult(rows, rows, 0, 0, 0, 0, -1));
  }

  @Test
  @DisplayName(
      "Given mutable row lists, when the result is created, then it keeps immutable copies")
  void copiesRowsDefensively() {
    CoverageResult result = result();

    assertThat(result.rows()).isUnmodifiable();
    assertThat(result.excludedRows()).isUnmodifiable();
  }

  @Test
  @DisplayName(
      "Given a predicate that matches nothing, when counts are computed, then every counter is zero")
  void countsNothingWhenNoRowMatches() {
    Predicate<CoverageRow> none = row -> false;

    assertThat(result().counts(none).covered()).isZero();
    assertThat(result().counts(none).excluded()).isZero();
  }
}
