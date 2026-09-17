/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */

package com.luisppb16.collectionsync.scan;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiArrayInitializerMemberValue;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiEnumConstant;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiReferenceExpression;
import com.luisppb16.collectionsync.domain.model.HttpMethod;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * {@link EndpointAnnotationResolver} for Spring MVC controllers.
 *
 * <p>All lookups are done by annotation qualified name on the PSI modifier list, so the resolver
 * works even when the Spring annotations are declared as stubs and cannot be resolved to their
 * declaring classes.
 *
 * <ul>
 *   <li><b>isController:</b> the class (or any class of its superclass hierarchy) carries
 *       {@code @RestController}, or {@code @Controller} together with {@code @ResponseBody}, or a
 *       class-level {@code @RequestMapping}.
 *   <li><b>basePath:</b> {@code value}/{@code path} of the class-level {@code @RequestMapping}
 *       (first element when declared as an array), or {@code ""} when absent.
 *   <li><b>methods:</b> {@code @GetMapping}, {@code @PostMapping}, {@code @PutMapping},
 *       {@code @DeleteMapping} and {@code @PatchMapping} each declare one fixed HTTP method;
 *       {@code @RequestMapping} declares the methods of its {@code method} attribute. A
 *       {@code @RequestMapping} without a concrete {@code method} attribute yields an empty list,
 *       so the endpoint is omitted (no HTTP method is known).
 *   <li><b>methodPath:</b> {@code value}/{@code path} of the mapping annotation (first element when
 *       declared as an array), or {@code ""} when absent.
 * </ul>
 *
 * <p>{@code RequestMethod} constants that do not map to a supported {@link HttpMethod} (e.g. {@code
 * TRACE}) are skipped instead of failing the whole scan.
 */
public final class SpringAnnotationResolver implements EndpointAnnotationResolver {

  private static final String REQUEST_MAPPING =
      "org.springframework.web.bind.annotation.RequestMapping";
  private static final String REST_CONTROLLER =
      "org.springframework.web.bind.annotation.RestController";
  private static final String CONTROLLER = "org.springframework.web.bind.annotation.Controller";
  private static final String RESPONSE_BODY =
      "org.springframework.web.bind.annotation.ResponseBody";
  private static final String VALUE = "value";
  private static final String PATH = "path";
  private static final String METHOD = "method";

  private static final List<FixedMapping> FIXED_MAPPINGS =
      List.of(
          new FixedMapping("org.springframework.web.bind.annotation.GetMapping", HttpMethod.GET),
          new FixedMapping("org.springframework.web.bind.annotation.PostMapping", HttpMethod.POST),
          new FixedMapping("org.springframework.web.bind.annotation.PutMapping", HttpMethod.PUT),
          new FixedMapping(
              "org.springframework.web.bind.annotation.DeleteMapping", HttpMethod.DELETE),
          new FixedMapping(
              "org.springframework.web.bind.annotation.PatchMapping", HttpMethod.PATCH));

  private static boolean isControllerClass(PsiClass psiClass) {
    return findAnnotation(psiClass, REST_CONTROLLER).isPresent()
        || (findAnnotation(psiClass, CONTROLLER).isPresent()
            && findAnnotation(psiClass, RESPONSE_BODY).isPresent())
        || findAnnotation(psiClass, REQUEST_MAPPING).isPresent();
  }

  /**
   * Walks the class hierarchy from the class itself up through its superclasses. A {@code
   * takeWhile} over a seen-set keeps the stream finite even for cyclic hierarchies.
   */
  private static List<PsiClass> hierarchyOf(PsiClass psiClass) {
    Set<PsiClass> seen = Collections.newSetFromMap(new IdentityHashMap<>());
    return Stream.iterate(psiClass, Objects::nonNull, PsiClass::getSuperClass)
        .takeWhile(seen::add)
        .toList();
  }

  private static List<HttpMethod> requestMappingMethods(PsiAnnotation requestMapping) {
    return requestMethodValues(requestMapping.findAttributeValue(METHOD));
  }

  private static List<HttpMethod> requestMethodValues(
      @Nullable PsiAnnotationMemberValue methodValue) {
    if (methodValue instanceof PsiArrayInitializerMemberValue array) {
      return Arrays.stream(array.getInitializers())
          .map(SpringAnnotationResolver::requestMethodValues)
          .flatMap(List::stream)
          .toList();
    }
    return Stream.ofNullable(enumConstantName(methodValue))
        .map(SpringAnnotationResolver::httpMethod)
        .flatMap(Optional::stream)
        .toList();
  }

  private static String enumConstantName(@Nullable PsiAnnotationMemberValue methodValue) {
    if (methodValue instanceof PsiReferenceExpression reference) {
      return reference.getReferenceName();
    }
    if (methodValue instanceof PsiEnumConstant enumConstant) {
      return enumConstant.getName();
    }
    return null;
  }

  private static Optional<HttpMethod> httpMethod(String requestMethodName) {
    if (requestMethodName == null) {
      return Optional.empty();
    }
    String normalizedName = requestMethodName.strip().toUpperCase(Locale.ROOT);
    return Arrays.stream(HttpMethod.values())
        .filter(method -> method.name().equals(normalizedName))
        .findFirst();
  }

  private static String mappingPath(PsiAnnotation mapping) {
    String value = firstStringValue(mapping, VALUE);
    return value.isEmpty() ? firstStringValue(mapping, PATH) : value;
  }

  /**
   * Reads an annotation member as a string, taking the first element when the member is declared as
   * an array of any length ({@code @RequestMapping({"/a", "/b"})} yields {@code "/a"}).
   */
  private static String firstStringValue(PsiAnnotation annotation, String attribute) {
    PsiAnnotationMemberValue value = annotation.findAttributeValue(attribute);
    if (value instanceof PsiArrayInitializerMemberValue array
        && array.getInitializers().length > 0) {
      value = array.getInitializers()[0];
    }
    if (value instanceof PsiLiteralExpression literal
        && literal.getValue() instanceof String text) {
      return text.strip();
    }
    return "";
  }

  private static Optional<PsiAnnotation> findAnnotation(
      PsiModifierListOwner owner, String... qualifiedNames) {
    return Arrays.stream(qualifiedNames)
        .map(qualifiedName -> findAnnotation(owner, qualifiedName))
        .flatMap(Optional::stream)
        .findFirst();
  }

  private static Optional<PsiAnnotation> findAnnotation(
      PsiModifierListOwner owner, String qualifiedName) {
    return Stream.ofNullable(owner.getModifierList())
        .map(modifierList -> modifierList.findAnnotation(qualifiedName))
        .filter(Objects::nonNull)
        .findFirst();
  }

  @Override
  public boolean isController(@NotNull PsiClass psiClass) {
    Objects.requireNonNull(psiClass);
    return hierarchyOf(psiClass).stream().anyMatch(SpringAnnotationResolver::isControllerClass);
  }

  @Override
  public @NotNull String basePath(@NotNull PsiClass psiClass) {
    Objects.requireNonNull(psiClass);
    return findAnnotation(psiClass, REQUEST_MAPPING)
        .map(SpringAnnotationResolver::mappingPath)
        .orElse("");
  }

  @Override
  public @NotNull List<HttpMethod> methods(@NotNull PsiMethod psiMethod) {
    Objects.requireNonNull(psiMethod);
    List<HttpMethod> fixedMethods =
        FIXED_MAPPINGS.stream()
            .filter(mapping -> findAnnotation(psiMethod, mapping.qualifiedName()).isPresent())
            .map(FixedMapping::httpMethod)
            .toList();
    if (!fixedMethods.isEmpty()) {
      return fixedMethods;
    }
    return findAnnotation(psiMethod, REQUEST_MAPPING)
        .map(SpringAnnotationResolver::requestMappingMethods)
        .orElse(List.of());
  }

  @Override
  public @NotNull String methodPath(@NotNull PsiMethod psiMethod) {
    Objects.requireNonNull(psiMethod);
    return findAnnotation(
            psiMethod,
            REQUEST_MAPPING,
            "org.springframework.web.bind.annotation.GetMapping",
            "org.springframework.web.bind.annotation.PostMapping",
            "org.springframework.web.bind.annotation.PutMapping",
            "org.springframework.web.bind.annotation.DeleteMapping",
            "org.springframework.web.bind.annotation.PatchMapping")
        .map(SpringAnnotationResolver::mappingPath)
        .orElse("");
  }

  private record FixedMapping(String qualifiedName, HttpMethod httpMethod) {}
}
