/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */
package com.luisppb16.collectionsync.domain.model;

import java.util.List;
import java.util.Objects;

/** A collection request that matches no endpoint, with up to three suggested endpoint candidates. */
public record OrphanRequest(RequestDescriptor request, List<OrphanCandidate> candidates) {

  public OrphanRequest {
    request = Objects.requireNonNull(request, "request");
    candidates = List.copyOf(candidates);
  }
}