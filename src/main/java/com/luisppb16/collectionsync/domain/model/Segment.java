/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */

package com.luisppb16.collectionsync.domain.model;

/**
 * A single segment of a normalized path template.
 *
 * <p>A normalized path is a list of segments. Two path templates match structurally if they have
 * the same number of segments and every position is compatible (see {@code PathMatcher}).
 */
public sealed interface Segment permits Segment.Literal, Segment.Variable {

  /**
   * A fixed path segment compared by exact, case-sensitive text.
   *
   * @param value segment text without separators; never blank
   */
  record Literal(String value) implements Segment {
    public Literal {
      if (value == null || value.isBlank()) {
        throw new IllegalArgumentException("Literal segment must not be null or blank");
      }
    }
  }

  /**
   * A variable segment. It matches any single path segment regardless of the syntax that produced
   * it ({@code {id}}, {@code {id:[0-9]+}}, {@code :id}, {@code {{id}}}, {@code {{ _.id }}}) and
   * regardless of the parameter name.
   */
  record Variable(String syntax) implements Segment {
    public Variable {
      if (syntax == null || syntax.isBlank()) {
        throw new IllegalArgumentException("Variable syntax must not be null or blank");
      }
    }
  }
}
