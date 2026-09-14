/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */
package com.luisppb16.collectionsync.infrastructure.psi.common;

import com.intellij.psi.PsiClass;
import com.luisppb16.collectionsync.domain.model.EndpointDescriptor;
import java.util.List;

/** Discovers endpoints in one Java class, for a concrete web framework. */
public interface EndpointScanner {

  /**
   * @param psiClass a class found in the project sources
   * @return the endpoints declared in it (empty when the class is not a controller of this framework)
   */
  java.util.List<EndpointDescriptor> scanClass(PsiClass psiClass);
}