/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */

package com.luisppb16.collectionsync.collection;

import com.fasterxml.jackson.databind.JsonNode;
import com.luisppb16.collectionsync.domain.model.ApiRequest;
import com.luisppb16.collectionsync.domain.model.HttpMethod;
import com.luisppb16.collectionsync.io.JsonYaml;
import com.luisppb16.collectionsync.io.OpenApiDocument;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/**
 * Parses an OpenAPI 3.x document as a collection of requests: every operation of every path becomes
 * one {@link ApiRequest}, so an API contract can be used as the reference for endpoint coverage.
 */
public final class OpenApiCollectionParser implements CollectionParser {

  private static List<ApiRequest> requestsOf(JsonNode root, File file) {
    if (!OpenApiDocument.isOpenApi(root)) {
      throw new IllegalArgumentException(
          "Not an OpenAPI 3.x document: expected 'openapi' and 'paths': " + file);
    }
    String collectionName = CollectionParsers.displayTitleOrFileBase(root.path("info"), file);
    return OpenApiDocument.operations(root)
        .map(operation ->
            new ApiRequest(
                HttpMethod.of(operation.methodKey()),
                operation.path(),
                requestName(operation.node(), operation.methodKey(), operation.path()),
                collectionName))
        .toList();
  }

  private static String requestName(JsonNode operation, String method, String path) {
    String operationId = operation.path("operationId").asText("").strip();
    if (!operationId.isEmpty()) {
      return operationId;
    }
    String summary = operation.path("summary").asText("").strip();
    if (!summary.isEmpty()) {
      return summary;
    }
    return method.toUpperCase(Locale.ROOT) + " " + path;
  }

  @Override
  public @NotNull List<ApiRequest> parse(@NotNull File file) throws IOException {
    Objects.requireNonNull(file, "file must not be null");
    return parseRoot(JsonYaml.readTree(file), file);
  }

  List<ApiRequest> parseRoot(@NotNull JsonNode root, @NotNull File file) {
    return requestsOf(root, file);
  }
}
