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
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.search.GlobalSearchScope;
import com.luisppb16.collectionsync.test.PsiTestCase;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("EndpointAnnotationResolvers")
class EndpointAnnotationResolversTest extends PsiTestCase {

  private static final String SPRING = "org.springframework.web.bind.annotation.";
  private static final String SPRING_PACKAGE =
      "package " + SPRING.substring(0, SPRING.length() - 1) + ";";
  private static final String MAPPING = SPRING + "RequestMapping";
  private static final String REST_CONTROLLER = SPRING + "RestController";
  private static final String GET_MAPPING = SPRING + "GetMapping";

  private static final List<String> STUBS =
      List.of(
          SPRING_PACKAGE + " public @interface RequestMapping { String[] value() default {}; }",
          SPRING_PACKAGE + " public @interface GetMapping { String[] value() default {}; }",
          SPRING_PACKAGE + " public @interface RestController { }",
          "package com.example; @" + MAPPING + " public @interface ComposedMapping { }");

  private static final List<String> FIXTURES =
      List.of(
          """
          package com.example;

          @org.springframework.web.bind.annotation.RestController
          public class AnnotatedOwner {

              @org.springframework.web.bind.annotation.GetMapping("/users")
              public java.lang.String single() { return null; }

              @org.springframework.web.bind.annotation.GetMapping({"/a", " /b "})
              public java.lang.String array() { return null; }

              @org.springframework.web.bind.annotation.GetMapping
              public java.lang.String withoutValue() { return null; }

              @org.springframework.web.bind.annotation.GetMapping(value = 1 + 1)
              public java.lang.String nonConstant() { return null; }
          }
          """,
          """
          package com.example;

          @com.example.ComposedMapping
          public class ComposedOwner {
          }
          """,
          """
          package com.example;

          public class InheritedOwner extends AnnotatedOwner {
          }
          """,
          """
          package com.example;

          @com.missing.Unknown
          public class UnresolvableOwner {
          }
          """);

  private static <T> T read(Supplier<T> action) {
    return ApplicationManager.getApplication().runReadAction((Computable<T>) action::get);
  }

  @BeforeEach
  void addFixtureClasses() {
    STUBS.forEach(this::addClass);
    FIXTURES.forEach(this::addClass);
  }

  @Test
  @DisplayName(
      "Given a single string attribute, when strings are read, then it is stripped and returned")
  void readsSingleString() {
    assertThat(
            read(() ->
                EndpointAnnotationResolvers.allStrings(
                    annotation("AnnotatedOwner", "single", GET_MAPPING), "value")))
        .containsExactly("/users");
  }

  @Test
  @DisplayName("Given an array attribute, when strings are read, then every element is stripped")
  void readsStringArrays() {
    assertThat(
            read(() ->
                EndpointAnnotationResolvers.allStrings(
                    annotation("AnnotatedOwner", "array", GET_MAPPING), "value")))
        .containsExactly("/a", "/b");
  }

  @Test
  @DisplayName(
      "Given a missing or non-constant attribute, when strings are read, then the list is empty")
  void ignoresMissingAndNonConstantAttributes() {
    assertThat(
            read(() ->
                EndpointAnnotationResolvers.allStrings(
                    annotation("AnnotatedOwner", "withoutValue", GET_MAPPING), "value")))
        .isEmpty();
    assertThat(
            read(() ->
                EndpointAnnotationResolvers.allStrings(
                    annotation("AnnotatedOwner", "nonConstant", GET_MAPPING), "value")))
        .isEmpty();
  }

  @Test
  @DisplayName(
      "Given an owner, when annotations are matched, then only the requested names are returned")
  void filtersAnnotationsByQualifiedName() {
    assertThat(
            read(() ->
                EndpointAnnotationResolvers.annotationsMatching(
                        psiClass("com.example.AnnotatedOwner"), List.of(REST_CONTROLLER))
                    .stream()
                    .map(PsiAnnotation::getQualifiedName)
                    .toList()))
        .containsExactly(REST_CONTROLLER);
    assertThat(
            read(() ->
                EndpointAnnotationResolvers.annotationsMatching(
                    psiClass("com.example.AnnotatedOwner"), List.of(MAPPING))))
        .isEmpty();
  }

  @Test
  @DisplayName(
      "Given a superclass annotation, when the hierarchy is searched, then the inherited annotation is found")
  void findsAnnotationsInHierarchy() {
    assertThat(
            read(() ->
                EndpointAnnotationResolvers.annotationsMatchingInHierarchy(
                    psiClass("com.example.InheritedOwner"), List.of(REST_CONTROLLER))))
        .isNotEmpty();
    assertThat(
            read(() ->
                EndpointAnnotationResolvers.annotationsMatchingInHierarchy(
                    psiClass("com.example.UnresolvableOwner"), List.of(REST_CONTROLLER))))
        .isEmpty();
  }

  @Test
  @DisplayName("Given direct and composed annotations, when matched, then both qualify")
  void matchesDirectAndMetaAnnotations() {
    assertThat(
            read(() ->
                EndpointAnnotationResolvers.matches(
                    annotation("AnnotatedOwner", null, REST_CONTROLLER), List.of(REST_CONTROLLER))))
        .isTrue();
    assertThat(
            read(() ->
                EndpointAnnotationResolvers.matches(
                    annotation("ComposedOwner", null, "com.example.ComposedMapping"),
                    List.of(MAPPING))))
        .isTrue();
  }

  @Test
  @DisplayName(
      "Given an annotation that cannot be resolved, when matched, then it does not qualify")
  void rejectsUnresolvableAnnotations() {
    assertThat(
            read(() ->
                EndpointAnnotationResolvers.matches(
                    annotation("UnresolvableOwner", null, "com.missing.Unknown"),
                    List.of(MAPPING))))
        .isFalse();
  }

  private PsiClass psiClass(String qualifiedName) {
    return read(() ->
        JavaPsiFacade.getInstance(getFixture().getProject())
            .findClass(qualifiedName, GlobalSearchScope.projectScope(getFixture().getProject())));
  }

  private PsiAnnotation annotation(String ownerName, String methodName, String qualifiedName) {
    PsiModifierListOwner owner =
        methodName == null ? psiClass("com.example." + ownerName) : method(ownerName, methodName);
    return read(() ->
        Arrays.stream(owner.getModifierList().getAnnotations())
            .filter(annotation -> qualifiedName.equals(annotation.getQualifiedName()))
            .findFirst()
            .orElseThrow(
                () -> new AssertionError("Fixture annotation " + qualifiedName + " must exist")));
  }

  private PsiMethod method(String ownerName, String methodName) {
    return read(() -> {
      PsiMethod[] methods =
          psiClass("com.example." + ownerName).findMethodsByName(methodName, false);
      assertThat(methods).as("Fixture method %s", methodName).isNotEmpty();
      return methods[0];
    });
  }

  private void addClass(String source) {
    getFixture().addClass(source);
  }
}
