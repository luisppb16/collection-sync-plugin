/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */

package com.luisppb16.collectionsync.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("ApiRequest")
class ApiRequestTest {

  @Test
  @DisplayName(
      "Given the short constructors, when a request is created, then module and base path default to empty")
  void defaultsModuleAndBasePathToEmpty() {
    ApiRequest fourArguments = new ApiRequest(HttpMethod.GET, "/users", "List users", "Users API");
    ApiRequest fiveArguments =
        new ApiRequest(HttpMethod.GET, "/users", "List users", "Users API", "app");

    assertThat(fourArguments.moduleName()).isEmpty();
    assertThat(fourArguments.basePath()).isEmpty();
    assertThat(fiveArguments.moduleName()).isEqualTo("app");
    assertThat(fiveArguments.basePath()).isEmpty();
  }

  @Test
  @DisplayName("Given null optional values, when the request is created, then they become empty")
  void normalizesNullOptionalValues() {
    ApiRequest request = new ApiRequest(HttpMethod.GET, "/users", null, "Users API", null, null);

    assertThat(request.name()).isEmpty();
    assertThat(request.moduleName()).isEmpty();
    assertThat(request.basePath()).isEmpty();
  }

  @Test
  @DisplayName("Given a null method, when the request is created, then it fails fast")
  void rejectsNullMethod() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new ApiRequest(null, "/users", "List", "Users API"));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  @DisplayName("Given a null or blank url, when the request is created, then it fails fast")
  void rejectsBlankUrl(String url) {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new ApiRequest(HttpMethod.GET, url, "List", "Users API"));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  @DisplayName(
      "Given a null or blank collection name, when the request is created, then it fails fast")
  void rejectsBlankCollectionName(String collectionName) {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new ApiRequest(HttpMethod.GET, "/users", "List", collectionName));
  }

  @Test
  @DisplayName(
      "Given a request, when its module or base path is replaced, then the other fields survive")
  void copiesWithModuleNameAndBasePath() {
    ApiRequest original =
        new ApiRequest(HttpMethod.POST, "/users", "Create user", "Users API", "api", "/v1");

    ApiRequest withModule = original.withModuleName("web");
    ApiRequest withBasePath = original.withBasePath("/api/v1");

    assertThat(withModule.moduleName()).isEqualTo("web");
    assertThat(withModule.basePath()).isEqualTo("/v1");
    assertThat(withBasePath.moduleName()).isEqualTo("api");
    assertThat(withBasePath.basePath()).isEqualTo("/api/v1");
    assertThat(withBasePath.method()).isEqualTo(HttpMethod.POST);
    assertThat(withBasePath.rawUrl()).isEqualTo("/users");
    assertThat(withBasePath.name()).isEqualTo("Create user");
    assertThat(withBasePath.collectionName()).isEqualTo("Users API");
  }
}
