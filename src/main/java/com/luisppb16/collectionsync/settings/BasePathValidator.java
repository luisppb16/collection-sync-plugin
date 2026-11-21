/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */

package com.luisppb16.collectionsync.settings;

import com.luisppb16.collectionsync.domain.model.Segment;
import com.luisppb16.collectionsync.domain.service.PathNormalizer;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/** Validates the URL root entered by the user before it is persisted and used for matching. */
public final class BasePathValidator {

  private BasePathValidator() {}

  /**
   * Classifies a raw base path.
   *
   * @param raw value typed by the user; may be null
   * @return the validation outcome; never null
   */
  public static Result check(@Nullable String raw) {
    if (raw == null || raw.isBlank()) {
      return Result.EMPTY;
    }
    List<Segment> segments = PathNormalizer.normalize(raw);
    if (segments.isEmpty()) {
      return Result.NO_SEGMENTS;
    }
    if (PathNormalizer.isVariableOnly(segments)) {
      return Result.VARIABLE_ONLY;
    }
    return Result.VALID;
  }

  /** Outcome of validating a base path. */
  public enum Result {
    /** Blank value: plain structural matching. */
    EMPTY,
    /** At least one segment with a literal part. */
    VALID,
    /** Non-blank value that normalizes to zero segments (e.g. {@code https://host}). */
    NO_SEGMENTS,
    /** Every segment is a variable (e.g. {@code {{baseUrl}}}). */
    VARIABLE_ONLY
  }
}
