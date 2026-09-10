/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */
package com.luisppb16.collectionsync.infrastructure.io;

/** Thrown when a file does not contain a valid collection of the expected format. */
public class UnsupportedCollectionFormatException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public UnsupportedCollectionFormatException(String message) {
    super(message);
  }
}