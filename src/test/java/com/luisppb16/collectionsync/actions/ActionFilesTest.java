/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */

package com.luisppb16.collectionsync.actions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIOException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("ActionFiles")
class ActionFilesTest {

  @TempDir Path tempDir;

  // ------------------------------------------------------------------ isSupportedFile

  private static VirtualFile fileOfType(String fileTypeName) {
    FileType fileType = mock(FileType.class);
    when(fileType.getName()).thenReturn(fileTypeName);
    VirtualFile virtualFile = mock(VirtualFile.class);
    when(virtualFile.getFileType()).thenReturn(fileType);
    return virtualFile;
  }

  private static AnActionEvent eventOf(Project project, VirtualFile file) {
    AnActionEvent event = mock(AnActionEvent.class);
    when(event.getProject()).thenReturn(project);
    when(event.getPresentation()).thenReturn(mock(Presentation.class));
    when(event.getData(CommonDataKeys.VIRTUAL_FILE)).thenReturn(file);
    return event;
  }

  @ParameterizedTest
  @ValueSource(strings = {"JSON", "YAML", "PLAIN_TEXT"})
  @DisplayName("Given a file of a JSON, YAML or plain-text type, when checked, then it is accepted")
  void acceptsSupportedFileTypes(String fileTypeName) {
    assertThat(ActionFiles.isSupportedFile(fileOfType(fileTypeName))).isTrue();
  }

  @Test
  @DisplayName("Given no file under the popup, when checked, then it is rejected")
  void rejectsNullFile() {
    assertThat(ActionFiles.isSupportedFile(null)).isFalse();
  }

  // ------------------------------------------------------------------ setEnabledForSupportedFile

  @ParameterizedTest
  @ValueSource(strings = {"UNKNOWN", "ZIP", "HTTP Request"})
  @DisplayName("Given a file of any other type, when checked, then it is rejected")
  void rejectsUnsupportedFileTypes(String fileTypeName) {
    assertThat(ActionFiles.isSupportedFile(fileOfType(fileTypeName))).isFalse();
  }

  @Test
  @DisplayName(
      "Given a project and a supported file, when the presentation is updated, then the action is enabled")
  void enablesActionForSupportedFile() {
    AnActionEvent event = eventOf(mock(Project.class), fileOfType("JSON"));
    Presentation presentation = event.getPresentation();

    ActionFiles.setEnabledForSupportedFile(event);

    verify(presentation).setEnabledAndVisible(true);
  }

  @Test
  @DisplayName(
      "Given an unsupported file, when the presentation is updated, then the action is disabled")
  void disablesActionForUnsupportedFile() {
    AnActionEvent event = eventOf(mock(Project.class), fileOfType("ZIP"));
    Presentation presentation = event.getPresentation();

    ActionFiles.setEnabledForSupportedFile(event);

    verify(presentation).setEnabledAndVisible(false);
  }

  // ------------------------------------------------------------------ isOpenApiDocument

  @Test
  @DisplayName(
      "Given no project or no file, when the presentation is updated, then the action is disabled")
  void disablesActionWithoutProjectOrFile() {
    AnActionEvent withoutProject = eventOf(null, fileOfType("JSON"));
    AnActionEvent withoutFile = eventOf(mock(Project.class), null);

    ActionFiles.setEnabledForSupportedFile(withoutProject);
    ActionFiles.setEnabledForSupportedFile(withoutFile);

    verify(withoutProject.getPresentation()).setEnabledAndVisible(false);
    verify(withoutFile.getPresentation()).setEnabledAndVisible(false);
  }

  @Test
  @DisplayName("Given an object with paths, when checked, then it is an OpenAPI document")
  void acceptsObjectWithPaths() {
    assertThat(ActionFiles.isOpenApiDocument(parse("{\"paths\": {\"/users\": {\"get\": {}}}}")))
        .isTrue();
  }

  @Test
  @DisplayName("Given an object without paths, when checked, then it is not an OpenAPI document")
  void rejectsObjectWithoutPaths() {
    assertThat(ActionFiles.isOpenApiDocument(parse("{\"openapi\": \"3.0.0\"}"))).isFalse();
  }

  // ------------------------------------------------------------------ readOpenApiTree

  @Test
  @DisplayName("Given a scalar or an array root, when checked, then it is not an OpenAPI document")
  void rejectsNonObjectRoots() {
    assertThat(ActionFiles.isOpenApiDocument(parse("\"a scalar\""))).isFalse();
    assertThat(ActionFiles.isOpenApiDocument(parse("[1, 2]"))).isFalse();
  }

  @Test
  @DisplayName("Given a JSON document, when read, then the root object is returned")
  void readsJsonDocument() throws IOException {
    Path file = write("openapi.json", "{\"paths\": {\"/users\": {\"get\": {}}}}");

    JsonNode root = ActionFiles.readOpenApiTree(file.toFile());

    assertThat(ActionFiles.isOpenApiDocument(root)).isTrue();
  }

  @Test
  @DisplayName("Given a YAML document, when read, then the root object is returned")
  void readsYamlDocument() throws IOException {
    Path file = write("openapi.yaml", "paths:\n  /users:\n    get:\n      summary: list\n");

    JsonNode root = ActionFiles.readOpenApiTree(file.toFile());

    assertThat(ActionFiles.isOpenApiDocument(root)).isTrue();
  }

  @Test
  @DisplayName(
      "Given YAML content in a .json file, when read, then the JSON parser fails and the YAML fallback parses it")
  void fallsBackToYamlWhenJsonParsingFails() throws IOException {
    Path file = write("openapi.json", "paths:\n  /users:\n    get: {}\n");

    JsonNode root = ActionFiles.readOpenApiTree(file.toFile());

    assertThat(ActionFiles.isOpenApiDocument(root)).isTrue();
  }

  @Test
  @DisplayName("Given an unreadable file, when read, then an IOException is thrown")
  void failsOnUnreadableFile() {
    assertThatIOException().isThrownBy(() -> ActionFiles.readOpenApiTree(tempDir.toFile()));
  }

  private JsonNode parse(String content) {
    try {
      return new ObjectMapper().readTree(content);
    } catch (IOException neverHappensOnInMemoryContent) {
      throw new AssertionError(neverHappensOnInMemoryContent);
    }
  }

  private Path write(String fileName, String content) throws IOException {
    Path path = tempDir.resolve(fileName);
    Files.writeString(path, content);
    return path;
  }
}
