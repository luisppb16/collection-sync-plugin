/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */

package com.luisppb16.collectionsync.actions;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Objects;

import org.jetbrains.annotations.NotNull;

import com.fasterxml.jackson.databind.JsonNode;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;

import com.luisppb16.collectionsync.i18n.EndpointCoverageBundle;
import com.luisppb16.collectionsync.settings.EndpointCoverageSettings;

/**
 * "Endpoint Coverage: Use as OpenAPI Source": available on {@code .json}, {@code .yaml} and
 * {@code .yml} files; validates that the file is an OpenAPI 3.x document (an object with
 * {@code paths}) and, when valid, sets it as the endpoint source and rescans.
 */
public final class UseAsOpenApiSourceAction extends AnAction {

    /**
     * Validates the document and, when it contains a {@code paths} object, sets its path as the
     * OpenAPI source with source type {@code OPEN_API} and rescans with the tool window open. The
     * settings are never touched when the file does not validate.
     *
     * @param event action event; must not be null
     */
    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = Objects.requireNonNull(event.getProject(), "project must not be null");
        VirtualFile selected = Objects.requireNonNull(event.getData(CommonDataKeys.VIRTUAL_FILE),
                "selected file must not be null");
        File openApiFile = new File(selected.getPath());
        if (!validateOpenApiDocument(openApiFile, project)) {
            return;
        }
        applySource(project, openApiFile.getAbsolutePath());
        ScanEndpointCoverageAction.activateAndScan(project);
    }

    /**
     * Enables the action only on supported collection/OpenAPI files.
     *
     * @param event action event; must not be null
     */
    @Override
    public void update(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        event.getPresentation().setEnabledAndVisible(project != null
                && ActionFiles.isSupportedFile(event.getData(CommonDataKeys.VIRTUAL_FILE)));
    }

    /**
     * The update only reads the data context.
     *
     * @return the EDT update thread
     */
    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
    }

    private static boolean validateOpenApiDocument(@NotNull File openApiFile, @NotNull Project project) {
        try {
            JsonNode root = ActionFiles.readOpenApiTree(openApiFile);
            if (ActionFiles.isOpenApiDocument(root)) {
                return true;
            }
            Messages.showErrorDialog(project,
                    EndpointCoverageBundle.message("action.openapi.invalid.message", openApiFile.getName()),
                    EndpointCoverageBundle.message("action.openapi.invalid.title"));
            return false;
        } catch (IOException brokenDocument) {
            Messages.showErrorDialog(project,
                    EndpointCoverageBundle.message("action.openapi.unreadable.message",
                            openApiFile.getName(), brokenDocument.getMessage()),
                    EndpointCoverageBundle.message("action.openapi.invalid.title"));
            return false;
        }
    }

    private static void applySource(@NotNull Project project, @NotNull String absolutePath) {
        EndpointCoverageSettings settings = EndpointCoverageSettings.getInstance(project);
        EndpointCoverageSettings.State updated = stateForUpdate(settings);
        updated.openApiFilePath = absolutePath;
        updated.sourceType = EndpointCoverageSettings.SourceType.OPEN_API.name();
        settings.setState(updated);
    }

    /**
     * Copy of the persisted state with mutable lists, mirroring how
     * {@link com.luisppb16.collectionsync.ui.toolwindow.CoveragePanel} applies updates: the whole
     * state is replaced so the change is persisted atomically.
     */
    private static @NotNull EndpointCoverageSettings.State stateForUpdate(@NotNull EndpointCoverageSettings settings) {
        EndpointCoverageSettings.State current = settings.getState();
        EndpointCoverageSettings.State updated = new EndpointCoverageSettings.State();
        updated.sourceType = current.sourceType;
        updated.openApiFilePath = current.openApiFilePath;
        updated.collectionFilePaths = new ArrayList<>(current.collectionFilePaths);
        updated.exclusions = new ArrayList<>(current.exclusions);
        updated.autoScanOnProjectOpen = current.autoScanOnProjectOpen;
        return updated;
    }
}