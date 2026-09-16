/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */

package com.luisppb16.collectionsync.coverage;

import com.intellij.openapi.project.Project;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/**
 * Pure decision of whether the tool window triggers a coverage scan by itself when it first opens
 * in a project, driven by the {@code autoScanOnProjectOpen} setting. Extracted from
 * {@code CoveragePanel} so the gating is testable without building any UI.
 *
 * <p>Manual scans (toolbar button, Tools menu, context actions) never go through this policy: they
 * must always run, whatever the setting says.
 */
public final class AutoScanPolicy {

  private AutoScanPolicy() {}

  /**
   * Decides whether the automatic scan on project open should be started.
   *
   * <p>Fail-fast on the project argument: every caller already owns a non-null project (the panel
   * constructor enforces it), so a null here is a programming error and is rejected instead of
   * silently skipping the scan.
   *
   * @param setting persisted {@code autoScanOnProjectOpen} setting
   * @param project project being opened; must not be null
   * @return true when the automatic scan may start
   * @throws NullPointerException when project is null
   */
  public static boolean shouldAutoScan(boolean setting, @NotNull Project project) {
    Objects.requireNonNull(project, "project must not be null");
    return setting;
  }
}