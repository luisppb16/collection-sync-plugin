/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */
package com.luisppb16.collectionsync.domain.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luisppb16.collectionsync.domain.model.DtoKind;
import com.luisppb16.collectionsync.domain.model.DtoProperty;
import com.luisppb16.collectionsync.domain.model.DtoPropertyType;
import com.luisppb16.collectionsync.domain.model.DtoShape;
import com.luisppb16.collectionsync.domain.model.EndpointDescriptor;
import com.luisppb16.collectionsync.domain.model.EndpointFramework;
import com.luisppb16.collectionsync.domain.model.HttpMethod;
import com.luisppb16.collectionsync.domain.model.ParamDescriptor;
import com.luisppb16.collectionsync.domain.model.ParamLocation;
import com.luisppb16.collectionsync.domain.model.RequestDescriptor;
import com.luisppb16.collectionsync.domain.model.RequestSource;
import java.util.List;

/** Minimal fixtures for domain service tests. */
final class TestFixtures {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private TestFixtures() {}

  static EndpointDescriptor endpoint(String controller, String template, HttpMethod... methods) {
    return new EndpointDescriptor(
        controller, "handleRequest", List.of(methods), "", template, List.of(), null, null, null,
        EndpointFramework.SPRING, "file:///UserController.java", 10);
  }

  static EndpointDescriptor endpointWithParams(
      String controller, String template, List<ParamDescriptor> params, DtoShape requestBody, HttpMethod... methods) {
    return new EndpointDescriptor(
        controller, "handleRequest", List.of(methods), "", template, params, requestBody, null, null,
        EndpointFramework.SPRING, "file:///UserController.java", 10);
  }

  static RequestDescriptor request(String name, HttpMethod method, String rawUrl) {
    return new RequestDescriptor(
        name, method, rawUrl, "", List.of(), List.of(), null, "", RequestSource.POSTMAN, null);
  }

  static RequestDescriptor requestWithParams(
      String name,
      HttpMethod method,
      String rawUrl,
      List<ParamDescriptor> query,
      List<ParamDescriptor> headers,
      String jsonBody) {
    JsonNode body = jsonBody == null ? null : jsonNode(jsonBody);
    return new RequestDescriptor(
        name, method, rawUrl, "", query, headers, body, "", RequestSource.POSTMAN, null);
  }

  static DtoShape userShape() {
    return new DtoShape(
        "UserDto",
        DtoKind.POJO,
        List.of(
            new DtoProperty("name", DtoPropertyType.STRING, null),
            new DtoProperty("email", DtoPropertyType.STRING, null)));
  }

  static JsonNode jsonNode(String json) {
    try {
      return MAPPER.readTree(json);
    } catch (Exception exception) {
      throw new IllegalArgumentException("invalid test JSON: " + json, exception);
    }
  }
}