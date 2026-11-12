/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */

package com.luisppb16.collectionsync.scan;

import com.intellij.codeInsight.MetaAnnotationUtil;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiModifierListOwner;
import com.luisppb16.collectionsync.domain.model.HttpMethod;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;

/**
 * Shared {@link EndpointAnnotationResolver} for frameworks whose endpoints are declared by fixed
 * verb annotations over a set of controller and path annotations.
 */
abstract class FixedVerbResolver implements EndpointAnnotationResolver {

  private static final String VALUE = "value";

  private final List<String> controllerAnnotations;
  private final List<String> methodPathAnnotations;
  private final List<FixedVerb> fixedVerbs;
  private final Map<String, HttpMethod> fixedByName;

  FixedVerbResolver(
      List<String> controllerAnnotations,
      List<String> methodPathAnnotations,
      List<FixedVerb> fixedVerbs) {
    this.controllerAnnotations = List.copyOf(controllerAnnotations);
    this.methodPathAnnotations = List.copyOf(methodPathAnnotations);
    this.fixedVerbs = List.copyOf(fixedVerbs);
    this.fixedByName =
        fixedVerbs.stream()
            .collect(Collectors.toUnmodifiableMap(FixedVerb::qualifiedName, FixedVerb::method));
  }

  private static List<PsiAnnotation> directAnnotations(PsiModifierListOwner owner) {
    PsiModifierList modifierList = owner.getModifierList();
    return modifierList == null ? List.of() : Arrays.asList(modifierList.getAnnotations());
  }

  @Override
  public boolean isController(@NotNull PsiClass psiClass) {
    Objects.requireNonNull(psiClass);
    return !EndpointAnnotationResolvers.annotationsMatchingInHierarchy(
            psiClass, controllerAnnotations)
        .isEmpty();
  }

  @Override
  public @NotNull List<String> basePaths(@NotNull PsiClass psiClass) {
    Objects.requireNonNull(psiClass);
    List<String> paths =
        EndpointAnnotationResolvers.annotationsMatchingInHierarchy(psiClass, controllerAnnotations)
            .stream()
            .flatMap(
                annotation -> EndpointAnnotationResolvers.allStrings(annotation, VALUE).stream())
            .distinct()
            .toList();
    return paths.isEmpty() ? List.of("") : paths;
  }

  @Override
  public @NotNull List<HttpMethod> methods(@NotNull PsiMethod psiMethod) {
    Objects.requireNonNull(psiMethod);
    return directAnnotations(psiMethod).stream()
        .map(this::fixedMethodOf)
        .filter(Objects::nonNull)
        .distinct()
        .toList();
  }

  @Override
  public @NotNull List<String> methodPaths(@NotNull PsiMethod psiMethod) {
    Objects.requireNonNull(psiMethod);
    List<PsiAnnotation> annotations = directAnnotations(psiMethod);
    if (annotations.stream().noneMatch(annotation -> fixedMethodOf(annotation) != null)) {
      return List.of();
    }
    List<String> paths =
        annotations.stream()
            .filter(annotation ->
                EndpointAnnotationResolvers.matches(annotation, methodPathAnnotations))
            .flatMap(
                annotation -> EndpointAnnotationResolvers.allStrings(annotation, VALUE).stream())
            .distinct()
            .toList();
    return paths.isEmpty() ? List.of("") : paths;
  }

  private HttpMethod fixedMethodOf(PsiAnnotation annotation) {
    HttpMethod direct = fixedByName.get(annotation.getQualifiedName());
    if (direct != null) {
      return direct;
    }
    PsiClass annotationType = annotation.resolveAnnotationType();
    if (annotationType == null) {
      return null;
    }
    for (FixedVerb verb : fixedVerbs) {
      if (MetaAnnotationUtil.isMetaAnnotated(annotationType, List.of(verb.qualifiedName()))) {
        return verb.method();
      }
    }
    return null;
  }
}
