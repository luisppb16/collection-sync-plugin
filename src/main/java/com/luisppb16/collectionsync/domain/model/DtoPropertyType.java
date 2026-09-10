/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */
package com.luisppb16.collectionsync.domain.model;

/** Leaf value kinds used when rendering a DTO shape to example JSON. */
public enum DtoPropertyType {
  STRING,
  NUMBER,
  INTEGER,
  BOOLEAN,
  ENUM,
  DATE,
  DATETIME,
  ARRAY,
  OBJECT,
  MAP,
  UNKNOWN
}