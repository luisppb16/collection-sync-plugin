/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */
package com.luisppb16.collectionsync.infrastructure.psi;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.util.PsiModificationTracker;
import com.luisppb16.collectionsync.util.Debouncer;
import java.util.Objects;

/**
 * Drops the endpoint cache shortly after any PSI modification, so the next query rescans the
 * edited files. Events arrive in bursts while typing; a 500 ms debounce collapses them into one
 * invalidation.
 */
public final class EndpointIndexInvalidator implements PsiModificationTracker.Listener {

  private static final long DEBOUNCE_MILLIS = 500;

  private final Debouncer debouncer = new Debouncer(DEBOUNCE_MILLIS);
  private final Project project;

  public EndpointIndexInvalidator(Project project) {
    this.project = Objects.requireNonNull(project, "project");
    Disposer.register(project, debouncer);
  }

  @Override
  public void modificationCountChanged() {
    debouncer.debounce(() -> EndpointIndexService.getInstance(project).invalidateAll());
  }
}