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
 * <p>A scan is also never started while the project is in dumb mode (indexing): it is deferred
 * with {@link DumbService#runWhenSmart(Runnable)} until the indexes are queryable, because the
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
    Objects.requireNonNull(endpoints);
    Objects.requireNonNull(collectionFiles);
    Objects.requireNonNull(exclusions);
    List<String> errors = new ArrayList<>();
    List<ApiRequest> requests =
        collectionFiles.stream()
            .flatMap(collectionFile -> parseCollection(collectionFile, errors).stream())
            .toList();
    return new ScanOutput(CoverageEngine.compute(endpoints, requests, exclusions), errors);
  }

  private static List<File> collectionFilesOf(EndpointCoverageSettings settings) {
    return settings.getCollectionFilePaths().stream().map(File::new).toList();
  }

  private static List<ApiRequest> parseCollection(File collectionFile, List<String> errors) {
    if (!collectionFile.isFile()) {
      errors.add(EndpointCoverageBundle.message(
          "service.error.collection.not.found", collectionFile.toString()));
      return List.of();
    }
    try {
      return CollectionParsers.forFile(collectionFile).parse(collectionFile);
    } catch (IOException | IllegalArgumentException brokenFile) {
      errors.add(EndpointCoverageBundle.message(
          "service.error.collection.parse", collectionFile.getName(), brokenFile.getMessage()));
      return List.of();
    }
  }

  /**
   * Runs a scan on a background thread and, when it finishes, executes the callback on the EDT.
   * EDT only: the dedup of deferred scans relies on the caller, the {@code runWhenSmart} callback
   * and the re-entrant {@code scanAsync} call all running on the same thread, so the deferred
   * callback can never interleave with a new request (enforced with an assertion, enabled in
   * tests and internal builds).
   *
   * <p>When the project is in dumb mode (indexing), the scan is not started at all: the user is
   * notified that the indexes are being built and the scan is deferred with
   * {@link DumbService#runWhenSmart(Runnable)} so it only runs once the indexes are queryable.
   * Repeated requests while dumb are deduped ({@link #tryScheduleOnce(AtomicBoolean)}): only one
   * deferred scan is registered, the latest callback wins and the user is notified only once per
   * dumb cycle.
   *
   * <p>The callback is not executed when the project was closed before the scan finished
   * (see {@link #queueScan(Runnable)}).
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
    dumbService.runWhenSmart(() -> {
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

  /**
   * Dedupes the registration of {@code runWhenSmart} callbacks: returns {@code true} only for the
   * first request while the given flag is clear, so repeated scan requests in dumb mode never
   * pile up duplicate callbacks. Extracted as a pure method so the dedupe is testable without a
   * project.
   *
   * @param scheduled flag that stays {@code true} while one deferred scan is registered; must not
   *     be null
   * @return true when this call registered the deferred scan, false when one was already pending
   */
  static boolean tryScheduleOnce(@NotNull AtomicBoolean scheduled) {
    return scheduled.compareAndSet(false, true);
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
   * @return the collection files that failed during the last scan, with the reason; never null
   */
  public List<String> lastErrors() {
    ScanOutput output = lastOutput;
    return output == null ? List.of() : output.errors();
  }

  /**
   * Runs a full scan synchronously and stores its result.
   *
   * @return the freshly computed report; never null
   */
  public @NotNull CoverageResult scan() {
    EndpointCoverageSettings settings = EndpointCoverageSettings.getInstance(project);
    ScanOutput output =
        computeScan(collectEndpoints(settings), collectionFilesOf(settings), settings.toRules());
    lastOutput = output;
    return output.result();
  }

  private List<ApiEndpoint> collectEndpoints(EndpointCoverageSettings settings) {
    return switch (settings.getSourceType()) {
      case OPEN_API -> new OpenApiEndpointSource(settings.getOpenApiFilePath()).collect(project);
      case CONTROLLER_ANNOTATIONS ->
          new ControllerEndpointSource(
                  List.of(new SpringAnnotationResolver(), new JaxRsAnnotationResolver()))
              .collect(project);
    };
  }

  /** Outcome of one scan: the coverage report plus the per-file parsing errors. */
  public record ScanOutput(CoverageResult result, List<String> errors) {

    public ScanOutput {
      Objects.requireNonNull(result, "result must not be null");
      errors = List.copyOf(errors);
    }
  }
}
