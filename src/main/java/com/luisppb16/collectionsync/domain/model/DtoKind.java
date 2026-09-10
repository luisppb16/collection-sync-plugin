/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */
package com.luisppb16.collectionsync.domain.model;

/** Structural kind of a resolved DTO shape. */
public enum DtoKind {
  POJO,
  RECORD,
  ENUM,
  COLLECTION,
  MAP,
  PRIMITIVE,
  UNKNOWN
}