/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */
package com.luisppb16.collectionsync.domain.service;

import static com.luisppb16.collectionsync.domain.service.TestFixtures.request;
import static org.assertj.core.api.Assertions.assertThat;

import com.luisppb16.collectionsync.domain.model.CollectionFolder;
import com.luisppb16.collectionsync.domain.model.CollectionModel;
import com.luisppb16.collectionsync.domain.model.HttpMethod;
import com.luisppb16.collectionsync.domain.model.RequestDescriptor;
import com.luisppb16.collectionsync.domain.model.RequestSource;
import com.luisppb16.collectionsync.domain.service.MergePlanner.MergeAction;
import com.luisppb16.collectionsync.domain.service.MergePlanner.MergeEntry;
import com.luisppb16.collectionsync.domain.service.MergePlanner.MergePlan;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MergePlannerTest {

  @Test
  @DisplayName("Given a collection with an existing folder, when planned, then the request targets that folder")
  void plansAddIntoExistingFolder() {
    CollectionFolder usersFolder = new CollectionFolder("UserController");
    CollectionFolder root = new CollectionFolder("root");
    root.getChildren().add(usersFolder);
    CollectionModel collection = model(root);
    RequestDescriptor generated = new RequestDescriptor(
        "UserController · GET /users", HttpMethod.GET, "{{baseUrl}}/users", "/users", List.of(), List.of(), null,
        "UserController", RequestSource.GENERATED, null);

    MergePlan plan = MergePlanner.plan(collection, List.of(generated));

    assertThat(plan.entries()).hasSize(1);
    MergeEntry entry = plan.entries().get(0);
    assertThat(entry.action()).isEqualTo(MergeAction.ADD);
    assertThat(entry.targetFolderPath()).isEqualTo("UserController");
    assertThat(entry.newFolder()).isFalse();
  }

  @Test
  @DisplayName("Given a request already present in the collection, when planned, then it is skipped as duplicate")
  void skipsDuplicates() {
    CollectionFolder root = new CollectionFolder("root");
    CollectionModel collection = model(root);
    RequestDescriptor existing = new RequestDescriptor(
        "List users", HttpMethod.GET, "{{baseUrl}}/users", "/users", List.of(), List.of(), null, "Users",
        RequestSource.POSTMAN, null);
    root.findOrCreateChild("Users").addRequest(existing);
    RequestDescriptor generated = new RequestDescriptor(
        "UserController · GET /users", HttpMethod.GET, "{{baseUrl}}/users", "/users", List.of(), List.of(), null,
        "UserController", RequestSource.GENERATED, null);

    MergePlan plan = MergePlanner.plan(collection, List.of(generated));

    assertThat(plan.entries()).hasSize(1);
    assertThat(plan.entries().get(0).action()).isEqualTo(MergeAction.SKIP_DUPLICATE);
  }

  @Test
  @DisplayName("Given a plan with an ADD entry, when applied, then the request lands in the right folder")
  void appliesAddEntries() {
    CollectionFolder root = new CollectionFolder("root");
    CollectionModel collection = model(root);
    RequestDescriptor generated = new RequestDescriptor(
        "UserController · GET /users", HttpMethod.GET, "{{baseUrl}}/users", "/users", List.of(), List.of(), null,
        "UserController", RequestSource.GENERATED, null);
    MergePlan plan = MergePlanner.plan(collection, List.of(generated));

    MergePlanner.apply(collection, plan);

    CollectionFolder target = root.findChild("UserController");
    assertThat(target).isNotNull();
    assertThat(target.getRequests()).hasSize(1);
  }

  @Test
  @DisplayName("Given a duplicate request, when applied, then nothing is added")
  void doesNotApplySkippedEntries() {
    CollectionFolder root = new CollectionFolder("root");
    CollectionModel collection = model(root);
    RequestDescriptor existing = new RequestDescriptor(
        "List users", HttpMethod.GET, "{{baseUrl}}/users", "/users", List.of(), List.of(), null, "Users",
        RequestSource.POSTMAN, null);
    root.findOrCreateChild("Users").addRequest(existing);
    RequestDescriptor generated = new RequestDescriptor(
        "UserController · GET /users", HttpMethod.GET, "{{baseUrl}}/users", "/users", List.of(), List.of(), null,
        "UserController", RequestSource.GENERATED, null);
    MergePlan plan = MergePlanner.plan(collection, List.of(generated));

    MergePlanner.apply(collection, plan);

    assertThat(root.getChildren()).hasSize(1);
    assertThat(root.getChildren().get(0).getRequests()).hasSize(1);
  }

  private static CollectionModel model(CollectionFolder root) {
    Map<String, String> variables = new HashMap<>();
    variables.put("baseUrl", "https://api.example.com");
    return new CollectionModel("col-1", "My API", root, variables, "POSTMAN");
  }
}