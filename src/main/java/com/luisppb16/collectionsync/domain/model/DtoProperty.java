/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */
package com.luisppb16.collectionsync.domain.model;

import java.util.Objects;

/**
 * A single property of a {@link DtoShape}: its name, its leaf value kind and, when the kind is
 * {@link DtoPropertyType#OBJECT} / {@link DtoPropertyType#ARRAY} / {@link DtoPropertyType#MAP},
 * the nested shape (element shape for arrays, entry value shape for maps).
 */
public record DtoProperty(String name, DtoPropertyType type, DtoShape nested) {

  public DtoProperty {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("DtoProperty.name must not be blank");
    }
    type = Objects.requireNonNull(type, "type");
  }
}