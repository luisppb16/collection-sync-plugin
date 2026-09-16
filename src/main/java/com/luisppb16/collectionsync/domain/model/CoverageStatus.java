/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */

package com.luisppb16.collectionsync.domain.model;

/** Classification of one row of the coverage report. */
public enum CoverageStatus {
  /** At least one collection request matches this endpoint. */
  COVERED,
  /** No collection request matches this endpoint. */
  UNCOVERED,
  /** Collection request that matches no endpoint of the project. */
  ORPHAN,
  /** Endpoint hidden by a user exclusion rule; only shown when the user lists exclusions. */
  EXCLUDED
}
