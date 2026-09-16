/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */

package com.luisppb16.collectionsync.domain.model;

import java.util.List;

/**
 * A user-defined exclusion: endpoints (and the requests that cover them) that are always omitted
 * from the coverage calculation.
 *
 * <p>The rule is matched structurally: an endpoint or request is excluded when its HTTP method is
 * equal and its normalized path matches {@link #pathPattern} with the same segment rules used by
 * the coverage matching engine.
 *
 * @param method HTTP method of the excluded endpoint; must not be null
 * @param pathPattern raw path as entered by the user, e.g. {@code /actuator/{name}}; must not be
 *     blank
 */
public record ExclusionRule(HttpMethod method, String pathPattern) {

  public ExclusionRule {
    if (method == null) {
      throw new IllegalArgumentException("Exclusion method must not be null");
    }
    if (pathPattern == null || pathPattern.isBlank()) {
      throw new IllegalArgumentException("Exclusion path must not be null or blank");
    }
  }

  /** Normalized segments of {@link #pathPattern}; used for structural matching. */
  public List<Segment> segments() {
    return com.luisppb16.collectionsync.domain.service.PathNormalizer.normalize(pathPattern);
  }
}
