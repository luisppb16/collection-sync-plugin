/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */

package com.luisppb16.collectionsync.coverage;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("CollectionModuleResolver")
class CollectionModuleResolverTest {

  @Test
  @DisplayName("Given an explicit binding, when the module is resolved, then the binding wins")
  void explicitBindingWins() {
    String module =
        CollectionModuleResolver.resolve(
            "/repo/services/user-service/users.json",
            "billing-service",
            List.of("user-service", "billing-service"));

    assertThat(module).isEqualTo("billing-service");
  }

  @Test
  @DisplayName(
      "Given a collection path containing several module names, when resolved, then the longest name wins")
  void infersLongestModuleName() {
    String module =
        CollectionModuleResolver.resolve(
            "/repo/services/user-service/collections/users.json",
            "",
            List.of("users", "user-service"));

    assertThat(module).isEqualTo("user-service");
  }

  @Test
  @DisplayName("Given a path without a module name, when resolved, then the collection is global")
  void returnsEmptyWhenNoModuleMatches() {
    String module =
        CollectionModuleResolver.resolve(
            "/repo/collections/api.postman_collection.json", null, List.of("user-service"));

    assertThat(module).isEmpty();
  }
}
