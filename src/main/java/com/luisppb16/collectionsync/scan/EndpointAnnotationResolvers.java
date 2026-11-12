/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */

package com.luisppb16.collectionsync.scan;

import com.intellij.codeInsight.MetaAnnotationUtil;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiArrayInitializerMemberValue;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiModifierListOwner;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.jetbrains.annotations.NotNull;

/** Shared PSI helpers for {@link EndpointAnnotationResolver} implementations. */
final class EndpointAnnotationResolvers {

  private EndpointAnnotationResolvers() {}

  static List<String> allStrings(@NotNull PsiAnnotation annotation, @NotNull String attribute) {
    PsiAnnotationMemberValue value = annotation.findAttributeValue(attribute);
    if (value == null) {
      return List.of();
    }
    if (value instanceof PsiArrayInitializerMemberValue array) {
      return Arrays.stream(array.getInitializers())
          .map(EndpointAnnotationResolvers::literalString)
          .filter(Objects::nonNull)
          .map(String::strip)
          .toList();
    }
    String text = literalString(value);
    return text == null ? List.of() : List.of(text.strip());
  }

  static List<PsiAnnotation> annotationsMatching(
      @NotNull PsiModifierListOwner owner, @NotNull Collection<String> qualifiedNames) {
    PsiModifierList modifierList = owner.getModifierList();
    if (modifierList == null) {
      return List.of();
    }
    return Arrays.stream(modifierList.getAnnotations())
        .filter(annotation -> matches(annotation, qualifiedNames))
        .toList();
  }

  static List<PsiAnnotation> annotationsMatchingInHierarchy(
      @NotNull PsiClass psiClass, @NotNull Collection<String> qualifiedNames) {
    Set<PsiClass> seen = Collections.newSetFromMap(new IdentityHashMap<>());
    Deque<PsiClass> pending = new ArrayDeque<>();
    pending.add(psiClass);
    while (!pending.isEmpty()) {
      PsiClass current = pending.removeFirst();
      if (current == null || !seen.add(current)) {
        continue;
      }
      List<PsiAnnotation> matches = annotationsMatching(current, qualifiedNames);
      if (!matches.isEmpty()) {
        return matches;
      }
      Collections.addAll(pending, current.getSupers());
    }
    return List.of();
  }

  static boolean matches(
      @NotNull PsiAnnotation annotation, @NotNull Collection<String> qualifiedNames) {
    String qualifiedName = annotation.getQualifiedName();
    if (qualifiedName != null && qualifiedNames.contains(qualifiedName)) {
      return true;
    }
    PsiClass annotationType = annotation.resolveAnnotationType();
    return annotationType != null
        && MetaAnnotationUtil.isMetaAnnotated(annotationType, qualifiedNames);
  }

  private static String literalString(PsiAnnotationMemberValue value) {
    if (value instanceof PsiLiteralExpression literal
        && literal.getValue() instanceof String text) {
      return text;
    }
    return null;
  }
}
