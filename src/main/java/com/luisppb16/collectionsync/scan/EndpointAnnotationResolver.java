/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */

package com.luisppb16.collectionsync.scan;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.luisppb16.collectionsync.domain.model.HttpMethod;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * Extracts endpoints from annotated controller methods for one specific framework (Spring MVC,
 * JAX-RS...). One implementation per framework, selected by a detector.
 */
public interface EndpointAnnotationResolver {

  /**
   * @param psiClass candidate class; must not be null
   * @return true if this class is a controller handled by this resolver's framework
   */
  boolean isController(@NotNull PsiClass psiClass);

  /**
   * Resolves every base path declared at class level.
   *
   * @param psiClass controller class; must not be null
   * @return the base paths, or a single empty string when the controller declares none; never null
   */
  @NotNull
  List<String> basePaths(@NotNull PsiClass psiClass);

  /**
   * Resolves every endpoint declared by one handler method.
   *
   * @param psiMethod handler method; must not be null
   * @return one entry per declared HTTP method, possibly empty; never null
   */
  @NotNull
  List<HttpMethod> methods(@NotNull PsiMethod psiMethod);

  /**
   * Resolves every path declared at method level, relative to the controller base path.
   *
   * @param psiMethod handler method; must not be null
   * @return the method paths, or empty when the method declares no endpoint; never null
   */
  @NotNull
  List<String> methodPaths(@NotNull PsiMethod psiMethod);
}
