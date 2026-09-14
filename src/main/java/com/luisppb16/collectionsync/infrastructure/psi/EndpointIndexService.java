/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */
package com.luisppb16.collectionsync.infrastructure.psi;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.luisppb16.collectionsync.application.service.EndpointDiscoveryPort;
import com.luisppb16.collectionsync.domain.model.EndpointDescriptor;
import com.luisppb16.collectionsync.infrastructure.psi.common.DtoShapeResolver;
import com.luisppb16.collectionsync.infrastructure.psi.common.EndpointScanner;
import com.luisppb16.collectionsync.infrastructure.psi.spring.SpringEndpointScanner;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Project service that keeps the endpoints discovered in the Java sources, cached per
 * {@link VirtualFile}. Entries are validated against the file modification stamp, so edited
 * files are rescanned on the next query and untouched files stay cached. Returns an empty list
 * while indexing (dumb mode), never blocks, and degrades gracefully on IDEs without Java support.
 */
@Service(Service.Level.PROJECT)
public final class EndpointIndexService implements EndpointDiscoveryPort {

  private record FileEndpoints(long modificationStamp, List<EndpointDescriptor> endpoints) {

    private FileEndpoints {
      Objects.requireNonNull(endpoints, "endpoints");
    }
  }

  private final Project project;
  private final List<EndpointScanner> scanners;
  private final Map<VirtualFile, FileEndpoints> cacheByFile = new ConcurrentHashMap<>();

  public EndpointIndexService(Project project) {
    this.project = Objects.requireNonNull(project, "project");
    this.scanners = scannersFor(project);
  }

  /** @return the project instance of the service. */
  public static EndpointIndexService getInstance(Project project) {
    return project.getService(EndpointIndexService.class);
  }

  private static List<EndpointScanner> scannersFor(Project project) {
    DtoShapeResolver dtoShapeResolver = new DtoShapeResolver(project);
    return List.of(new SpringEndpointScanner(dtoShapeResolver));
  }

  /** @return every endpoint in the project sources; empty while indexing. */
  public List<EndpointDescriptor> endpoints() {
    if (DumbService.isDumb(project)) {
      return List.of();
    }
    return runRead(() -> {
      List<EndpointDescriptor> endpoints = new ArrayList<>();
      for (VirtualFile file : FileTypeIndex.getFiles(JavaFileType.INSTANCE, GlobalSearchScope.projectScope(project))) {
        endpoints.addAll(endpointsInFile(file));
      }
      return endpoints;
    });
  }

  /**
   * @param file a Java source file
   * @return the endpoints declared in it, from cache when the file has not changed since the last scan
   */
  public List<EndpointDescriptor> endpointsInFile(VirtualFile file) {
    long modificationStamp = file.getModificationStamp();
    FileEndpoints cached = cacheByFile.get(file);
    if (cached != null && cached.modificationStamp() == modificationStamp) {
      return cached.endpoints();
    }
    FileEndpoints entry = cacheByFile.compute(file, (ignored, previous) -> scanFile(file, modificationStamp));
    return entry.endpoints();
  }

  private FileEndpoints scanFile(VirtualFile file, long modificationStamp) {
    List<EndpointDescriptor> endpoints = runRead(() -> {
      PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
      if (!(psiFile instanceof PsiJavaFile)) {
        return List.of();
      }
      List<EndpointDescriptor> result = new ArrayList<>();
      for (PsiClass psiClass : PsiTreeUtil.collectElementsOfType(psiFile, PsiClass.class)) {
        for (EndpointScanner scanner : scanners) {
          result.addAll(scanner.scanClass(psiClass));
        }
      }
      return result;
    });
    return new FileEndpoints(modificationStamp, endpoints);
  }

  private static <T> T runRead(Computable<T> computable) {
    return ApplicationManager.getApplication().runReadAction(computable);
  }

  /** Drops the cached scan of the given file (used when the file is deleted or moved). */
  public void invalidate(VirtualFile file) {
    cacheByFile.remove(file);
  }

  /** Drops the whole cache, forcing a rescan of every file on the next query. */
  public void invalidateAll() {
    cacheByFile.clear();
  }
}