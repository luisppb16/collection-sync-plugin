/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */
package com.luisppb16.collectionsync.domain.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.Objects;

/**
 * Framework-agnostic model of an imported or generated collection, shared by the Postman and
 * Insomnia mappers and by the coverage/matching engine. {@code rawExtra} keeps the format-native
 * collection node (Postman root fields, Insomnia workspace) for faithful re-export.
 */
public record CollectionModel(String id, String name, CollectionFolder root, Map<String, String> variables, String format, JsonNode rawExtra) {

  public CollectionModel {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("CollectionModel.name must not be blank");
    }
    root = Objects.requireNonNull(root, "root");
    variables = Map.copyOf(variables);
    format = Objects.requireNonNull(format, "format");
  }

  /** Convenience constructor for collections without passthrough data. */
  public CollectionModel(String id, String name, CollectionFolder root, Map<String, String> variables, String format) {
    this(id, name, root, variables, format, null);
  }
}