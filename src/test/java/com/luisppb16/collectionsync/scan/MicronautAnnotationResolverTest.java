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

@DisplayName("MicronautAnnotationResolver")
class MicronautAnnotationResolverTest extends PsiTestCase {

  private static final String PACKAGE = "package io.micronaut.http.annotation;";

  private static final List<String> STUBS =
      List.of(
          PACKAGE + " public @interface Controller { String[] value() default {}; }",
          PACKAGE + " public @interface Get { String[] value() default {}; }",
          PACKAGE + " public @interface Post { String[] value() default {}; }",
          "package com.example; @io.micronaut.http.annotation.Get public @interface Fetch { }");

  private static final List<String> CONTROLLERS =
      List.of(
          """
          package com.example;

          @io.micronaut.http.annotation.Controller("/pet")
          public class PetController {
              @io.micronaut.http.annotation.Get("/{id}")
              public java.lang.String get() { return null; }

              @io.micronaut.http.annotation.Post
              public java.lang.String create() { return null; }

              @com.example.Fetch("/composed")
              public java.lang.String composed() { return null; }

              public java.lang.String plain() { return null; }
          }
          """,
          """
          package com.example;

          public class PlainService {
              public void doWork() { }
          }
          """);

  private final MicronautAnnotationResolver resolver = new MicronautAnnotationResolver();

  private static <T> T read(Supplier<T> action) {
    return ApplicationManager.getApplication().runReadAction((Computable<T>) action::get);
  }

  @BeforeEach
  void addFixtureClasses() {
    STUBS.forEach(this::addClass);
    CONTROLLERS.forEach(this::addClass);
  }

  @Test
  @DisplayName("Given a Controller annotation, when the class is checked, then it is a controller")
  void acceptsControllers() {
    assertThat(read(() -> resolver.isController(psiClass("com.example.PetController")))).isTrue();
    assertThat(read(() -> resolver.isController(psiClass("com.example.PlainService")))).isFalse();
  }

  @Test
  @DisplayName("Given a Controller value, when base paths are read, then it is returned")
  void readsBasePaths() {
    assertThat(read(() -> resolver.basePaths(psiClass("com.example.PetController"))))
        .containsExactly("/pet");
    assertThat(read(() -> resolver.basePaths(psiClass("com.example.PlainService"))))
        .containsExactly("");
  }

  @Test
  @DisplayName(
      "Given Micronaut verb annotations, when methods are read, then direct and composed verbs are resolved")
  void readsMethods() {
    PsiClass controller = psiClass("com.example.PetController");

    assertThat(read(() -> resolver.methods(method(controller, "get"))))
        .containsExactly(HttpMethod.GET);
    assertThat(read(() -> resolver.methods(method(controller, "create"))))
        .containsExactly(HttpMethod.POST);
    assertThat(read(() -> resolver.methods(method(controller, "composed"))))
        .containsExactly(HttpMethod.GET);
    assertThat(read(() -> resolver.methods(method(controller, "plain")))).isEmpty();
  }

  @Test
  @DisplayName(
      "Given Micronaut verb annotations, when method paths are read, then paths or the empty default are returned")
  void readsMethodPaths() {
    PsiClass controller = psiClass("com.example.PetController");

    assertThat(read(() -> resolver.methodPaths(method(controller, "get"))))
        .containsExactly("/{id}");
    assertThat(read(() -> resolver.methodPaths(method(controller, "create")))).containsExactly("");
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
