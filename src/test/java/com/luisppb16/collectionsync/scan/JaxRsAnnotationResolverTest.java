/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */

package com.luisppb16.collectionsync.scan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

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

@DisplayName("JaxRsAnnotationResolver")
class JaxRsAnnotationResolverTest extends PsiTestCase {

  private static final List<String> STUBS =
      List.of(
          "package jakarta.ws.rs; public @interface Path { String value(); }",
          "package jakarta.ws.rs; public @interface GET { }",
          "package jakarta.ws.rs; public @interface POST { }",
          "package jakarta.ws.rs; public @interface PUT { }",
          "package jakarta.ws.rs; public @interface DELETE { }",
          "package jakarta.ws.rs; public @interface PATCH { }",
          "package jakarta.ws.rs; public @interface HEAD { }",
          "package jakarta.ws.rs; public @interface OPTIONS { }",
          "package javax.ws.rs; public @interface Path { String value(); }",
          "package javax.ws.rs; public @interface GET { }",
          "package javax.ws.rs; public @interface POST { }",
          "package com.example; @jakarta.ws.rs.GET public @interface MyGet { }");

  private static final List<String> CONTROLLERS =
      List.of(
          """
          package com.example;

          @jakarta.ws.rs.Path("/orders")
          public class OrderResource {
              @jakarta.ws.rs.GET
              public java.lang.String list() { return null; }

              @jakarta.ws.rs.POST
              @jakarta.ws.rs.Path("/{orderId}")
              public java.lang.String create() { return null; }

              @com.example.MyGet
              @jakarta.ws.rs.Path("/composed")
              public java.lang.String composed() { return null; }

              @com.missing.Verb
              public java.lang.String unresolvable() { return null; }

              public java.lang.String plain() { return null; }
          }

          class ChildResource extends OrderResource {
          }
          """,
          """
          package com.example;

          @javax.ws.rs.Path("/legacy")
          public class LegacyResource {
              @javax.ws.rs.GET
              public java.lang.String list() { return null; }
          }
          """,
          """
          package com.example;

          public class PlainService {
              public void doWork() { }
          }
          """);

  private final JaxRsAnnotationResolver jakartaResolver = new JaxRsAnnotationResolver();

  private final JaxRsAnnotationResolver javaxResolver = new JaxRsAnnotationResolver("javax.ws.rs");

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
      "Given resources in both namespaces, when each resolver checks them, then only its namespace matches")
  void separatesNamespaces() {
    assertThat(read(() -> jakartaResolver.isController(psiClass("com.example.OrderResource"))))
        .isTrue();
    assertThat(read(() -> jakartaResolver.isController(psiClass("com.example.LegacyResource"))))
        .isFalse();
    assertThat(read(() -> javaxResolver.isController(psiClass("com.example.LegacyResource"))))
        .isTrue();
    assertThat(read(() -> javaxResolver.isController(psiClass("com.example.OrderResource"))))
        .isFalse();
  }

  @Test
  @DisplayName(
      "Given a resource without a Path annotation, when it is checked, then it is not a controller")
  void rejectsPlainClasses() {
    assertThat(read(() -> jakartaResolver.isController(psiClass("com.example.PlainService"))))
        .isFalse();
  }

  @Test
  @DisplayName("Given a class-level Path, when base paths are read, then its value is returned")
  void readsBasePaths() {
    assertThat(read(() -> jakartaResolver.basePaths(psiClass("com.example.OrderResource"))))
        .containsExactly("/orders");
    assertThat(read(() -> jakartaResolver.basePaths(psiClass("com.example.PlainService"))))
        .containsExactly("");
  }

  @Test
  @DisplayName(
      "Given fixed verb annotations, when methods are read, then direct and composed verbs are resolved")
  void readsMethods() {
    PsiClass resource = psiClass("com.example.OrderResource");

    assertThat(read(() -> jakartaResolver.methods(method(resource, "list"))))
        .containsExactly(HttpMethod.GET);
    assertThat(read(() -> jakartaResolver.methods(method(resource, "create"))))
        .containsExactly(HttpMethod.POST);
    assertThat(read(() -> jakartaResolver.methods(method(resource, "composed"))))
        .containsExactly(HttpMethod.GET);
    assertThat(read(() -> jakartaResolver.methods(method(resource, "unresolvable")))).isEmpty();
    assertThat(read(() -> jakartaResolver.methods(method(resource, "plain")))).isEmpty();
  }

  @Test
  @DisplayName(
      "Given method Path annotations, when method paths are read, then paths or the empty default are returned")
  void readsMethodPaths() {
    PsiClass resource = psiClass("com.example.OrderResource");

    assertThat(read(() -> jakartaResolver.methodPaths(method(resource, "create"))))
        .containsExactly("/{orderId}");
    assertThat(read(() -> jakartaResolver.methodPaths(method(resource, "list"))))
        .containsExactly("");
    assertThat(read(() -> jakartaResolver.methodPaths(method(resource, "plain")))).isEmpty();
  }

  @Test
  @DisplayName(
      "Given a subclass of a resource, when it is checked, then the inherited Path annotation is honoured")
  void readsBasePathsFromTheHierarchy() {
    assertThat(read(() -> jakartaResolver.isController(psiClass("com.example.ChildResource"))))
        .isTrue();
    assertThat(read(() -> jakartaResolver.basePaths(psiClass("com.example.ChildResource"))))
        .containsExactly("/orders");
  }

  @Test
  @DisplayName("Given a null or blank namespace, when the resolver is built, then it fails fast")
  void validatesNamespace() {
    assertThatIllegalArgumentException().isThrownBy(() -> new JaxRsAnnotationResolver(null));
    assertThatIllegalArgumentException().isThrownBy(() -> new JaxRsAnnotationResolver("   "));
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
