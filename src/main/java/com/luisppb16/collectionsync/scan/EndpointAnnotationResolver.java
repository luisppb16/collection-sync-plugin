/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */

package com.luisppb16.collectionsync.scan;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.luisppb16.collectionsync.domain.model.HttpMethod;
import java.util.List;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;

/**
 * Extracts endpoints from annotated controller methods for one specific framework (Spring MVC,
 * JAX-RS...). One implementation per framework, selected by a detector.
 */
public interface EndpointAnnotationResolver {

  /**
   * Reads the string value of an annotation member, supporting string literals, single-element
   * string arrays and missing values.
   *
   * @param annotation annotation to read; must not be null
   * @param attribute member name, e.g. {@code value}; must not be null
   * @return the string value, or empty when the member is absent
   */
  @NotNull
  static Optional<String> stringValue(
      @NotNull PsiAnnotation annotation, @NotNull String attribute) {
    return EndpointAnnotationResolvers.stringValue(annotation, attribute);
  }

  /**
   * @param psiClass candidate class; must not be null
   * @return true if this class is a controller handled by this resolver's framework
   */
  boolean isController(@NotNull PsiClass psiClass);

  /**
   * Resolves the base path declared at class level.
   *
   * @param psiClass controller class; must not be null
   * @return the base path ("" when the controller declares none); never null
   */
  @NotNull
  String basePath(@NotNull PsiClass psiClass);

  /**
   * Resolves every endpoint declared by one handler method.
   *
   * @param psiMethod handler method; must not be null
   * @return one endpoint per declared HTTP method, possibly empty; never null
   */
  @NotNull
  List<HttpMethod> methods(@NotNull PsiMethod psiMethod);

  /**
   * Resolves the path declared at method level, relative to the controller base path.
   *
   * @param psiMethod handler method; must not be null
   * @return the method path ("" when the mapping has no path); never null
   */
  @NotNull
  String methodPath(@NotNull PsiMethod psiMethod);
}
