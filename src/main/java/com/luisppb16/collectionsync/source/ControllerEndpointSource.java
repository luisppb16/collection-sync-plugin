/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */

package com.luisppb16.collectionsync.source;

import com.intellij.openapi.application.NonBlockingReadAction;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.luisppb16.collectionsync.domain.model.ApiEndpoint;
import com.luisppb16.collectionsync.scan.EndpointAnnotationResolver;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * {@link EndpointSource} that scans the source code of every project module for controller classes
 * and extracts their endpoints through framework-specific {@link EndpointAnnotationResolver}s
 * (Spring MVC, JAX-RS...).
 *
 * <h2>The scan algorithm, step by step</h2>
 *
 * <ol>
 *   <li><b>Module iteration.</b> Every module of the project is visited ({@link
 *       ModuleManager#getModules()}), so the same class name in different modules produces one
 *       endpoint per module.
 *   <li><b>Candidate discovery.</b> For each module, {@link PsiShortNamesCache} lists every class
 *       name visible inside the module scope; this works on stub indexes too, without requiring any
 *       framework annotation to be resolvable to real source.
 *   <li><b>Framework detection.</b> A class is a controller when at least one configured resolver
 *       claims it through {@link EndpointAnnotationResolver#isController(PsiClass)}.
 *   <li><b>Endpoint extraction.</b> For every method of a controller class whose {@link
 *       EndpointAnnotationResolver#methods(PsiMethod)} is not empty, one {@link ApiEndpoint} per
 *       declared HTTP method is produced, with the full path being the class base path joined to
 *       the method path with exactly one {@code /} (missing and redundant slashes are trimmed), the
 *       qualified class name as owner, the module name, and the handler {@link PsiMethod} for
 *       navigation. Endpoints whose joined path would be blank are skipped.
 * </ol>
 *
 * <h2>Dumb-mode safety</h2>
 *
 * <p>Discovery relies on stub indexes ({@link PsiShortNamesCache}), which are only queryable in
 * smart mode, so the scan is protected in three layers:
 *
 * <ol>
 *   <li><b>Defer at the origin.</b> {@code CoverageService} does not start a scan while the IDE is
 *       in dumb mode; it notifies the user and defers the scan with {@link
 *       DumbService#runWhenSmart(Runnable)} until indexing finishes.
 *   <li><b>Smart-mode read action.</b> {@link #collect(Project)} runs its read action through
 *       {@link ReadAction#nonBlocking(Callable)} with {@link
 *       NonBlockingReadAction#inSmartMode(Project)}, which waits for smart mode before the stub
 *       indexes are touched (and retries if indexing restarts mid-scan), instead of crashing with
 *       {@link IndexNotReadyException}.
 *   <li><b>Per-module degradation.</b> If indexing starts in the middle of a scan and a module
 *       lookup still fails, {@link #collectSkippingFailedModules(List, Function, BiConsumer)} skips
 *       only that module (logged at WARN level with its name) and keeps the endpoints of the
 *       remaining modules; a single module failure never aborts the whole scan.
 * </ol>
 */
public final class ControllerEndpointSource implements EndpointSource {

  private static final Logger LOG = Logger.getInstance(ControllerEndpointSource.class);

  private final List<EndpointAnnotationResolver> resolvers;

  /**
   * @param resolvers framework resolvers applied to every candidate class; must not be null and
   *     must not contain null elements
   */
  public ControllerEndpointSource(@NotNull List<EndpointAnnotationResolver> resolvers) {
    Objects.requireNonNull(resolvers);
    this.resolvers = List.copyOf(resolvers);
  }

  /**
   * Layer 3 of the dumb-mode defence: degrades per module instead of aborting the whole scan.
   *
   * <p>Scans the given modules one by one and accumulates their endpoints. When a module fails with
   * a {@link ProcessCanceledException}, the failure is rethrown (the platform contract forbids
   * swallowing it); when it fails with {@link IndexNotReadyException}, raised when indexing starts
   * in the middle of a scan, the failure is reported through {@code onModuleFailure} and the
   * remaining modules still contribute to the result. Any other {@link RuntimeException} is not a
   * degradation case: it propagates to fail fast on genuine bugs instead of publishing a silently
   * incomplete report.
   *
   * <p>Each module stream is materialised eagerly so the failure is attributed to its own module
   * instead of escaping lazily into a sibling's evaluation. Package-private and free of PSI calls,
   * so the degradation can be unit tested with stub scan functions.
   *
   * @param modules modules to scan, in order; must not be null
   * @param endpointsOfModule scan of a single module, already inside a read action; must not be
   *     null
   * @param onModuleFailure invoked with the module and its failure when that module is skipped;
   *     must not be null
   * @return the endpoints of every module that scanned successfully; never null
   */
  static List<ApiEndpoint> collectSkippingFailedModules(
      @NotNull List<Module> modules,
      @NotNull Function<Module, List<ApiEndpoint>> endpointsOfModule,
      @NotNull BiConsumer<Module, RuntimeException> onModuleFailure) {
    Objects.requireNonNull(modules);
    Objects.requireNonNull(endpointsOfModule);
    Objects.requireNonNull(onModuleFailure);
    List<ApiEndpoint> endpoints = new ArrayList<>();
    for (Module module : modules) {
      try {
        endpoints.addAll(endpointsOfModule.apply(module));
      } catch (ProcessCanceledException cancelled) {
        throw cancelled;
      } catch (IndexNotReadyException indexesNotReady) {
        onModuleFailure.accept(module, indexesNotReady);
      }
    }
    return List.copyOf(endpoints);
  }

  private static void logSkippedModule(@NotNull Module module, @NotNull RuntimeException failure) {
    LOG.warn(
        "Endpoint scan skipped the module '"
            + module.getName()
            + "' because its index is not ready: "
            + failure,
        failure);
  }

  /**
   * Joins a base path and a method path with exactly one {@code /} between them, tolerating missing
   * or redundant slashes on either side. Returns an empty string only when both inputs are empty,
   * which the caller treats as "no path declared" and skips.
   */
  private static String joinPath(String basePath, String methodPath) {
    String base = stripSlashes(basePath);
    String method = stripSlashes(methodPath);
    String joined =
        Stream.of(base, method)
            .filter(segment -> !segment.isEmpty())
            .collect(Collectors.joining("/"));
    return joined.isEmpty() ? "" : "/" + joined;
  }

  private static String stripSlashes(@Nullable String path) {
    if (path == null) {
      return "";
    }
    return path.strip().replaceAll("^/+|/+$", "");
  }

  @Override
  public @NotNull List<ApiEndpoint> collect(@NotNull Project project) {
    // collectModules already WARN-logs every skipped module: the interface entry point needs no
    // extra failure callback.
    return collect(project, (skippedModule, failure) -> {});
  }

  /**
   * Same as {@link #collect(Project)}, but every module skipped by the per-module degradation is
   * additionally reported through {@code onModuleFailure}, so callers can surface the degradation
   * to the user instead of leaving it in the IDE log only. The WARN log entry is written for every
   * skipped module either way.
   *
   * @param project current project; must not be null
   * @param onModuleFailure invoked for every module skipped by the degradation; must not be null
   * @return the endpoints of every module that scanned successfully; never null
   */
  public @NotNull List<ApiEndpoint> collect(
      @NotNull Project project, @NotNull BiConsumer<Module, RuntimeException> onModuleFailure) {
    Objects.requireNonNull(project);
    Objects.requireNonNull(onModuleFailure, "onModuleFailure must not be null");
    // PSI access from a background thread requires a read action, and the stub index behind
    // PsiShortNamesCache is only queryable in smart mode: inSmartMode waits for smart mode
    // (and retries if indexing restarts mid-scan) instead of failing with
    // IndexNotReadyException. This is the non-deprecated equivalent of the old
    // DumbService.runReadActionInSmartMode, which is deprecated in the 2026.2 platform.
    return ReadAction.nonBlocking(() -> collectModules(project, onModuleFailure))
        .inSmartMode(project)
        .executeSynchronously();
  }

  private List<ApiEndpoint> collectModules(
      @NotNull Project project, @NotNull BiConsumer<Module, RuntimeException> onModuleFailure) {
    return collectSkippingFailedModules(
        List.of(ModuleManager.getInstance(project).getModules()),
        module -> endpointsOfModule(module).toList(),
        (module, failure) -> {
          logSkippedModule(module, failure);
          onModuleFailure.accept(module, failure);
        });
  }

  private Stream<ApiEndpoint> endpointsOfModule(Module module) {
    PsiShortNamesCache namesCache = PsiShortNamesCache.getInstance(module.getProject());
    return Arrays.stream(namesCache.getAllClassNames())
        .distinct()
        .flatMap(className -> endpointsOfNamedClass(className, module, namesCache));
  }

  private Stream<ApiEndpoint> endpointsOfNamedClass(
      String className, Module module, PsiShortNamesCache namesCache) {
    return Arrays.stream(
            namesCache.getClassesByName(className, GlobalSearchScope.moduleScope(module)))
        .flatMap(psiClass -> endpointsOfController(psiClass, module));
  }

  private Stream<ApiEndpoint> endpointsOfController(PsiClass psiClass, Module module) {
    return resolvers.stream()
        .filter(resolver -> resolver.isController(psiClass))
        .flatMap(resolver -> endpointsOfMethods(psiClass, module, resolver));
  }

  private Stream<ApiEndpoint> endpointsOfMethods(
      PsiClass psiClass, Module module, EndpointAnnotationResolver resolver) {
    String basePath = resolver.basePath(psiClass);
    String ownerClass = Optional.ofNullable(psiClass.getQualifiedName()).orElse("");
    return Arrays.stream(psiClass.getAllMethods())
        .flatMap(psiMethod -> endpointsOfMethod(psiMethod, basePath, ownerClass, module, resolver));
  }

  private Stream<ApiEndpoint> endpointsOfMethod(
      PsiMethod psiMethod,
      String basePath,
      String ownerClass,
      Module module,
      EndpointAnnotationResolver resolver) {
    String path = joinPath(basePath, resolver.methodPath(psiMethod));
    if (path.isEmpty()) {
      return Stream.empty();
    }
    return resolver.methods(psiMethod).stream()
        .map(
            httpMethod ->
                new ApiEndpoint(httpMethod, path, ownerClass, module.getName(), psiMethod));
  }
}
