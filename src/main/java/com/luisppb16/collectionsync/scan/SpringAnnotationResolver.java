/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */

package com.luisppb16.collectionsync.scan;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.MetaAnnotationUtil;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiArrayInitializerMemberValue;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiEnumConstant;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiReferenceExpression;
import com.luisppb16.collectionsync.domain.model.HttpMethod;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * {@link EndpointAnnotationResolver} for Spring MVC controllers.
 *
 * <p>All lookups are done by annotation qualified name on the PSI modifier list, so the resolver
 * works even when the Spring annotations are declared as stubs and cannot be resolved to their
 * declaring classes. Composed annotations whose annotation type is meta-annotated with a mapping
 * annotation are accepted as well.
 *
 * <ul>
 *   <li><b>isController:</b> the class (or any class of its superclass hierarchy) carries
 *       {@code @RestController}, or {@code @Controller} together with {@code @ResponseBody}, or a
 *       class-level {@code @RequestMapping}.
 *   <li><b>basePaths:</b> every {@code value}/{@code path} of the class-level
 *       {@code @RequestMapping}.
 *   <li><b>methods:</b> {@code @GetMapping}, {@code @PostMapping}, {@code @PutMapping},
 *       {@code @DeleteMapping} and {@code @PatchMapping} each declare one fixed HTTP method;
 *       {@code @RequestMapping} declares the methods of its {@code method} attribute, or {@link
 *       HttpMethod#ANY} when it declares none.
 *   <li><b>methodPaths:</b> every {@code value}/{@code path} of the mapping annotation.
 * </ul>
 *
 * <p>{@code RequestMethod} constants that do not map to a supported {@link HttpMethod} are skipped
 * instead of failing the whole scan.
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

  private static final List<FixedVerb> FIXED_MAPPINGS =
      List.of(
          new FixedVerb("org.springframework.web.bind.annotation.GetMapping", HttpMethod.GET),
          new FixedVerb("org.springframework.web.bind.annotation.PostMapping", HttpMethod.POST),
          new FixedVerb("org.springframework.web.bind.annotation.PutMapping", HttpMethod.PUT),
          new FixedVerb("org.springframework.web.bind.annotation.DeleteMapping", HttpMethod.DELETE),
          new FixedVerb("org.springframework.web.bind.annotation.PatchMapping", HttpMethod.PATCH));

  private static final List<String> MAPPING_NAMES =
      Stream.concat(
              Stream.of(REQUEST_MAPPING), FIXED_MAPPINGS.stream().map(FixedVerb::qualifiedName))
          .toList();

  private static boolean isControllerClass(PsiClass psiClass) {
    return hasAnnotationInHierarchy(psiClass, REST_CONTROLLER)
        || (hasAnnotationInHierarchy(psiClass, CONTROLLER)
            && hasAnnotationInHierarchy(psiClass, RESPONSE_BODY))
        || hasAnnotationInHierarchy(psiClass, REQUEST_MAPPING);
  }

  private static boolean hasAnnotationInHierarchy(PsiClass psiClass, String annotation) {
    return !EndpointAnnotationResolvers.annotationsMatchingInHierarchy(
            psiClass, List.of(annotation))
        .isEmpty();
  }

  private static List<PsiAnnotation> mappingAnnotations(PsiModifierListOwner owner) {
    return EndpointAnnotationResolvers.annotationsMatching(owner, MAPPING_NAMES);
  }

  private static List<String> mappingPaths(PsiAnnotation annotation) {
    List<String> paths = new ArrayList<>();
    paths.addAll(EndpointAnnotationResolvers.allStrings(annotation, VALUE));
    paths.addAll(EndpointAnnotationResolvers.allStrings(annotation, PATH));
    return paths.isEmpty() ? List.of("") : paths.stream().distinct().toList();
  }

  private static List<HttpMethod> methodsOf(PsiAnnotation annotation) {
    String qualifiedName = annotation.getQualifiedName();
    for (FixedVerb mapping : FIXED_MAPPINGS) {
      if (mapping.qualifiedName().equals(qualifiedName)) {
        return List.of(mapping.method());
      }
    }
    PsiClass annotationType = annotation.resolveAnnotationType();
    if (annotationType != null) {
      for (FixedVerb mapping : FIXED_MAPPINGS) {
        if (MetaAnnotationUtil.isMetaAnnotated(annotationType, List.of(mapping.qualifiedName()))) {
          return List.of(mapping.method());
        }
      }
    }
    List<HttpMethod> methods = requestMappingMethods(annotation, annotationType);
    return methods.isEmpty() ? List.of(HttpMethod.ANY) : methods;
  }

  private static List<HttpMethod> requestMappingMethods(
      PsiAnnotation annotation, @Nullable PsiClass annotationType) {
    PsiAnnotationMemberValue methodValue = annotation.findAttributeValue(METHOD);
    if (methodValue == null && annotationType != null) {
      PsiAnnotation metaAnnotation = AnnotationUtil.findAnnotation(annotationType, REQUEST_MAPPING);
      methodValue = metaAnnotation == null ? null : metaAnnotation.findAttributeValue(METHOD);
    }
    return requestMethodValues(methodValue);
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
    try {
      return Optional.of(HttpMethod.of(requestMethodName));
    } catch (IllegalArgumentException unsupportedMethod) {
      return Optional.empty();
    }
  }

  @Override
  public boolean isController(@NotNull PsiClass psiClass) {
    Objects.requireNonNull(psiClass);
    return isControllerClass(psiClass);
  }

  @Override
  public @NotNull List<String> basePaths(@NotNull PsiClass psiClass) {
    Objects.requireNonNull(psiClass);
    List<PsiAnnotation> annotations = mappingAnnotations(psiClass);
    if (annotations.isEmpty()) {
      return List.of("");
    }
    return annotations.stream()
        .flatMap(annotation -> mappingPaths(annotation).stream())
        .distinct()
        .toList();
  }

  @Override
  public @NotNull List<HttpMethod> methods(@NotNull PsiMethod psiMethod) {
    Objects.requireNonNull(psiMethod);
    return mappingAnnotations(psiMethod).stream()
        .flatMap(annotation -> methodsOf(annotation).stream())
        .distinct()
        .toList();
  }

  @Override
  public @NotNull List<String> methodPaths(@NotNull PsiMethod psiMethod) {
    Objects.requireNonNull(psiMethod);
    List<PsiAnnotation> annotations = mappingAnnotations(psiMethod);
    if (annotations.isEmpty()) {
      return List.of();
    }
    return annotations.stream()
        .flatMap(annotation -> mappingPaths(annotation).stream())
        .distinct()
        .toList();
  }
}
