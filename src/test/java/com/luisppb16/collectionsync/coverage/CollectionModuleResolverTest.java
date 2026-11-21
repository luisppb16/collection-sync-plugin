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

  @Test
  @DisplayName("Given no collection path, when resolved, then the collection is global")
  void returnsEmptyWithoutPath() {
    assertThat(CollectionModuleResolver.resolve(null, null, List.of("user-service"))).isEmpty();
    assertThat(CollectionModuleResolver.resolve("   ", null, List.of("user-service"))).isEmpty();
  }

  @Test
  @DisplayName(
      "Given an explicit binding that is not a project module, when resolved, then inference takes over")
  void ignoresUnknownExplicitBinding() {
    String module =
        CollectionModuleResolver.resolve(
            "/repo/user-service/users.json", "ghost-service", List.of("user-service"));

    assertThat(module).isEqualTo("user-service");
  }

  @Test
  @DisplayName(
      "Given two modules matching with the same length, when resolved, then the collection stays global")
  void returnsEmptyWhenTheMatchIsAmbiguous() {
    String module =
        CollectionModuleResolver.resolve(
            "/repo/alpha/bravo/users.json", null, List.of("alpha", "bravo"));

    assertThat(module).isEmpty();
  }

  @Test
  @DisplayName("Given a module name made only of separators, when resolved, then it never matches")
  void ignoresModulesWithoutAlphanumericCharacters() {
    String module =
        CollectionModuleResolver.resolve(
            "/repo/services/users.json", null, List.of("---", "services"));

    assertThat(module).isEqualTo("services");
  }

  @Test
  @DisplayName("Given a Windows-style path, when resolved, then backslashes separate segments too")
  void splitsWindowsPaths() {
    String module =
        CollectionModuleResolver.resolve(
            "C:\\repo\\user-service\\users.json", null, List.of("user-service"));

    assertThat(module).isEqualTo("user-service");
  }
}
