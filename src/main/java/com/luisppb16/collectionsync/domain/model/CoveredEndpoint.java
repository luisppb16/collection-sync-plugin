/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */
package com.luisppb16.collectionsync.domain.model;

import java.util.Objects;

/** An endpoint together with the request that covers it and the quality of that coverage. */
public record CoveredEndpoint(EndpointDescriptor endpoint, RequestDescriptor request, CoverageLevel level) {

  public CoveredEndpoint {
    endpoint = Objects.requireNonNull(endpoint, "endpoint");
    request = Objects.requireNonNull(request, "request");
    level = Objects.requireNonNull(level, "level");
  }
}