/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */

package com.luisppb16.collectionsync.coverage;

import static org.assertj.core.api.Assertions.assertThat;

import com.luisppb16.collectionsync.coverage.CoverageService.ScanOutput;
import com.luisppb16.collectionsync.i18n.EndpointCoverageBundle;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("CoverageService.computeScan")
class CoverageServiceTest {

  private static final String VALID_COLLECTION =
      """
            {"info": {"name": "Valid API"}, "item": [
              {"name": "Ping", "request": {"method": "GET", "url": "/ping"}},
              {"name": "Create user", "request": {"method": "POST", "url": "/users"}}
            ]}""";

  private static final String BROKEN_COLLECTION = "definitely not json";

  @TempDir Path tempDir;

  @Test
  @DisplayName(
      "Given a missing file, a broken file and a valid file, when the scan is computed, then only the valid file counts and the failures are recorded")
  void toleratesBrokenFilesAmongValidOnes() throws IOException {
    Path broken = write("broken.json", BROKEN_COLLECTION);
    Path valid = write("valid.json", VALID_COLLECTION);
    Path missing = tempDir.resolve("missing.json");

    ScanOutput output =
        CoverageService.computeScan(
            List.of(), List.of(missing.toFile(), broken.toFile(), valid.toFile()), List.of());

    assertThat(output.errors()).hasSize(2);
    assertThat(output.errors().stream().anyMatch(error -> error.contains(missing.toString())))
        .as("missing file must be reported")
        .isTrue();
    assertThat(
            output.errors().stream()
                .anyMatch(
                    error ->
                        error.startsWith(
                            // Resolved through the bundle so the assertion holds in any locale.
                            EndpointCoverageBundle.message(
                                "service.error.collection.parse", "broken.json", ""))))
        .as("broken file must be reported")
        .isTrue();
    assertThat(output.result().orphanCount()).isEqualTo(2);
    assertThat(output.result().collectionCount()).isEqualTo(1);
    assertThat(output.result().rows()).isNotEmpty();
  }

  @Test
  @DisplayName(
      "Given only failing collection files, when the scan is computed, then an empty but valid report is returned with the per-file errors")
  void returnsEmptyValidReportWhenEveryFileFails() throws IOException {
    Path missing = tempDir.resolve("missing.json");
    Path broken = write("broken.json", BROKEN_COLLECTION);

    ScanOutput output =
        CoverageService.computeScan(
            List.of(), List.of(missing.toFile(), broken.toFile()), List.of());

    assertThat(output.result().rows()).isEmpty();
    assertThat(output.result().coveredCount()).isZero();
    assertThat(output.result().uncoveredCount()).isZero();
    assertThat(output.result().orphanCount()).isZero();
    assertThat(output.result().collectionCount()).isZero();
    assertThat(output.errors()).hasSize(2);
  }

  @Test
  @DisplayName(
      "Given a collection file that parses to no requests, when the scan is computed, then an error is recorded for it")
  void recordsErrorForEmptyCollection() throws IOException {
    Path empty = write("empty-collection.json", "{\"resources\": []}");

    ScanOutput output = CoverageService.computeScan(List.of(), List.of(empty.toFile()), List.of());

    assertThat(output.result().orphanCount()).isZero();
    assertThat(output.errors()).hasSize(1);
    assertThat(output.errors().getFirst())
        .startsWith(
            // Resolved through the bundle so the assertion holds in any locale.
            EndpointCoverageBundle.message(
                "service.error.collection.empty", "empty-collection.json"));
  }

  @Test
  @DisplayName(
      "Given a missing collection file among valid ones, when the scan is computed, then the missing path is recorded on its own list")
  void recordsMissingCollectionPaths() throws IOException {
    Path missing = tempDir.resolve("missing.json");
    Path valid = write("valid.json", VALID_COLLECTION);

    ScanOutput output =
        CoverageService.computeScan(
            List.of(), List.of(missing.toFile(), valid.toFile()), List.of());

    assertThat(output.missingCollectionPaths()).containsExactly(missing.toString());
    assertThat(output.result().collectionCount()).isEqualTo(1);
    assertThat(output.result().orphanCount()).isEqualTo(2);
  }

  @Test
  @DisplayName(
      "Given scan issues raised outside the collection parsing, when the scan is computed, then they are appended to the recorded errors")
  void appendsScanIssuesToErrors() throws IOException {
    Path valid = write("valid.json", VALID_COLLECTION);

    ScanOutput output =
        CoverageService.computeScan(
            List.of(), List.of(valid.toFile()), List.of(), List.of("Module 'x' skipped"));

    assertThat(output.errors()).containsExactly("Module 'x' skipped");
    assertThat(output.result().orphanCount()).isEqualTo(2);
  }

  @Test
  @DisplayName(
      "Given collection paths where some files no longer exist, when they are filtered, then only the paths of existing files remain")
  void keepsOnlyExistingCollectionPaths() throws IOException {
    Path existing = write("existing.json", VALID_COLLECTION);

    List<String> filtered =
        CoverageService.existingCollectionPaths(
            List.of(
                existing.toString(),
                tempDir.resolve("gone.json").toString(),
                "",
                "/definitely/missing.json"));

    assertThat(filtered).containsExactly(existing.toString());
  }

  private Path write(String fileName, String content) throws IOException {
    Path path = tempDir.resolve(fileName);
    Files.writeString(path, content);
    return path;
  }
}
