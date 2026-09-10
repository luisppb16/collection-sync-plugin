/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */
package com.luisppb16.collectionsync.domain.model;

import java.util.Objects;

/** An endpoint suggested as a possible target for an orphan request, with its similarity score. */
public record OrphanCandidate(EndpointDescriptor endpoint, double score) {

  public OrphanCandidate {
    endpoint = Objects.requireNonNull(endpoint, "endpoint");
  }
}