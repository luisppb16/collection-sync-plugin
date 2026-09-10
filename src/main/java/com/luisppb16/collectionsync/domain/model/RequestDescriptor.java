/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */
package com.luisppb16.collectionsync.domain.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Objects;

/**
 * A single request inside a collection.
 *
 * <p>{@code rawUrl} keeps the collection-native syntax ({@code {{baseUrl}}/{{id}}} for Postman,
 * {@code _.base_url/{{ _.id }}} for Insomnia); {@code normalizedTemplate} is the framework-agnostic
 * path used for matching, computed from {@code rawUrl}.
 *
 * <p>{@code rawExtra} carries collection-format passthrough data (scripts, auth, responses) that
 * the plugin does not interpret but must not lose on export.
 */
public record RequestDescriptor(
    String name,
    HttpMethod method,
    String rawUrl,
    String normalizedTemplate,
    List<ParamDescriptor> query,
    List<ParamDescriptor> headers,
    JsonNode body,
    String folderPath,
    RequestSource source,
    JsonNode rawExtra) {

  public RequestDescriptor {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("RequestDescriptor.name must not be blank");
    }
    method = Objects.requireNonNull(method, "method");
    Objects.requireNonNull(rawUrl, "rawUrl");
    query = List.copyOf(query);
    headers = List.copyOf(headers);
    Objects.requireNonNull(source, "source");
  }
}