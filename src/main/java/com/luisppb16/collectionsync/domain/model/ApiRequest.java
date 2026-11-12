/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */

package com.luisppb16.collectionsync.domain.model;

/**
 * A request defined inside an imported collection (Postman v2.1, Insomnia v4/v5 or OpenAPI 3.x).
 *
 * <p>{@code rawUrl} is the URL used for matching: it may be a full URL ({@code
 * https://api.example.com/users/17}), a path with an unresolved variable ({@code
 * {{baseUrl}}/users/:userId}) or a plain path ({@code /users/1}). Known collection variables are
 * resolved before the request is created; unresolved ones stay as wildcards for {@code
 * PathNormalizer}.
 *
 * @param method HTTP method; must not be null
 * @param rawUrl URL or path used for matching; must not be blank
 * @param name request name shown in the report; never null
 * @param collectionName name of the collection the request belongs to; never blank
 * @param moduleName module/service the collection is associated with, or empty when global
 */
public record ApiRequest(
    HttpMethod method, String rawUrl, String name, String collectionName, String moduleName) {

  public ApiRequest(HttpMethod method, String rawUrl, String name, String collectionName) {
    this(method, rawUrl, name, collectionName, "");
  }

  public ApiRequest {
    if (method == null) {
      throw new IllegalArgumentException("Request method must not be null");
    }
    if (rawUrl == null || rawUrl.isBlank()) {
      throw new IllegalArgumentException("Request url must not be null or blank");
    }
    if (collectionName == null || collectionName.isBlank()) {
      throw new IllegalArgumentException("Collection name must not be null or blank");
    }
    name = name == null ? "" : name;
    moduleName = moduleName == null ? "" : moduleName;
  }

  public ApiRequest withModuleName(String moduleName) {
    return new ApiRequest(method, rawUrl, name, collectionName, moduleName);
  }
}
