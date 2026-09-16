/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */

package com.luisppb16.collectionsync.domain.model;

import java.util.List;

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
}
