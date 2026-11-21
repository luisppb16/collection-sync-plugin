/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */

package com.luisppb16.collectionsync.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.luisppb16.collectionsync.domain.model.ApiEndpoint;
import com.luisppb16.collectionsync.domain.model.HttpMethod;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("ControllerEndpointSource path helpers")
class ControllerEndpointSourcePathsTest {

  @ParameterizedTest
  @CsvSource({
    "'/api/', '/users', '/api/users'",
    "'api', 'users', '/api/users'",
    "'/api', '/users', '/api/users'",
    "'//api//', '//users//', '/api/users'",
    "'', '/users', '/users'",
    "'/api', '', '/api'",
    "'/', '/', ''",
    "'', '', ''"
  })
  @DisplayName(
      "Given a base path and a method path, when joined, then exactly one slash separates them")
  void joinsPaths(String basePath, String methodPath, String expected) {
    assertThat(ControllerEndpointSource.joinPath(basePath, methodPath)).isEqualTo(expected);
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", "/", "//", "   "})
  @DisplayName("Given a null or separator-only path, when stripped, then it becomes empty")
  void stripsPathsWithoutSegments(String path) {
    assertThat(ControllerEndpointSource.stripSlashes(path)).isEmpty();
  }

  @ParameterizedTest
  @CsvSource({
    "'  /api/users/  ', 'api/users'",
    "'api/users', 'api/users'",
    "'///api/users///', 'api/users'"
  })
  @DisplayName(
      "Given a path with surrounding separators, when stripped, then only the edge separators go")
  void stripsEdgeSlashes(String path, String expected) {
    assertThat(ControllerEndpointSource.stripSlashes(path)).isEqualTo(expected);
  }

  @Test
  @DisplayName(
      "Given base paths, method paths and methods, when expanded, then one endpoint per combination")
  void expandsEveryCombination() {
    List<ApiEndpoint> endpoints =
        ControllerEndpointSource.expand(
            List.of("/api", "/v1"),
            List.of("/users", "/users", "/orders"),
            List.of(HttpMethod.GET, HttpMethod.POST, HttpMethod.GET),
            "UserController",
            "app",
            null);

    assertThat(endpoints)
        .extracting(
            ApiEndpoint::method,
            ApiEndpoint::pathTemplate,
            ApiEndpoint::ownerClass,
            ApiEndpoint::moduleName)
        .containsExactly(
            tuple(HttpMethod.GET, "/api/users", "UserController", "app"),
            tuple(HttpMethod.POST, "/api/users", "UserController", "app"),
            tuple(HttpMethod.GET, "/api/orders", "UserController", "app"),
            tuple(HttpMethod.POST, "/api/orders", "UserController", "app"),
            tuple(HttpMethod.GET, "/v1/users", "UserController", "app"),
            tuple(HttpMethod.POST, "/v1/users", "UserController", "app"),
            tuple(HttpMethod.GET, "/v1/orders", "UserController", "app"),
            tuple(HttpMethod.POST, "/v1/orders", "UserController", "app"));
  }

  @Test
  @DisplayName("Given no methods, when expanded, then no endpoints are produced")
  void expandsNothingWithoutMethods() {
    assertThat(
            ControllerEndpointSource.expand(
                List.of("/api"), List.of("/users"), List.of(), "UserController", "app", null))
        .isEmpty();
  }

  @Test
  @DisplayName("Given empty paths, when expanded, then the empty path is skipped")
  void skipsEmptyPaths() {
    assertThat(
            ControllerEndpointSource.expand(
                List.of(""), List.of(""), List.of(HttpMethod.GET), "UserController", "app", null))
        .isEmpty();
  }
}
