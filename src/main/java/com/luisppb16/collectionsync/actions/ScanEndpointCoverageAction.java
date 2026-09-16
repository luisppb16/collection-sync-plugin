/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */

package com.luisppb16.collectionsync.actions;

import java.util.Objects;

import org.jetbrains.annotations.NotNull;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;

import com.luisppb16.collectionsync.coverage.CoverageService;
import com.luisppb16.collectionsync.ui.toolwindow.CoveragePanel;

/**
 * "Analyze Endpoints Coverage": activates the "Endpoint Coverage" tool window and launches a
 * background scan.
 *
 * <p>The refresh of the table lives in {@link CoveragePanel#refresh()}; the panel runs its own
 * scan on creation, so this action only refreshes a panel that is already open. The text and
 * description come from the resource bundle via {@code withJava.xml} (key convention
 * {@code action.<id>.text}); the {@code text}/{@code description} XML attributes only act as an
 * English fallback when the bundle keys are missing.
 */
public final class ScanEndpointCoverageAction extends AnAction {

    /** Tool window id, exactly as registered in {@code withJava.xml}. */
    static final String TOOL_WINDOW_ID = "Endpoint Coverage";

    /**
     * Activates the tool window and scans when it is shown.
     *
     * @param event action event; must not be null
     */
    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = Objects.requireNonNull(event.getProject(), "project must not be null");
        activateAndScan(project);
    }

    /**
     * Shows the action only on projects with at least one module.
     *
     * @param event action event; must not be null
     */
    @Override
    public void update(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        event.getPresentation().setEnabledAndVisible(project != null && hasModules(project));
    }

    /**
     * The update touches {@code ModuleManager}, which is documented to be read from the EDT.
     *
     * @return the EDT update thread
     */
    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
    }

    /**
     * Activates the "Endpoint Coverage" tool window and, once active, launches a background scan
     * that refreshes the open panel on completion. Shared with the context menu actions so they
     * behave exactly the same. When the tool window is not available (project without the Java
     * plugin), nothing happens.
     *
     * @param project current project; must not be null
     */
    static void activateAndScan(@NotNull Project project) {
        Objects.requireNonNull(project, "project must not be null");
        ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID);
        if (toolWindow == null) {
            return;
        }
        toolWindow.activate(() -> project.getService(CoverageService.class)
                .scanAsync(() -> refreshPanel(toolWindow)));
    }

    private static void refreshPanel(@NotNull ToolWindow toolWindow) {
        Content content = toolWindow.getContentManager().getContent(0);
        if (content != null && content.getComponent() instanceof CoveragePanel panel) {
            panel.refresh();
        }
    }

    private static boolean hasModules(@NotNull Project project) {
        return ModuleManager.getInstance(project).getModules().length > 0;
    }
}