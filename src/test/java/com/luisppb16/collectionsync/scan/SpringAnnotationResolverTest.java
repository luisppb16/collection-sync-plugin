/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */

package com.luisppb16.collectionsync.scan;

import static org.assertj.core.api.Assertions.assertThat;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.luisppb16.collectionsync.domain.model.HttpMethod;
import com.luisppb16.collectionsync.test.PsiTestCase;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SpringAnnotationResolver")
class SpringAnnotationResolverTest extends PsiTestCase {

  private static final String PACKAGE = "package org.springframework.web.bind.annotation;";

  private static final List<String> STUBS =
      List.of(
          PACKAGE
              + " public enum RequestMethod { GET, HEAD, POST, PUT, PATCH, DELETE, OPTIONS, TRACE, BREW }",
          PACKAGE
              + " public @interface RequestMapping { String[] value() default {}; String[] path() default {};"
              + " RequestMethod[] method() default {}; }",
          PACKAGE
              + " public @interface GetMapping { String[] value() default {}; String[] path() default {}; }",
          PACKAGE
              + " public @interface PostMapping { String[] value() default {}; String[] path() default {}; }",
          PACKAGE
              + " public @interface PutMapping { String[] value() default {}; String[] path() default {}; }",
          PACKAGE
              + " public @interface DeleteMapping { String[] value() default {}; String[] path() default {}; }",
          PACKAGE
              + " public @interface PatchMapping { String[] value() default {}; String[] path() default {}; }",
          PACKAGE + " public @interface RestController { }",
          PACKAGE + " public @interface Controller { }",
          PACKAGE + " public @interface ResponseBody { }",
          "package com.example;"
              + " @org.springframework.web.bind.annotation.GetMapping public @interface ListUsers { }",
          "package com.example;"
              + " @org.springframework.web.bind.annotation.RequestMapping("
              + "method = org.springframework.web.bind.annotation.RequestMethod.PUT)"
              + " public @interface MyPut { }");

  private static final List<String> CONTROLLERS =
      List.of(
          """
          package com.example;

          @org.springframework.web.bind.annotation.RestController
          @org.springframework.web.bind.annotation.RequestMapping(value = {"/api", "/v2"}, path = "/api")
          public class RestUsers {
              @org.springframework.web.bind.annotation.PostMapping(path = "/users")
              public java.lang.String create() { return null; }

              @org.springframework.web.bind.annotation.RequestMapping(method = {
                  org.springframework.web.bind.annotation.RequestMethod.GET,
                  org.springframework.web.bind.annotation.RequestMethod.DELETE})
              public java.lang.String multi() { return null; }

              @org.springframework.web.bind.annotation.RequestMapping
              public java.lang.String any() { return null; }

              @org.springframework.web.bind.annotation.RequestMapping(
                  method = org.springframework.web.bind.annotation.RequestMethod.BREW)
              public java.lang.String unsupported() { return null; }

              @com.example.ListUsers
              public java.lang.String composed() { return null; }

              @com.example.MyPut
              public java.lang.String composedPut() { return null; }

              public java.lang.String plain() { return null; }
          }
          """,
          """
          package com.example;

          @org.springframework.web.bind.annotation.RestController
          public class InheritedUsers extends BaseUsers {
          }
          """,
          """
          package com.example;

          @org.springframework.web.bind.annotation.RestController
          public class BaseUsers {
          }
          """,
          """
          package com.example;

          @org.springframework.web.bind.annotation.Controller
          @org.springframework.web.bind.annotation.ResponseBody
          public class AnnotatedUsers {
          }
          """,
          """
          package com.example;

          @org.springframework.web.bind.annotation.Controller
          public class TemplateUsers {
          }
          """,
          """
          package com.example;

          @org.springframework.web.bind.annotation.RequestMapping("/mapped")
          public class MappedUsers {
          }
          """,
          """
          package com.example;

          public class PlainService {
              public void doWork() { }
          }
          """);

  private final SpringAnnotationResolver resolver = new SpringAnnotationResolver();

  private static <T> T read(Supplier<T> action) {
    return ApplicationManager.getApplication().runReadAction((Computable<T>) action::get);
  }

  @BeforeEach
  void addFixtureClasses() {
    STUBS.forEach(this::addClass);
    CONTROLLERS.forEach(this::addClass);
  }

  @Test
  @DisplayName(
      "Given supported controller shapes, when they are checked, then every Spring controller is accepted")
  void acceptsControllers() {
    assertThat(read(() -> resolver.isController(psiClass("com.example.RestUsers")))).isTrue();
    assertThat(read(() -> resolver.isController(psiClass("com.example.InheritedUsers")))).isTrue();
    assertThat(read(() -> resolver.isController(psiClass("com.example.AnnotatedUsers")))).isTrue();
    assertThat(read(() -> resolver.isController(psiClass("com.example.MappedUsers")))).isTrue();
  }

  @Test
  @DisplayName(
      "Given a plain class or a Controller without ResponseBody, when checked, then it is not a controller")
  void rejectsNonControllers() {
    assertThat(read(() -> resolver.isController(psiClass("com.example.PlainService")))).isFalse();
    assertThat(read(() -> resolver.isController(psiClass("com.example.TemplateUsers")))).isFalse();
  }

  @Test
  @DisplayName(
      "Given class-level mappings, when base paths are read, then values and paths are merged and distinct")
  void readsBasePaths() {
    assertThat(read(() -> resolver.basePaths(psiClass("com.example.RestUsers"))))
        .containsExactlyInAnyOrder("/api", "/v2");
    assertThat(read(() -> resolver.basePaths(psiClass("com.example.PlainService"))))
        .containsExactly("");
  }

  @Test
  @DisplayName(
      "Given mapping annotations, when methods are read, then verb annotations and RequestMapping methods are resolved")
  void readsMethods() {
    PsiClass controller = psiClass("com.example.RestUsers");

    assertThat(read(() -> resolver.methods(method(controller, "create"))))
        .containsExactly(HttpMethod.POST);
    assertThat(read(() -> resolver.methods(method(controller, "multi"))))
        .containsExactly(HttpMethod.GET, HttpMethod.DELETE);
    assertThat(read(() -> resolver.methods(method(controller, "any"))))
        .containsExactly(HttpMethod.ANY);
    assertThat(read(() -> resolver.methods(method(controller, "composed"))))
        .containsExactly(HttpMethod.GET);
    assertThat(read(() -> resolver.methods(method(controller, "composedPut"))))
        .containsExactly(HttpMethod.PUT);
    assertThat(read(() -> resolver.methods(method(controller, "unsupported"))))
        .containsExactly(HttpMethod.ANY);
    assertThat(read(() -> resolver.methods(method(controller, "plain")))).isEmpty();
  }

  @Test
  @DisplayName(
      "Given mapping annotations, when method paths are read, then value and path attributes are returned")
  void readsMethodPaths() {
    PsiClass controller = psiClass("com.example.RestUsers");

    assertThat(read(() -> resolver.methodPaths(method(controller, "create"))))
        .containsExactly("/users");
    assertThat(read(() -> resolver.methodPaths(method(controller, "multi")))).containsExactly("");
    assertThat(read(() -> resolver.methodPaths(method(controller, "plain")))).isEmpty();
  }

  private PsiClass psiClass(String qualifiedName) {
    return read(() ->
        JavaPsiFacade.getInstance(getFixture().getProject())
            .findClass(qualifiedName, GlobalSearchScope.projectScope(getFixture().getProject())));
  }

  private PsiMethod method(PsiClass owner, String name) {
    return read(() -> {
      PsiMethod[] methods = owner.findMethodsByName(name, false);
      assertThat(methods).as("Fixture method %s", name).isNotEmpty();
      return methods[0];
    });
  }

  private void addClass(String source) {
    getFixture().addClass(source);
  }
}
