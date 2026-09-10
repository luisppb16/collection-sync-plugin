/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */
package com.luisppb16.collectionsync.domain.model;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Aggregate numbers of a coverage report. */
public record CoverageStats(
    int totalEndpoints,
    int coveredEndpoints,
    int missingEndpoints,
    int orphanRequests,
    int duplicateRequests,
    double coveragePercent,
    Map<HttpMethod, Long> coveredByMethod,
    Map<String, Long> coveredByController) {

  public CoverageStats {
    coveredByMethod = Map.copyOf(coveredByMethod);
    coveredByController = Map.copyOf(coveredByController);
  }
}