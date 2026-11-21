/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */

package com.luisppb16.collectionsync.source;

import static org.assertj.core.api.Assertions.assertThat;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.luisppb16.collectionsync.test.PsiTestCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ControllerEndpointSource module ownership")
class ControllerEndpointSourceOwnershipTest extends PsiTestCase {

  @Test
  @DisplayName(
      "Given a class in the module content, when module ownership is checked, then it is accepted")
  void acceptsClassesInModuleContent() {
    getFixture().addClass("package com.example; public class OwnedController {}");

    assertThat(belongsToFixtureModule("com.example.OwnedController")).isTrue();
  }

  @Test
  @DisplayName(
      "Given a class from a dependency, when module ownership is checked, then it is rejected")
  void rejectsClassesOutsideModuleContent() {
    assertThat(belongsToFixtureModule("java.lang.String")).isFalse();
  }

  private boolean belongsToFixtureModule(String qualifiedName) {
    Project project = getFixture().getProject();
    Module module = getFixture().getModule();
    ProjectFileIndex fileIndex = ProjectFileIndex.getInstance(project);
    return ApplicationManager.getApplication()
        .runReadAction(
            (Computable<Boolean>)
                () -> {
                  PsiClass psiClass =
                      JavaPsiFacade.getInstance(project)
                          .findClass(qualifiedName, GlobalSearchScope.allScope(project));
                  return psiClass != null
                      && ControllerEndpointSource.belongsToModule(psiClass, module, fileIndex);
                });
  }
}
