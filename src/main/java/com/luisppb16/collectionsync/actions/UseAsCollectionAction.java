/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */

package com.luisppb16.collectionsync.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.luisppb16.collectionsync.collection.CollectionParsers;
import com.luisppb16.collectionsync.domain.model.ApiRequest;
import com.luisppb16.collectionsync.i18n.EndpointCoverageBundle;
import com.luisppb16.collectionsync.settings.EndpointCoverageSettings;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/**
 * "Endpoint Coverage: Check Coverage with This Collection": available on files the IDE recognizes
 * as JSON, YAML or plain text; validates the file as a Postman v2.1 or Insomnia v4/v5 collection
 * with at least one request and, when valid, adds it to the project collection paths and rescans.
 */
public final class UseAsCollectionAction extends AnAction {

  private static boolean validateCollection(
      @NotNull File collectionFile, @NotNull Project project) {
    List<ApiRequest> requests;
    try {
      requests = CollectionParsers.parse(collectionFile);
    } catch (IOException | IllegalArgumentException brokenCollection) {
      Messages.showErrorDialog(
          project,
          EndpointCoverageBundle.message(
              "action.collection.invalid.message",
              collectionFile.getName(),
              brokenCollection.getMessage()),
          EndpointCoverageBundle.message("action.collection.invalid.title"));
      return false;
    }
    if (requests.isEmpty()) {
      Messages.showErrorDialog(
          project,
          EndpointCoverageBundle.message(
              "action.collection.empty.message", collectionFile.getName()),
          EndpointCoverageBundle.message("action.collection.empty.title"));
      return false;
    }
    return true;
  }

  private static void addCollectionPath(@NotNull Project project, @NotNull String path) {
    EndpointCoverageSettings settings = EndpointCoverageSettings.getInstance(project);
    EndpointCoverageSettings.State updated =
        EndpointCoverageSettings.copyForUpdate(settings.getState());
    if (!updated.collectionFilePaths.contains(path)) {
      updated.collectionFilePaths.add(path);
    }
    settings.setState(updated);
  }

  /**
   * Validates the file as a collection and, when it parses to at least one request, adds its path
   * to the settings (without duplicates) and rescans with the tool window open. The settings are
   * never touched when the file does not validate.
   *
   * @param event action event; must not be null
   */
  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    Project project = Objects.requireNonNull(event.getProject(), "project must not be null");
    VirtualFile selected =
        Objects.requireNonNull(
            event.getData(CommonDataKeys.VIRTUAL_FILE), "selected file must not be null");
    File collectionFile = new File(selected.getPath());
    if (!validateCollection(collectionFile, project)) {
      return;
    }
    addCollectionPath(project, collectionFile.getAbsolutePath());
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
    event
        .getPresentation()
        .setEnabledAndVisible(
            project != null
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
}
