/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */
package com.luisppb16.collectionsync.infrastructure.postman;

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

class PostmanImporterTest {

  private static CollectionModel collection;

  @BeforeAll
  static void givenAPostmanCollectionFixture() throws IOException {
    Path fixture = Path.of("src/test/resources/collections/postman-v21-sample.json");
    collection = new PostmanImporter().importFile(fixture);
  }

  @Test
  @DisplayName("Given the fixture, when imported, then the collection name, id and variables are read")
  void readsCollectionMetadata() {
    assertThat(collection.name()).isEqualTo("Users API");
    assertThat(collection.id()).isEqualTo("9b7a3d1c-0000-4000-8000-aaaaaaaaaaaa");
    assertThat(collection.variables()).containsEntry("baseUrl", "https://api.example.com").containsEntry("token", "secret-token");
  }

  @Test
  @DisplayName("Given nested folders, when imported, then the folder tree is preserved")
  void preservesFolderTree() {
    assertThat(collection.root().findChild("Users")).isNotNull();
    assertThat(collection.root().findChild("Users").getRequests()).hasSize(3);
  }

  @Test
  @DisplayName("Given a request with a url object, when imported, then method, url, query and headers are read")
  void readsRequestFields() {
    RequestDescriptor create = collection.root().findChild("Users").getRequests().get(2);

    assertThat(create.name()).isEqualTo("Create user");
    assertThat(create.method()).isEqualTo(HttpMethod.POST);
    assertThat(create.rawUrl()).isEqualTo("{{baseUrl}}/users");
    assertThat(create.query()).isEmpty();
    assertThat(create.headers()).hasSize(1);
    assertThat(create.headers().get(0).name()).isEqualTo("X-Auth");
    assertThat(create.body().get("name").asText()).isEqualTo("Ada");
  }

  @Test
  @DisplayName("Given a request url, when imported, then the normalized template is computed")
  void computesNormalizedTemplate() {
    RequestDescriptor getUser = collection.root().findChild("Users").getRequests().get(1);

    assertThat(getUser.normalizedTemplate()).isEqualTo("users/{id}");
  }

  @Test
  @DisplayName("Given unknown fields like event scripts, when imported, then they are kept for passthrough")
  void keepsRawItemForPassthrough() {
    RequestDescriptor listUsers = collection.root().findChild("Users").getRequests().get(0);

    assertThat(listUsers.rawExtra().path("event")).isNotNull();
  }

  @Test
  @DisplayName("Given a file without info.name, when imported, then it fails fast")
  void rejectsInvalidCollections() {
    PostmanImporter importer = new PostmanImporter();

    assertThatThrownBy(() -> importer.importTree(new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode()))
        .isInstanceOf(UnsupportedCollectionFormatException.class);
  }
}