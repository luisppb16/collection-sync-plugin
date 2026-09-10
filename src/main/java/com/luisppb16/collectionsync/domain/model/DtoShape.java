/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */
package com.luisppb16.collectionsync.domain.model;

import java.util.List;
import java.util.Objects;

/**
 * Framework-agnostic shape of a request body DTO: a recursive tree of properties used by the JSON
 * generator to produce example bodies. Built by {@code DtoShapeResolver} in the PSI layer.
 */
public record DtoShape(String name, DtoKind kind, List<DtoProperty> properties) {

  public DtoShape {
    name = Objects.requireNonNull(name, "name");
    kind = Objects.requireNonNull(kind, "kind");
    properties = List.copyOf(properties);
  }
}