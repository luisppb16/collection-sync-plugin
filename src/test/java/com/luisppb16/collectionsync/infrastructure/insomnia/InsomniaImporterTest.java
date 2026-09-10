/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */
package com.luisppb16.collectionsync.infrastructure.insomnia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.luisppb16.collectionsync.domain.model.CollectionModel;
import com.luisppb16.collectionsync.domain.model.HttpMethod;
import com.luisppb16.collectionsync.domain.model.RequestDescriptor;
import com.luisppb16.collectionsync.infrastructure.io.UnsupportedCollectionFormatException;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InsomniaImporterTest {

  private static CollectionModel collection;

  @BeforeAll
  static void givenAnInsomniaFixture() throws IOException {
    Path fixture = Path.of("src/test/resources/collections/insomnia-v4-sample.yaml");
    collection = new InsomniaImporter().importFile(fixture);
  }

  @Test
  @DisplayName("Given the fixture, when imported, then workspace name, id and environment variables are read")
  void readsWorkspaceMetadata() {
    assertThat(collection.name()).isEqualTo("Users API Workspace");
    assertThat(collection.id()).isEqualTo("wrk_5f4c3b2a111122223333444455556666");
    assertThat(collection.variables()).containsEntry("base_url", "https://api.example.com").containsEntry("token", "secret-token");
  }

  @Test
  @DisplayName("Given request groups, when imported, then the folder tree is preserved")
  void preservesFolderTree() {
    assertThat(collection.root().findChild("Users")).isNotNull();
    assertThat(collection.root().findChild("Users").getRequests()).hasSize(2);
  }

  @Test
  @DisplayName("Given a request resource, when imported, then method, url, params, headers and body are read")
  void readsRequestFields() {
    RequestDescriptor create = collection.root().findChild("Users").getRequests().get(1);

    assertThat(create.name()).isEqualTo("Create user");
    assertThat(create.method()).isEqualTo(HttpMethod.POST);
    assertThat(create.rawUrl()).isEqualTo("{{ _.base_url }}/users");
    assertThat(create.headers().get(0).name()).isEqualTo("X-Auth");
    assertThat(create.body().get("name").asText()).isEqualTo("Ada");
    assertThat(create.normalizedTemplate()).isEqualTo("users");
  }

  @Test
  @DisplayName("Given a request with a query parameter, when imported, then the parameter is read")
  void readsQueryParameters() {
    RequestDescriptor list = collection.root().findChild("Users").getRequests().get(0);

    assertThat(list.query()).hasSize(1);
    assertThat(list.query().get(0).name()).isEqualTo("include");
    assertThat(list.normalizedTemplate()).isEqualTo("users");
  }

  @Test
  @DisplayName("Given non-collection resources like api_spec, when imported, then they are ignored without failing")
  void ignoresUnknownResourceTypes() {
    assertThat(collection.root().findChild("Users")).isNotNull();
  }

  @Test
  @DisplayName("Given a document that is not an Insomnia export, when imported, then it fails fast")
  void rejectsInvalidDocuments() {
    InsomniaImporter importer = new InsomniaImporter();

    assertThatThrownBy(() -> importer.importTree(new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode()))
        .isInstanceOf(UnsupportedCollectionFormatException.class);
  }
}