/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */

package com.luisppb16.collectionsync.domain.model;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Outcome of a coverage calculation.
 *
 * @param rows all visible rows (covered, uncovered and orphan), sorted for display; never null
 * @param excludedRows endpoints hidden by exclusion rules, for the "Quitar exclusión" action; never
 *     null
 * @param coveredCount endpoints matched by at least one request
 * @param uncoveredCount endpoints matched by no request
 * @param orphanCount requests matched by no endpoint
 * @param excludedCount endpoints skipped because of exclusion rules
 * @param collectionCount number of collection files parsed for this result
 */
public record CoverageResult(
    List<CoverageRow> rows,
    List<CoverageRow> excludedRows,
    int coveredCount,
    int uncoveredCount,
    int orphanCount,
    int excludedCount,
    int collectionCount) {

  public CoverageResult {
    if (rows == null || excludedRows == null) {
      throw new IllegalArgumentException("Coverage rows must not be null");
    }
    rows = List.copyOf(rows);
    excludedRows = List.copyOf(excludedRows);
    if (coveredCount < 0
        || uncoveredCount < 0
        || orphanCount < 0
        || excludedCount < 0
        || collectionCount < 0) {
      throw new IllegalArgumentException("Coverage counts must not be negative");
    }
  }

  public Counts counts(Predicate<CoverageRow> predicate) {
    Objects.requireNonNull(predicate, "predicate must not be null");
    int covered = 0;
    int uncovered = 0;
    int orphan = 0;
    int excluded = 0;
    for (CoverageRow row : rows) {
      if (!predicate.test(row)) {
        continue;
      }
      switch (row.status()) {
        case COVERED -> covered++;
        case UNCOVERED -> uncovered++;
        case ORPHAN -> orphan++;
        case EXCLUDED -> {}
      }
    }
    for (CoverageRow row : excludedRows) {
      if (predicate.test(row)) {
        excluded++;
      }
    }
    return new Counts(covered, uncovered, orphan, excluded);
  }

  public Stream<CoverageRow> allRows() {
    return Stream.concat(rows.stream(), excludedRows.stream());
  }

  public CoverageResult filtered(Predicate<CoverageRow> predicate) {
    Objects.requireNonNull(predicate, "predicate must not be null");
    List<CoverageRow> filteredRows = rows.stream().filter(predicate).toList();
    List<CoverageRow> filteredExcludedRows = excludedRows.stream().filter(predicate).toList();
    Counts counts = counts(predicate);
    return new CoverageResult(
        filteredRows,
        filteredExcludedRows,
        counts.covered(),
        counts.uncovered(),
        counts.orphan(),
        counts.excluded(),
        collectionCount);
  }

  public record Counts(int covered, int uncovered, int orphan, int excluded) {}
}
