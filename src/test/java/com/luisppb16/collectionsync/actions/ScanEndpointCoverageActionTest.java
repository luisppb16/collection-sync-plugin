/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */

package com.luisppb16.collectionsync.actions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.ContentManager;
import com.luisppb16.collectionsync.coverage.CoverageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

@DisplayName("ScanEndpointCoverageAction")
class ScanEndpointCoverageActionTest {

  private final ScanEndpointCoverageAction action = new ScanEndpointCoverageAction();

  private static AnActionEvent eventOf(Project project) {
    AnActionEvent event = mock(AnActionEvent.class);
    when(event.getProject()).thenReturn(project);
    when(event.getPresentation()).thenReturn(mock(Presentation.class));
    return event;
  }

  @Test
  @DisplayName("Given the action, when its update thread is requested, then it reports the EDT")
  void updatesOnEdt() {
    assertThat(action.getActionUpdateThread()).isEqualTo(ActionUpdateThread.EDT);
  }

  @Test
  @DisplayName(
      "Given a project with modules, when the action is updated, then it is enabled and visible")
  void enablesActionOnProjectsWithModules() {
    Project project = mock(Project.class);
    ModuleManager moduleManager = mock(ModuleManager.class);
    when(moduleManager.getModules()).thenReturn(new Module[] {mock(Module.class)});
    AnActionEvent event = eventOf(project);
    Presentation presentation = event.getPresentation();

    try (MockedStatic<ModuleManager> managers = mockStatic(ModuleManager.class)) {
      managers.when(() -> ModuleManager.getInstance(project)).thenReturn(moduleManager);
      action.update(event);
    }

    verify(presentation).setEnabledAndVisible(true);
  }

  @Test
  @DisplayName("Given a project without modules, when the action is updated, then it is disabled")
  void disablesActionWithoutModules() {
    Project project = mock(Project.class);
    ModuleManager moduleManager = mock(ModuleManager.class);
    when(moduleManager.getModules()).thenReturn(new Module[0]);
    AnActionEvent event = eventOf(project);
    Presentation presentation = event.getPresentation();

    try (MockedStatic<ModuleManager> managers = mockStatic(ModuleManager.class)) {
      managers.when(() -> ModuleManager.getInstance(project)).thenReturn(moduleManager);
      action.update(event);
    }

    verify(presentation).setEnabledAndVisible(false);
  }

  @Test
  @DisplayName(
      "Given no project, when the action is updated, then it is disabled without touching the modules")
  void disablesActionWithoutProject() {
    AnActionEvent event = eventOf(null);
    Presentation presentation = event.getPresentation();

    action.update(event);

    verify(presentation).setEnabledAndVisible(false);
  }

  @Test
  @DisplayName(
      "Given an open tool window, when the action runs, then the scan is requested and the panel refreshed")
  void activatesToolWindowAndScans() {
    Project project = mock(Project.class);
    CoverageService service = mock(CoverageService.class);
    ToolWindow toolWindow = mock(ToolWindow.class);
    ToolWindowManager manager = mock(ToolWindowManager.class);
    ContentManager contentManager = mock(ContentManager.class);
    when(project.getService(CoverageService.class)).thenReturn(service);
    when(manager.getToolWindow(ScanEndpointCoverageAction.TOOL_WINDOW_ID)).thenReturn(toolWindow);
    when(toolWindow.getContentManager()).thenReturn(contentManager);
    when(contentManager.getContent(0)).thenReturn(null);
    AnActionEvent event = eventOf(project);

    try (MockedStatic<ToolWindowManager> managers = mockStatic(ToolWindowManager.class)) {
      managers.when(() -> ToolWindowManager.getInstance(project)).thenReturn(manager);
      action.actionPerformed(event);

      ArgumentCaptor<Runnable> activation = ArgumentCaptor.forClass(Runnable.class);
      verify(toolWindow).activate(activation.capture());
      activation.getValue().run();
    }

    ArgumentCaptor<Runnable> scan = ArgumentCaptor.forClass(Runnable.class);
    verify(service).scanAsync(scan.capture());
    scan.getValue().run();
  }

  @Test
  @DisplayName("Given no tool window, when the action runs, then no scan is requested")
  void doesNothingWithoutToolWindow() {
    Project project = mock(Project.class);
    CoverageService service = mock(CoverageService.class);
    ToolWindowManager manager = mock(ToolWindowManager.class);
    when(project.getService(CoverageService.class)).thenReturn(service);
    when(manager.getToolWindow(ScanEndpointCoverageAction.TOOL_WINDOW_ID)).thenReturn(null);
    AnActionEvent event = eventOf(project);

    try (MockedStatic<ToolWindowManager> managers = mockStatic(ToolWindowManager.class)) {
      managers.when(() -> ToolWindowManager.getInstance(project)).thenReturn(manager);
      action.actionPerformed(event);
    }

    verify(service, never()).scanAsync(any());
  }
}
