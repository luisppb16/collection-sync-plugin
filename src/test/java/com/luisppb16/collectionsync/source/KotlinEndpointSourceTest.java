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
import com.luisppb16.collectionsync.test.PsiTestCase;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("KotlinEndpointSource")
class KotlinEndpointSourceTest extends PsiTestCase {

  private static final String CONTROLLERS =
      """
      package com.example

      @RestController
      @RequestMapping("/api")
      class UserController {
          @GetMapping("/users/{id}")
          fun getUser(id: Long): String = ""

          @PostMapping("/users")
          fun createUser(): String = ""
      }

      @RestController
      class OuterController {
          @GetMapping("/outer")
          fun list(): String = ""

          @RestController
          class NestedController {
              @PostMapping("/nested")
              fun create(): String = ""
          }
      }

      @Controller
      class AnyController {
          @RequestMapping("/any")
          fun handle(): String = ""
      }

      @Path("/kt")
      class PathOnlyResource {
          @GET("/x")
          fun read(): String = ""
      }

      class PlainService {
          fun doWork() {}
      }
      """;

  private final KotlinEndpointSource source = new KotlinEndpointSource();

  @BeforeEach
  void addFixtureClasses() {
    getFixture().addFileToProject("Controllers.kt", CONTROLLERS);
  }

  @Test
  @DisplayName(
      "Given Kotlin controllers, when endpoints are collected, then the annotated routes are extracted")
  void collectsEndpointsFromKotlinControllers() {
    List<ApiEndpoint> endpoints = source.collect(getFixture().getProject());

    assertThat(endpoints)
        .extracting(ApiEndpoint::method, ApiEndpoint::pathTemplate, ApiEndpoint::ownerClass)
        .containsExactlyInAnyOrder(
            tuple(HttpMethod.GET, "/api/users/{id}", "com.example.UserController"),
            tuple(HttpMethod.POST, "/api/users", "com.example.UserController"),
            tuple(HttpMethod.GET, "/outer", "com.example.OuterController"),
            tuple(HttpMethod.POST, "/nested", "com.example.OuterController.NestedController"),
            tuple(HttpMethod.ANY, "/any", "com.example.AnyController"),
            tuple(HttpMethod.GET, "/kt/x", "com.example.PathOnlyResource"));
  }

  @Test
  @DisplayName(
      "Given non-controller Kotlin classes, when endpoints are collected, then they contribute nothing")
  void ignoresClassesWithoutControllerAnnotations() {
    List<ApiEndpoint> endpoints = source.collect(getFixture().getProject());

    assertThat(endpoints)
        .noneMatch(endpoint -> endpoint.ownerClass().equals("com.example.PlainService"));
  }
}
