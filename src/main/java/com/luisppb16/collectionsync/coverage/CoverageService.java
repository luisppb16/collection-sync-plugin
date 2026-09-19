/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */

package com.luisppb16.collectionsync.coverage;

import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.luisppb16.collectionsync.collection.CollectionParsers;
import com.luisppb16.collectionsync.domain.model.ApiEndpoint;
import com.luisppb16.collectionsync.domain.model.ApiRequest;
import com.luisppb16.collectionsync.domain.model.CoverageResult;
import com.luisppb16.collectionsync.domain.model.ExclusionRule;
import com.luisppb16.collectionsync.domain.service.CoverageEngine;
import com.luisppb16.collectionsync.i18n.EndpointCoverageBundle;
import com.luisppb16.collectionsync.scan.JaxRsAnnotationResolver;
import com.luisppb16.collectionsync.scan.SpringAnnotationResolver;
import com.luisppb16.collectionsync.settings.EndpointCoverageSettings;
import com.luisppb16.collectionsync.source.ControllerEndpointSource;
import com.luisppb16.collectionsync.source.OpenApiEndpointSource;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Project-level service that runs the endpoint coverage calculation in the background and keeps the
 * last result for the tool window.
 *
 * <p>A scan never aborts because of one broken collection file: every file is parsed independently,
 * the failures are recorded as human-readable messages ({@link #lastErrors()}) and the remaining
 * files still contribute to the report.
 *
 * <p>A scan is also never started while the project is in dumb mode (indexing): it is deferred with
 * {@link DumbService#runWhenSmart(Runnable)} until the indexes are queryable, because the
 * controller scan reads stub indexes that only work in smart mode.
 */
@Service(Service.Level.PROJECT)
public final class CoverageService {

  private static final String TASK_TITLE = EndpointCoverageBundle.message("service.task.title");

  private static final String NOTIFICATION_GROUP_ID = "CollectionSync Notifications";

  private final Project project;

  /** Stays {@code true} while one deferred scan is registered with {@code runWhenSmart}. */
  private final AtomicBoolean pendingSmartScan = new AtomicBoolean(false);

  /** Latest callback of the deferred scans; only meaningful while {@link #pendingSmartScan}. */
  private volatile @Nullable Runnable pendingOnDone;

  /**
   * Published as a single immutable snapshot so readers never see the result of one scan paired
   * with the errors of another. Scans are not serialized: when two run concurrently, the last one
   * to finish wins.
   */
  private volatile ScanOutput lastOutput;

  /**
   * @param project owning project; must not be null
   */
  public CoverageService(@NotNull Project project) {
    this.project = Objects.requireNonNull(project);
  }

  /**
   * Pure orchestration of a scan: parses the collection files, tolerating per-file failures, and
   * computes the coverage of the given endpoints against every collected request. Kept static so it
   * can be tested without a Project.
   *
   * @param endpoints real endpoints of the project; must not be null
   * @param collectionFiles collection files to parse; must not be null
   * @param exclusions user exclusion rules; must not be null
   * @return the report and the list of failures; never null
   */
  public static @NotNull ScanOutput computeScan(
      @NotNull List<ApiEndpoint> endpoints,
      @NotNull List<File> collectionFiles,
      @NotNull List<ExclusionRule> exclusions) {
    return computeScan(endpoints, collectionFiles, exclusions, List.of());
  }

  /**
   * Same as {@link #computeScan(List, List, List)} appending the given scan issues (e.g. modules
   * skipped because their index was not ready) to the recorded errors.
   *
   * @param scanIssues issues raised outside the per-collection parsing; must not be null
   */
  public static @NotNull ScanOutput computeScan(
      @NotNull List<ApiEndpoint> endpoints,
      @NotNull List<File> collectionFiles,
      @NotNull List<ExclusionRule> exclusions,
      @NotNull List<String> scanIssues) {
    Objects.requireNonNull(endpoints);
    Objects.requireNonNull(collectionFiles);
    Objects.requireNonNull(exclusions);
    Objects.requireNonNull(scanIssues);
    List<String> errors = new ArrayList<>(scanIssues);
    List<String> missingPaths = new ArrayList<>();
    List<ApiRequest> requests =
        collectionFiles.stream()
            .flatMap(
                collectionFile -> parseCollection(collectionFile, errors, missingPaths).stream())
            .toList();
    return new ScanOutput(
        CoverageEngine.compute(endpoints, requests, exclusions), errors, missingPaths);
  }

  /**
   * Filters the given collection paths down to the files that still exist on disk, so a dead
   * persisted path can be removed from the settings. Kept static so it can be tested without a
   * project and reused by the tool window.
   *
   * @param paths collection paths to filter; must not be null
   * @return the paths that resolve to an existing regular file; never null
   */
  public static List<String> existingCollectionPaths(@NotNull List<String> paths) {
    Objects.requireNonNull(paths);
    return paths.stream().filter(path -> new File(path).isFile()).toList();
  }

  private static List<File> collectionFilesOf(EndpointCoverageSettings settings) {
    return settings.getCollectionFilePaths().stream().map(File::new).toList();
  }

  private static List<ApiRequest> parseCollection(
      File collectionFile, List<String> errors, List<String> missingPaths) {
    if (!collectionFile.isFile()) {
      errors.add(
          EndpointCoverageBundle.message(
              "service.error.collection.not.found", collectionFile.toString()));
      missingPaths.add(collectionFile.toString());
      return List.of();
    }
    try {
      List<ApiRequest> requests = CollectionParsers.parse(collectionFile);
      if (requests.isEmpty()) {
        // A collection that parses but holds no requests would silently lower the coverage
        // baseline; surfaced as an error so the user can fix or remove the file.
        errors.add(
            EndpointCoverageBundle.message(
                "service.error.collection.empty", collectionFile.getName()));
      }
      return requests;
    } catch (IOException | IllegalArgumentException brokenFile) {
      errors.add(
          EndpointCoverageBundle.message(
              "service.error.collection.parse", collectionFile.getName(), brokenFile.getMessage()));
      return List.of();
    }
  }

  /**
   * Dedupes the registration of {@code runWhenSmart} callbacks: returns {@code true} only for the
   * first request while the given flag is clear, so repeated scan requests in dumb mode never pile
   * up duplicate callbacks. Extracted as a pure method so the dedupe is testable without a project.
   *
   * @param scheduled flag that stays {@code true} while one deferred scan is registered; must not
   *     be null
   * @return true when this call registered the deferred scan, false when one was already pending
   */
  static boolean tryScheduleOnce(@NotNull AtomicBoolean scheduled) {
    return scheduled.compareAndSet(false, true);
  }

  /**
   * Runs a scan on a background thread and, when it finishes, executes the callback on the EDT. EDT
   * only: the dedup of deferred scans relies on the caller, the {@code runWhenSmart} callback and
   * the re-entrant {@code scanAsync} call all running on the same thread, so the deferred callback
   * can never interleave with a new request (enforced with an assertion, enabled in tests and
   * internal builds).
   *
   * <p>When the project is in dumb mode (indexing), the scan is not started at all: the user is
   * notified that the indexes are being built and the scan is deferred with {@link
   * DumbService#runWhenSmart(Runnable)} so it only runs once the indexes are queryable. Repeated
   * requests while dumb are deduped ({@link #tryScheduleOnce(AtomicBoolean)}): only one deferred
   * scan is registered, the latest callback wins and the user is notified only once per dumb cycle.
   *
   * <p>The callback is not executed when the project was closed before the scan finished (see
   * {@link #queueScan(Runnable)}).
   *
   * <p>Concurrent scans are not serialized: each finished scan publishes its own snapshot and the
   * last one to finish stays visible.
   *
   * @param onDone callback invoked (via {@code invokeLater}) after the result is stored; must not
   *     be null
   */
  public void scanAsync(@NotNull Runnable onDone) {
    Objects.requireNonNull(onDone, "onDone must not be null");
    assert ApplicationManager.getApplication().isDispatchThread()
        : "scanAsync must run on the EDT: the deferred-scan dedup is not thread-safe";
    DumbService dumbService = DumbService.getInstance(project);
    if (dumbService.isDumb()) {
      deferScanUntilSmart(onDone, dumbService);
      return;
    }
    queueScan(onDone);
  }

  /**
   * Defers the scan until the project leaves dumb mode, notifying the user first (only once per
   * dumb cycle: a request that does not register the deferred scan is already covered by the
   * pending one). The deferred callback re-enters {@link #scanAsync(Runnable)}, so a fresh
   * dumb-mode cycle re-defers instead of touching half-built indexes.
   */
  private void deferScanUntilSmart(@NotNull Runnable onDone, @NotNull DumbService dumbService) {
    pendingOnDone = onDone;
    if (!tryScheduleOnce(pendingSmartScan)) {
      return;
    }
    notifyIndexesNotReady();
    dumbService.runWhenSmart(
        () -> {
          if (project.isDisposed()) {
            return;
          }
          Runnable deferredOnDone = pendingOnDone;
          pendingOnDone = null;
          pendingSmartScan.set(false);
          if (deferredOnDone != null) {
            scanAsync(deferredOnDone);
          }
        });
  }

  private void queueScan(@NotNull Runnable onDone) {
    new Task.Backgroundable(project, TASK_TITLE, false) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        scan();
        // Expired when the project closes mid-scan: running the callback afterwards would touch
        // notifications and tool windows of a disposed project.
        ApplicationManager.getApplication().invokeLater(onDone, project.getDisposed());
      }
    }.queue();
  }

  private void notifyIndexesNotReady() {
    NotificationGroupManager.getInstance()
        .getNotificationGroup(NOTIFICATION_GROUP_ID)
        .createNotification(
            EndpointCoverageBundle.message("service.notification.indexes.building"),
            NotificationType.WARNING)
        .notify(project);
  }

  /**
   * @return the result of the last finished scan, or null when no scan has run yet
   */
  public @Nullable CoverageResult lastResult() {
    ScanOutput output = lastOutput;
    return output == null ? null : output.result();
  }

  /**
   * @return the issues recorded during the last scan (collection files that failed, with the
   *     reason, and modules skipped by the endpoint source); never null
   */
  public List<String> lastErrors() {
    ScanOutput output = lastOutput;
    return output == null ? List.of() : output.errors();
  }

  /**
   * @return the collection files of the last scan that no longer exist on disk; never null
   */
  public List<String> lastMissingCollectionPaths() {
    ScanOutput output = lastOutput;
    return output == null ? List.of() : output.missingCollectionPaths();
  }

  /**
   * @return a snapshot of the last finished scan (result, issues and missing paths), or null when
   *     no scan has run yet; readers wanting a consistent trio must read this once instead of the
   *     three getters above
   */
  public @Nullable ScanOutput lastScan() {
    return lastOutput;
  }

  /**
   * Runs a full scan synchronously and stores its result.
   *
   * @return the freshly computed report; never null
   */
  public @NotNull CoverageResult scan() {
    EndpointCoverageSettings settings = EndpointCoverageSettings.getInstance(project);
    List<String> scanIssues = new ArrayList<>();
    List<ApiEndpoint> endpoints = collectEndpoints(settings, scanIssues);
    ScanOutput output =
        computeScan(endpoints, collectionFilesOf(settings), settings.toRules(), scanIssues);
    lastOutput = output;
    return output.result();
  }

  private List<ApiEndpoint> collectEndpoints(
      EndpointCoverageSettings settings, List<String> scanIssues) {
    return switch (settings.getSourceType()) {
      case OPEN_API -> new OpenApiEndpointSource(settings.getOpenApiFilePath()).collect(project);
      case CONTROLLER_ANNOTATIONS ->
          new ControllerEndpointSource(
                  List.of(new SpringAnnotationResolver(), new JaxRsAnnotationResolver()))
              .collect(
                  project,
                  (skippedModule, failure) ->
                      scanIssues.add(
                          EndpointCoverageBundle.message(
                              "service.error.module.skipped",
                              skippedModule.getName(),
                              String.valueOf(failure.getMessage()))));
    };
  }

  /**
   * Outcome of one scan: the coverage report, the recorded issues (per-file parsing errors and
   * modules skipped by the endpoint source) and the paths of the missing collection files.
   */
  public record ScanOutput(
      CoverageResult result, List<String> errors, List<String> missingCollectionPaths) {

    public ScanOutput {
      Objects.requireNonNull(result, "result must not be null");
      errors = List.copyOf(errors);
      missingCollectionPaths = List.copyOf(missingCollectionPaths);
    }
  }
}
