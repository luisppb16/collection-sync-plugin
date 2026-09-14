/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */
package com.luisppb16.collectionsync.infrastructure.psi;

import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import java.util.Objects;

/**
 * PSI fixture base. The platform default {@link LightJavaCodeInsightFixtureTestCase} descriptor
 * ships a mock JDK whose home does not exist in this IDE distribution, so JDK types never resolve;
 * the descriptor here wires the real toolchain JDK instead so {@code String}, {@code List},
 * {@code Optional} and friends resolve like they do in the IDE.
 */
public abstract class PsiTestCase extends LightJavaCodeInsightFixtureTestCase {

  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return new DefaultLightProjectDescriptor() {
      @Override
      public Sdk getSdk() {
        String javaHome = Objects.requireNonNull(System.getProperty("java.home"), "java.home");
        return JavaSdk.getInstance().createJdk("java 25", javaHome, false);
      }
    };
  }
}