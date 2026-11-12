/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */

package com.luisppb16.collectionsync.io;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Set;
import java.util.stream.Stream;

/** Shared knowledge about the OpenAPI 3.x document shape. */
public final class OpenApiDocument {

  public static final Set<String> OPERATION_KEYS =
      Set.of("get", "post", "put", "delete", "patch", "head", "options", "trace");

  private OpenApiDocument() {}

  public static Stream<Operation> operations(JsonNode root) {
    return root.path("paths").properties().stream()
        .flatMap(pathEntry ->
            pathEntry.getValue().properties().stream()
                .filter(operation -> OPERATION_KEYS.contains(operation.getKey()))
                .map(operation ->
                    new Operation(pathEntry.getKey(), operation.getKey(), operation.getValue())));
  }

  /**
   * Reports whether the given root is an OpenAPI 3.x document.
   *
   * @param root parsed document root; may be null
   * @return true when the root is an object declaring {@code openapi} and {@code paths}
   */
  public static boolean isOpenApi(JsonNode root) {
    return root != null
        && root.isObject()
        && root.hasNonNull("openapi")
        && root.hasNonNull("paths");
  }

  public record Operation(String path, String methodKey, JsonNode node) {}
}
