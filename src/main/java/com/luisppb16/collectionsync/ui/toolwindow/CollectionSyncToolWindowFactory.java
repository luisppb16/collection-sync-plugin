/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */
package com.luisppb16.collectionsync.ui.toolwindow;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.components.JBLabel;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;

/**
 * Entry point of the CollectionSync tool window. Phase 0 placeholder: real coverage trees are
 * wired in later phases.
 */
public final class CollectionSyncToolWindowFactory implements ToolWindowFactory {

  @Override
  public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
    JBLabel placeholder = new JBLabel("CollectionSync — coverage panel coming in a later phase");
    placeholder.setHorizontalAlignment(JBLabel.CENTER);

    JPanel panel = new JPanel();
    panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
    panel.add(placeholder);

    toolWindow.getComponent().add(panel);
  }
}