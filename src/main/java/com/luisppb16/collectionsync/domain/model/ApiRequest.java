/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */

package com.luisppb16.collectionsync.domain.model;

/**
 * A request defined inside an imported collection (Postman v2.1 or Insomnia v4/v5).
 *
 * <p>{@code rawUrl} is the URL exactly as declared in the collection: it may be a full URL
 * ({@code https://api.example.com/users/17}), use a collection variable
 * ({@code {{baseUrl}}/users/:userId}) or a plain path ({@code /users/1}). Normalization is
 * performed later by the matching engine.
 *
 * @param method         HTTP method; must not be null
 * @param rawUrl         URL or path as declared in the collection; must not be blank
 * @param name           request name shown in the report; never null
 * @param collectionName name of the collection file the request belongs to; never blank
 */
public record ApiRequest(HttpMethod method, String rawUrl, String name, String collectionName) {

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
    }
}