/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */
package com.luisppb16.collectionsync.infrastructure.psi;

import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import java.util.Objects;

/**
 * Adds inline fixture sources to the test project as real files (the fixture API of this platform
 * version has no in-memory class adder) and hands back the top-level class.
 */
final class PsiTestHelper {

  private final CodeInsightTestFixture fixture;

  PsiTestHelper(CodeInsightTestFixture fixture) {
    this.fixture = Objects.requireNonNull(fixture, "fixture");
  }

  /** @return the first top-level class of the added source file. */
  PsiClass addClass(String path, String source) {
    PsiJavaFile psiFile = (PsiJavaFile) fixture.addFileToProject(path, source);
    return psiFile.getClasses()[0];
  }

  /** @return the class with the given qualified name previously added to the project. */
  PsiClass findClass(String qualifiedName) {
    return JavaPsiFacade.getInstance(fixture.getProject())
        .findClass(qualifiedName, GlobalSearchScope.allScope(fixture.getProject()));
  }
}