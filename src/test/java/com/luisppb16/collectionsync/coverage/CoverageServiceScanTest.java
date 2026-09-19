/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */

package com.luisppb16.collectionsync.coverage;

import static org.assertj.core.api.Assertions.assertThat;

import com.luisppb16.collectionsync.domain.model.CoverageResult;
import com.luisppb16.collectionsync.i18n.EndpointCoverageBundle;
import com.luisppb16.collectionsync.settings.EndpointCoverageSettings;
import com.luisppb16.collectionsync.test.PsiTestCase;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("CoverageService.scan")
class CoverageServiceScanTest extends PsiTestCase {

  private static final String BROKEN_COLLECTION = "definitely not json";

  private static final String VALID_COLLECTION =
      """
            {"info": {"name": "Valid API"}, "item": [
              {"name": "Ping", "request": {"method": "GET", "url": "/ping"}}
            ]}""";

  @TempDir Path tempDir;

  @Test
  @DisplayName(
      "Given a broken collection file, when a scan runs, then the result and the error are stored on the service")
  void storesResultAndErrorOfBrokenCollection() throws IOException {
    Path broken = write("broken.json", BROKEN_COLLECTION);
    configureCollections(broken.toString());
    CoverageService service = new CoverageService(getFixture().getProject());

    CoverageResult result = service.scan();

    assertThat(service.lastResult()).isSameAs(result);
    assertThat(service.lastErrors()).hasSize(1);
    assertThat(service.lastErrors().getFirst())
        .startsWith(
            // Resolved through the bundle so the assertion holds in any locale.
            EndpointCoverageBundle.message("service.error.collection.parse", "broken.json", ""));
    assertThat(result.rows()).isEmpty();
  }

  @Test
  @DisplayName(
      "Given a valid collection file, when a scan runs, then the result counts its requests and no errors are stored")
  void storesCleanResultOfValidCollection() throws IOException {
    Path valid = write("valid.json", VALID_COLLECTION);
    configureCollections(valid.toString());
    CoverageService service = new CoverageService(getFixture().getProject());

    CoverageResult result = service.scan();

    assertThat(service.lastResult()).isSameAs(result);
    assertThat(service.lastErrors()).isEmpty();
    assertThat(result.orphanCount()).isEqualTo(1);
    assertThat(result.collectionCount()).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "Given a collection file that no longer exists, when a scan runs, then the missing path is stored on the service")
  void storesMissingCollectionPaths() {
    Path missing = tempDir.resolve("deleted.json");
    configureCollections(missing.toString());
    CoverageService service = new CoverageService(getFixture().getProject());

    CoverageResult result = service.scan();

    assertThat(service.lastMissingCollectionPaths()).containsExactly(missing.toString());
    assertThat(service.lastErrors()).hasSize(1);
    assertThat(result.coveredCount()).isZero();
    assertThat(result.uncoveredCount()).isZero();
  }

  /**
   * Unit-level coverage of the dumb-mode dedup. Only the extracted pure method {@link
   * CoverageService#tryScheduleOnce(AtomicBoolean)} is exercised here; the rest of the defer flow
   * is deliberately not unit-tested because it depends on platform services of the running project:
   * the "indexes building" notification ({@code NotificationGroupManager}), the {@code
   * DumbService.runWhenSmart} registration (including the "latest callback wins" overwrite of the
   * pending callback) and the re-entrant {@code scanAsync} call that re-defers when a new dumb
   * cycle starts at the callback moment. Driving all of that would need an integration test that
   * toggles real dumb mode in the fixture; the registration behaviour itself is guarded by the pure
   * dedupe and the EDT-only contract documented in {@code CoverageService#scanAsync}.
   */
  @Test
  @DisplayName(
      "Given a deferred scan already scheduled, when more scans are requested while dumb, "
          + "then the runWhenSmart registration is deduped until the flag is cleared")
  void dedupesDeferredSmartScanRegistrations() {
    AtomicBoolean scheduled = new AtomicBoolean(false);

    assertThat(CoverageService.tryScheduleOnce(scheduled))
        .as("the first request while dumb registers the deferred scan")
        .isTrue();
    assertThat(CoverageService.tryScheduleOnce(scheduled))
        .as("further requests while dumb must not pile up duplicate runWhenSmart callbacks")
        .isFalse();

    scheduled.set(false);

    assertThat(CoverageService.tryScheduleOnce(scheduled))
        .as(
            "the flag is cleared when the deferred scan runs, so a later dumb-mode cycle "
                + "can register again")
        .isTrue();
  }

  private Path write(String fileName, String content) throws IOException {
    Path path = tempDir.resolve(fileName);
    Files.writeString(path, content);
    return path;
  }

  private void configureCollections(String... paths) {
    EndpointCoverageSettings.State state = new EndpointCoverageSettings.State();
    state.collectionFilePaths.addAll(Arrays.asList(paths));
    EndpointCoverageSettings.getInstance(getFixture().getProject()).setState(state);
  }
}
