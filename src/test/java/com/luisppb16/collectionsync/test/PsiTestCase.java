/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */

package com.luisppb16.collectionsync.test;

import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase5;

/**
 * Base class for PSI-level tests backed by the IntelliJ light test fixture.
 *
 * <p>The project descriptor uses the <b>real JDK</b> (the JVM running the tests, exposed through
 * {@code java.home}) instead of the mock JDK: with the real JDK the fixture sources keep normal
 * resolution of JDK types, which keeps PSI navigation and class lookups faithful to production.
 *
 * <p>Framework annotations under test (Spring, JAX-RS) are not on the test classpath, so fixtures
 * declare stub annotations inside the fixture sources themselves, written fully-qualified.
 */
public abstract class PsiTestCase extends LightJavaCodeInsightFixtureTestCase5 {

  protected PsiTestCase() {
    super(
        new DefaultLightProjectDescriptor() {
          @Override
          public Sdk getSdk() {
            return JavaSdk.getInstance()
                .createJdk("real-jdk", System.getProperty("java.home"), false);
          }
        });
  }

  /**
   * These tests build their sources in memory through the fixture, so no external test data
   * directory is needed: a plain marker path keeps the fixture from probing the IntelliJ community
   * test data roots.
   */
  @Override
  protected String getTestDataPath() {
    return "testData";
  }
}
