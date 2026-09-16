/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */

package com.luisppb16.collectionsync.ui.toolwindow;

import java.util.Arrays;
import java.util.Objects;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;

/**
 * Registers the "Endpoint Coverage" tool window, available on projects that contain Java modules.
 *
 * <p>The applicability check is defensive on purpose: any platform class-loading surprise degrades
 * to {@code true} so a working Java setup never loses the tool window.
 */
public final class EndpointCoverageToolWindowFactory implements ToolWindowFactory, DumbAware {

    private static final Logger LOG = Logger.getInstance(EndpointCoverageToolWindowFactory.class);

    /**
     * Builds the coverage panel and attaches it as the tool window content.
     *
     * @param project    current project; must not be null
     * @param toolWindow tool window to populate; must not be null
     */
    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        Objects.requireNonNull(toolWindow);
        Content content = ContentFactory.getInstance()
                .createContent(new CoveragePanel(project), "", false);
        toolWindow.getContentManager().addContent(content);
    }

    /**
     * Reports whether the tool window applies to the given project.
     *
     * @param project current project; must not be null
     * @return true when the project has at least one Java module (or the check cannot run)
     */
    @Override
    public @Nullable Object isApplicableAsync(@NotNull Project project,
                                              @NotNull kotlin.coroutines.Continuation<? super Boolean> continuation) {
        try {
            return hasJavaModule(Objects.requireNonNull(project));
        } catch (RuntimeException | LinkageError brokenPlatform) {
            LOG.warn("Failed to check whether the project has Java modules; showing the tool window anyway",
                    brokenPlatform);
            return Boolean.TRUE;
        }
    }

    private static boolean hasJavaModule(@NotNull Project project) {
        Module[] modules = ModuleManager.getInstance(project).getModules();
        return Arrays.stream(modules)
                .map(ModuleType::get)
                .anyMatch(JavaModuleType.getModuleType()::equals);
    }
}