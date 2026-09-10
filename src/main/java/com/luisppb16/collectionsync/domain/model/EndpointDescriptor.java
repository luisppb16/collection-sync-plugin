/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */
package com.luisppb16.collectionsync.domain.model;

import java.util.List;
import java.util.Objects;

/**
 * A REST endpoint discovered in the project code.
 *
 * <p>{@code pathTemplate} is the class-level and method-level path joined and normalized: it
 * always starts with {@code "/"} and its variables are bare ({@code "/users/{id}"}).
 *
 * <p>{@code httpMethods} being empty means the endpoint accepts any HTTP method (e.g. a Spring
 * {@code @RequestMapping} without {@code method}); generation expands it to a GET happy path.
 */
public record EndpointDescriptor(
    String controllerClassName,
    String methodDeclarationName,
    List<HttpMethod> httpMethods,
    String basePath,
    String pathTemplate,
    List<ParamDescriptor> params,
    DtoShape requestBody,
    String consumes,
    String produces,
    EndpointFramework framework,
    String fileUrl,
    int line) {

  public EndpointDescriptor {
    if (controllerClassName == null || controllerClassName.isBlank()) {
      throw new IllegalArgumentException("controllerClassName must not be blank");
    }
    if (methodDeclarationName == null || methodDeclarationName.isBlank()) {
      throw new IllegalArgumentException("methodDeclarationName must not be blank");
    }
    if (pathTemplate == null || !pathTemplate.startsWith("/")) {
      throw new IllegalArgumentException("pathTemplate must be normalized and start with '/'");
    }
    httpMethods = List.copyOf(httpMethods);
    params = List.copyOf(params);
    framework = Objects.requireNonNull(framework, "framework");
    Objects.requireNonNull(fileUrl, "fileUrl");
  }

  /** @return true when the endpoint accepts any HTTP method. */
  public boolean acceptsAllMethods() {
    return httpMethods.isEmpty();
  }
}