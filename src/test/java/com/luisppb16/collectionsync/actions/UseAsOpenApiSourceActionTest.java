/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */

package com.luisppb16.collectionsync.actions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.luisppb16.collectionsync.settings.EndpointCoverageSettings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

@DisplayName("UseAsOpenApiSourceAction")
class UseAsOpenApiSourceActionTest {

  private static final String OPENAPI =
      "{\"openapi\": \"3.0.0\", \"paths\": {\"/ping\": {\"get\": {}}}}";

  private final UseAsOpenApiSourceAction action = new UseAsOpenApiSourceAction();

  @TempDir Path tempDir;

  private static AnActionEvent eventOf(Project project, Path file) {
    VirtualFile virtualFile = mock(VirtualFile.class);
    when(virtualFile.getPath()).thenReturn(file.toString());
    AnActionEvent event = mock(AnActionEvent.class);
    when(event.getProject()).thenReturn(project);
    when(event.getData(CommonDataKeys.VIRTUAL_FILE)).thenReturn(virtualFile);
    return event;
  }

  @Test
  @DisplayName(
      "Given an OpenAPI document, when the action runs, then it becomes the source and a scan is requested")
  void storesOpenApiDocument() throws IOException {
    Path file = write("openapi.json", OPENAPI);
    Project project = mock(Project.class);
    EndpointCoverageSettings settings = new EndpointCoverageSettings();

    try (MockedStatic<EndpointCoverageSettings> settingsStatic =
            mockStatic(EndpointCoverageSettings.class);
        MockedStatic<ScanEndpointCoverageAction> scan =
            mockStatic(ScanEndpointCoverageAction.class)) {
      settingsStatic.when(() -> EndpointCoverageSettings.getInstance(project)).thenReturn(settings);

      action.actionPerformed(eventOf(project, file));

      scan.verify(() -> ScanEndpointCoverageAction.activateAndScan(project));
    }

    assertThat(settings.getOpenApiFilePath()).isEqualTo(file.toAbsolutePath().toString());
    assertThat(settings.getSourceType()).isEqualTo(EndpointCoverageSettings.SourceType.OPEN_API);
  }

  @Test
  @DisplayName(
      "Given a JSON document that is not OpenAPI, when the action runs, then an error is reported and nothing is stored")
  void rejectsNonOpenApiDocument() throws IOException {
    Path file = write("postman.json", "{\"info\": {\"name\": \"Users\"}, \"item\": []}");
    Project project = mock(Project.class);
    EndpointCoverageSettings settings = new EndpointCoverageSettings();

    try (MockedStatic<EndpointCoverageSettings> settingsStatic =
            mockStatic(EndpointCoverageSettings.class);
        MockedStatic<Messages> messages = mockStatic(Messages.class);
        MockedStatic<ScanEndpointCoverageAction> scan =
            mockStatic(ScanEndpointCoverageAction.class)) {
      settingsStatic.when(() -> EndpointCoverageSettings.getInstance(project)).thenReturn(settings);

      action.actionPerformed(eventOf(project, file));

      messages.verify(() -> Messages.showErrorDialog(eq(project), anyString(), anyString()));
      scan.verifyNoInteractions();
    }

    assertThat(settings.getOpenApiFilePath()).isEmpty();
    assertThat(settings.getSourceType())
        .isEqualTo(EndpointCoverageSettings.SourceType.CONTROLLER_ANNOTATIONS);
  }

  @Test
  @DisplayName(
      "Given an unreadable document, when the action runs, then an unreadable error is reported and nothing is stored")
  void rejectsUnreadableDocument() throws IOException {
    Path file = write("broken.json", "key: [unclosed");
    Project project = mock(Project.class);
    EndpointCoverageSettings settings = new EndpointCoverageSettings();

    try (MockedStatic<EndpointCoverageSettings> settingsStatic =
            mockStatic(EndpointCoverageSettings.class);
        MockedStatic<Messages> messages = mockStatic(Messages.class);
        MockedStatic<ScanEndpointCoverageAction> scan =
            mockStatic(ScanEndpointCoverageAction.class)) {
      settingsStatic.when(() -> EndpointCoverageSettings.getInstance(project)).thenReturn(settings);

      action.actionPerformed(eventOf(project, file));

      messages.verify(() -> Messages.showErrorDialog(eq(project), anyString(), anyString()));
      scan.verifyNoInteractions();
    }

    assertThat(settings.getOpenApiFilePath()).isEmpty();
  }

  @Test
  @DisplayName(
      "Given an action event, when the action is updated or its thread is requested, then both delegate consistently")
  void delegatesUpdateAndUpdateThread() {
    AnActionEvent event = mock(AnActionEvent.class);
    when(event.getProject()).thenReturn(mock(Project.class));
    when(event.getPresentation()).thenReturn(mock(Presentation.class));

    action.update(event);

    assertThat(action.getActionUpdateThread()).isEqualTo(ActionUpdateThread.EDT);
  }

  private Path write(String fileName, String content) throws IOException {
    Path path = tempDir.resolve(fileName);
    Files.writeString(path, content);
    return path;
  }
}
