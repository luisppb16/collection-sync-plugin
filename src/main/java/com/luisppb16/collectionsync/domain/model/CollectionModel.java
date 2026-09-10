/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */
package com.luisppb16.collectionsync.domain.model;

import java.util.Map;
import java.util.Objects;

/**
 * Framework-agnostic model of an imported or generated collection, shared by the Postman and
 * Insomnia mappers and by the coverage/matching engine.
 */
public record CollectionModel(String id, String name, CollectionFolder root, Map<String, String> variables, String format) {

  public CollectionModel {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("CollectionModel.name must not be blank");
    }
    root = Objects.requireNonNull(root, "root");
    variables = Map.copyOf(variables);
    format = Objects.requireNonNull(format, "format");
  }
}