/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */

package com.luisppb16.collectionsync.domain.model;

import java.util.Locale;

/** HTTP methods supported by the coverage calculation. */
public enum HttpMethod {
  GET,
  POST,
  PUT,
  DELETE,
  PATCH,
  HEAD,
  OPTIONS;

  /**
   * Parses an HTTP method, normalizing it to upper case.
   *
   * @param raw raw method name as found in a collection file or annotation; must not be {@code
   *     null}
   * @return the matching constant
   * @throws IllegalArgumentException if the raw value is null, blank or not a supported method
   */
  public static HttpMethod of(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new IllegalArgumentException("HTTP method must not be null or blank");
    }
    return HttpMethod.valueOf(raw.strip().toUpperCase(Locale.ROOT));
  }
}
