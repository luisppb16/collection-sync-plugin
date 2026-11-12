/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */

package com.luisppb16.collectionsync.source;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.luisppb16.collectionsync.domain.model.ApiEndpoint;
import com.luisppb16.collectionsync.domain.model.HttpMethod;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.name.FqName;
import org.jetbrains.kotlin.name.Name;
import org.jetbrains.kotlin.psi.KtAnnotationEntry;
import org.jetbrains.kotlin.psi.KtClass;
import org.jetbrains.kotlin.psi.KtDeclaration;
import org.jetbrains.kotlin.psi.KtFile;
import org.jetbrains.kotlin.psi.KtNamedFunction;

/**
 * Best-effort endpoint discovery for Kotlin controllers (Spring, JAX-RS and Micronaut annotations).
 * It is loaded only when the Kotlin plugin is installed; without it, the Java scan is unchanged.
 *
 * <p>Annotations are matched by their short name and paths are read from the string literals of the
 * annotation text. This deliberately over-includes rather than missing an endpoint. Ktor DSL routes
 * are out of scope because they are plain function calls, not annotations.
 */
public final class KotlinEndpointSource implements EndpointSource {

  private static final Pattern QUOTED = Pattern.compile("\"((?:\\\\.|[^\"\\\\])*)\"");

  private static final Set<String> CONTROLLERS =
      Set.of("RestController", "Controller", "Path", "RequestMapping");

  private static final Set<String> BASE_PATH_ANNOTATIONS =
      Set.of("RequestMapping", "Controller", "Path");

  private static HttpMethod verbOf(String name) {
    String base =
        name.endsWith("Mapping") ? name.substring(0, name.length() - "Mapping".length()) : name;
    try {
      return HttpMethod.of(base);
    } catch (IllegalArgumentException notAVerb) {
      return null;
    }
  }

  private static List<ApiEndpoint> collectFiles(Project project) {
    List<ApiEndpoint> endpoints = new ArrayList<>();
    for (VirtualFile file :
        FilenameIndex.getAllFilesByExt(project, "kt", GlobalSearchScope.projectScope(project))) {
      PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
      if (psiFile instanceof KtFile ktFile) {
        for (KtDeclaration declaration : ktFile.getDeclarations()) {
          if (declaration instanceof KtClass ktClass) {
            endpoints.addAll(endpointsOf(ktClass));
          }
        }
      }
    }
    return endpoints.stream().distinct().toList();
  }

  private static List<ApiEndpoint> endpointsOf(KtClass ktClass) {
    List<KtAnnotationEntry> classAnnotations = ktClass.getAnnotationEntries();
    if (classAnnotations.stream()
        .noneMatch(annotation -> CONTROLLERS.contains(shortName(annotation)))) {
      return List.of();
    }
    List<String> basePaths = pathsOf(classAnnotations, BASE_PATH_ANNOTATIONS);
    if (basePaths.isEmpty()) {
      basePaths = List.of("");
    }
    String owner = ownerOf(ktClass);
    String module = moduleOf(ktClass);
    List<ApiEndpoint> endpoints = new ArrayList<>();
    for (KtDeclaration declaration : ktClass.getDeclarations()) {
      if (declaration instanceof KtNamedFunction function) {
        endpoints.addAll(endpointsOf(function, basePaths, owner, module));
      } else if (declaration instanceof KtClass nestedClass) {
        endpoints.addAll(endpointsOf(nestedClass));
      }
    }
    return endpoints;
  }

  private static List<ApiEndpoint> endpointsOf(
      KtNamedFunction function, List<String> basePaths, String owner, String module) {
    List<HttpMethod> methods = new ArrayList<>();
    List<String> methodPaths = new ArrayList<>();
    for (KtAnnotationEntry annotation : function.getAnnotationEntries()) {
      String name = shortName(annotation);
      HttpMethod verb = verbOf(name);
      if (verb != null) {
        methods.add(verb);
        addPaths(methodPaths, annotation);
      } else if ("RequestMapping".equals(name)) {
        methods.add(HttpMethod.ANY);
        addPaths(methodPaths, annotation);
      }
    }
    if (methods.isEmpty()) {
      return List.of();
    }
    if (methodPaths.isEmpty()) {
      methodPaths.add("");
    }
    return ControllerEndpointSource.expand(basePaths, methodPaths, methods, owner, module, null);
  }

  private static void addPaths(List<String> paths, KtAnnotationEntry annotation) {
    List<String> declared = quotedStrings(annotation.getText());
    paths.addAll(declared.isEmpty() ? List.of("") : declared);
  }

  private static List<String> pathsOf(
      List<KtAnnotationEntry> annotations, Set<String> annotationNames) {
    List<String> paths = new ArrayList<>();
    for (KtAnnotationEntry annotation : annotations) {
      if (annotationNames.contains(shortName(annotation))) {
        paths.addAll(quotedStrings(annotation.getText()));
      }
    }
    return paths.stream().distinct().toList();
  }

  private static List<String> quotedStrings(String text) {
    List<String> values = new ArrayList<>();
    Matcher matcher = QUOTED.matcher(text);
    while (matcher.find()) {
      values.add(matcher.group(1));
    }
    return values;
  }

  private static String shortName(KtAnnotationEntry annotation) {
    Name name = annotation.getShortName();
    return name == null ? "" : name.asString();
  }

  private static String ownerOf(KtClass ktClass) {
    FqName fqName = ktClass.getFqName();
    return fqName == null ? "" : fqName.asString();
  }

  private static String moduleOf(KtClass ktClass) {
    Module module = ModuleUtilCore.findModuleForPsiElement(ktClass);
    return module == null ? "" : module.getName();
  }

  @Override
  public @NotNull List<ApiEndpoint> collect(@NotNull Project project) {
    Objects.requireNonNull(project);
    return ReadAction.nonBlocking(() -> collectFiles(project))
        .inSmartMode(project)
        .executeSynchronously();
  }
}
