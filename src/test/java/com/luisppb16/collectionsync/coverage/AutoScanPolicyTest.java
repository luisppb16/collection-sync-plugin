/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */

package com.luisppb16.collectionsync.coverage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.intellij.openapi.project.Project;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AutoScanPolicy.shouldAutoScan")
class AutoScanPolicyTest {

  private final Project project = mock(Project.class);

  @Test
  @DisplayName(
      "Given the setting enabled and a live project, when the decision is evaluated, then the automatic scan runs")
  void allowsScanWhenSettingEnabled() {
    assertThat(AutoScanPolicy.shouldAutoScan(true, project)).isTrue();
  }

  @Test
  @DisplayName(
      "Given the setting disabled and a live project, when the decision is evaluated, then the automatic scan is skipped")
  void skipsScanWhenSettingDisabled() {
    assertThat(AutoScanPolicy.shouldAutoScan(false, project)).isFalse();
  }

  @Test
  @DisplayName(
      "Given a null project, when the decision is evaluated, then the call fails fast instead of returning a value")
  void rejectsNullProject() {
    // The IntelliJ platform null-check instrumentation reports the @NotNull violation as an
    // IllegalArgumentException; the plain JDK path (requireNonNull) would throw a
    // NullPointerException. Both fail fast before any scan decision is made.
    assertThatThrownBy(() -> AutoScanPolicy.shouldAutoScan(true, null))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("project");
  }
}