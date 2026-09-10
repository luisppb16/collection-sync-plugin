/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */
package com.luisppb16.collectionsync.domain.model;

import java.util.List;
import java.util.Objects;

/** Result of comparing a collection against the endpoints discovered in the project. */
public record CoverageReport(
    List<CoveredEndpoint> covered,
    List<EndpointDescriptor> missing,
    List<OrphanRequest> orphans,
    CoverageStats stats) {

  public CoverageReport {
    covered = List.copyOf(covered);
    missing = List.copyOf(missing);
    orphans = List.copyOf(orphans);
    stats = Objects.requireNonNull(stats, "stats");
  }
}