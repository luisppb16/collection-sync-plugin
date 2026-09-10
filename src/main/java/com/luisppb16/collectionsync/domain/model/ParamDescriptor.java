/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */
package com.luisppb16.collectionsync.domain.model;

import java.util.Objects;

/** A single typed parameter of an endpoint or request (path variable, query param or header). */
public record ParamDescriptor(String name, ParamLocation location, String typeName, boolean required) {

  public ParamDescriptor {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("ParamDescriptor.name must not be blank");
    }
    location = Objects.requireNonNull(location, "location");
  }
}