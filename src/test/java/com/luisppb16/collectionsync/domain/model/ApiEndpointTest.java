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

@DisplayName("ApiEndpoint")
class ApiEndpointTest {

  @Test
  @DisplayName("Given null owner and module, when the endpoint is created, then both become empty")
  void normalizesNullOwnerAndModule() {
    ApiEndpoint endpoint = new ApiEndpoint(HttpMethod.GET, "/users", null, null, null);

    assertThat(endpoint.ownerClass()).isEmpty();
    assertThat(endpoint.moduleName()).isEmpty();
    assertThat(endpoint.psiMethod()).isNull();
  }

  @Test
  @DisplayName("Given a null method, when the endpoint is created, then it fails fast")
  void rejectsNullMethod() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new ApiEndpoint(null, "/users", "UserController", "app", null));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  @DisplayName("Given a null or blank path, when the endpoint is created, then it fails fast")
  void rejectsBlankPath(String path) {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new ApiEndpoint(HttpMethod.GET, path, "UserController", "app", null));
  }
}
