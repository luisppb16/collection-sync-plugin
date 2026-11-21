/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */

package com.luisppb16.collectionsync.ui.toolwindow;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/** Registers the "CollectionSync" tool window, available in IDEs that ship the Java plugin. */
public final class EndpointCoverageToolWindowFactory implements ToolWindowFactory, DumbAware {

  /**
   * Builds the coverage panel and attaches it as the tool window content.
   *
   * @param project current project; must not be null
   * @param toolWindow tool window to populate; must not be null
   */
  @Override
  public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
    Objects.requireNonNull(toolWindow);
    Content content =
        ContentFactory.getInstance().createContent(new CoveragePanel(project), "", false);
    toolWindow.getContentManager().addContent(content);
  }
}
