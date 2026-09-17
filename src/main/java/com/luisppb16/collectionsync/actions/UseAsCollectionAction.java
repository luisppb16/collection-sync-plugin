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
import com.luisppb16.collectionsync.i18n.EndpointCoverageBundle;
import com.luisppb16.collectionsync.settings.EndpointCoverageSettings;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/**
 * "Endpoint Coverage: Check Coverage with This Collection": available on {@code .json}, {@code
 * .yaml} and {@code .yml} files; validates the file as a Postman v2.1 or Insomnia v4/v5 collection
 * and, when valid, adds it to the project collection paths and rescans.
 */
public final class UseAsCollectionAction extends AnAction {

  private static boolean validateCollection(
      @NotNull File collectionFile, @NotNull Project project) {
    try {
      CollectionParsers.forFile(collectionFile).parse(collectionFile);
      return true;
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
  }

  private static void addCollectionPath(@NotNull Project project, @NotNull String path) {
    EndpointCoverageSettings settings = EndpointCoverageSettings.getInstance(project);
    EndpointCoverageSettings.State updated = stateForUpdate(settings);
    if (!updated.collectionFilePaths.contains(path)) {
      updated.collectionFilePaths.add(path);
    }
    settings.setState(updated);
  }

  /**
   * Copy of the persisted state with mutable lists, mirroring how {@link
   * com.luisppb16.collectionsync.ui.toolwindow.CoveragePanel} applies updates: the whole state is
   * replaced so the change is persisted atomically.
   */
  private static @NotNull EndpointCoverageSettings.State stateForUpdate(
      @NotNull EndpointCoverageSettings settings) {
    EndpointCoverageSettings.State current = settings.getState();
    EndpointCoverageSettings.State updated = new EndpointCoverageSettings.State();
    updated.sourceType = current.sourceType;
    updated.openApiFilePath = current.openApiFilePath;
    updated.collectionFilePaths = new ArrayList<>(current.collectionFilePaths);
    updated.exclusions = new ArrayList<>(current.exclusions);
    updated.autoScanOnProjectOpen = current.autoScanOnProjectOpen;
    return updated;
  }

  /**
   * Validates the file as a collection and, when it parses, adds its path to the settings (without
   * duplicates) and rescans with the tool window open. The settings are never touched when the file
   * does not parse.
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
