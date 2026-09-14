/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */
package com.luisppb16.collectionsync.infrastructure.psi;

import static org.assertj.core.api.Assertions.assertThat;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.luisppb16.collectionsync.domain.model.EndpointDescriptor;
import java.util.List;

/**
 * Endpoint index service against indexed project fixtures. JUnit3 style is required by the
 * platform test framework; the Given-When-Then contract lives in the test method names and bodies.
 */
public class EndpointIndexServiceTest extends PsiTestCase {

  private static final String CONTROLLER_PATH = "src/com/example/fixtures/UserController.java";

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.addFileToProject("src/org/springframework/web/bind/annotation/RequestMethod.java", PsiFixtures.REQUEST_METHOD_ENUM);
    myFixture.addFileToProject("src/com/example/fixtures/UserDto.java", PsiFixtures.USER_DTO);
  }

  public void testIndexesEndpointsFromProjectFiles() {
    // Given a project with one annotated controller file.
    PsiFile psiFile = myFixture.addFileToProject(CONTROLLER_PATH, PsiFixtures.USER_CONTROLLER);
    EndpointIndexService service = new EndpointIndexService(getProject());

    List<EndpointDescriptor> endpoints = service.endpointsInFile(psiFile.getVirtualFile());

    // When queried, then the controller endpoints are discovered.
    assertThat(endpoints).hasSize(4);
    assertThat(endpoints.get(0).controllerClassName()).isEqualTo("UserController");
  }

  public void testCachesPerFileUntilFileChanges() {
    // Given a scanned file.
    PsiFile psiFile = myFixture.addFileToProject(CONTROLLER_PATH, PsiFixtures.USER_CONTROLLER);
    EndpointIndexService service = new EndpointIndexService(getProject());

    List<EndpointDescriptor> first = service.endpointsInFile(psiFile.getVirtualFile());
    List<EndpointDescriptor> second = service.endpointsInFile(psiFile.getVirtualFile());

    // When queried twice without changes, then the same cached list is returned.
    assertThat(second).isSameAs(first);

    // When the file is overwritten, then the scan produces the new endpoints.
    Document document = FileDocumentManager.getInstance().getDocument(psiFile.getVirtualFile());
    WriteCommandAction.runWriteCommandAction(getProject(), () -> document.setText(PsiFixtures.PLAIN_CLASS));
    PsiDocumentManager.getInstance(getProject()).commitDocument(document);
    FileDocumentManager.getInstance().saveDocument(document);
    List<EndpointDescriptor> rescan = service.endpointsInFile(psiFile.getVirtualFile());

    assertThat(rescan).isEmpty();
    assertThat(rescan).isNotSameAs(first);
  }

  public void testInvalidateDropsTheFileCache() {
    // Given a scanned file.
    PsiFile psiFile = myFixture.addFileToProject(CONTROLLER_PATH, PsiFixtures.USER_CONTROLLER);
    EndpointIndexService service = new EndpointIndexService(getProject());
    List<EndpointDescriptor> first = service.endpointsInFile(psiFile.getVirtualFile());

    // When the file is invalidated, then the next query rescans it without losing results.
    service.invalidate(psiFile.getVirtualFile());
    List<EndpointDescriptor> rescan = service.endpointsInFile(psiFile.getVirtualFile());

    assertThat(rescan).isNotSameAs(first);
    assertThat(rescan).hasSize(4);
  }

  public void testInvalidateAllEmptiesTheWholeCache() {
    // Given a scanned file.
    PsiFile psiFile = myFixture.addFileToProject(CONTROLLER_PATH, PsiFixtures.USER_CONTROLLER);
    EndpointIndexService service = new EndpointIndexService(getProject());
    List<EndpointDescriptor> first = service.endpointsInFile(psiFile.getVirtualFile());

    // When invalidateAll runs and the file is queried again, then a fresh scan happens.
    service.invalidateAll();
    List<EndpointDescriptor> rescan = service.endpointsInFile(psiFile.getVirtualFile());

    assertThat(rescan).isNotSameAs(first);
    assertThat(rescan).hasSize(4);
  }

  public void testProjectIndexServiceFindsEndpointsThroughTheIndex() {
    // Given an indexed controller and the project-level service.
    myFixture.addFileToProject(CONTROLLER_PATH, PsiFixtures.USER_CONTROLLER);
    EndpointIndexService service = new EndpointIndexService(getProject());

    List<EndpointDescriptor> endpoints = service.endpoints();

    // When all project sources are scanned, then the endpoint is found through the file index.
    assertThat(endpoints).isNotEmpty();
    assertThat(endpoints.get(0).controllerClassName()).isEqualTo("UserController");
  }
}