/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */

package com.luisppb16.collectionsync.export;

import static org.assertj.core.api.Assertions.assertThat;

import com.luisppb16.collectionsync.domain.model.CoverageResult;
import com.luisppb16.collectionsync.domain.model.CoverageRow;
import com.luisppb16.collectionsync.domain.model.CoverageStatus;
import com.luisppb16.collectionsync.domain.model.HttpMethod;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("CoverageReportExporter")
class CoverageReportExporterTest {

  private static final String CSV_HEADER = "Status,Method,Path,Owner,Module";
  private static final String MARKDOWN_HEADER = "| Status | Method | Path | Owner | Module |";

  @Test
  @DisplayName(
      "Given a full coverage result, when it is exported to Markdown, then summary and every row are rendered")
  void rendersMarkdownSummaryAndRows() {
    CoverageResult result =
        new CoverageResult(
            List.of(
                new CoverageRow(
                    CoverageStatus.COVERED,
                    HttpMethod.GET,
                    "/users/{id}",
                    "UserController",
                    "app",
                    null),
                new CoverageRow(
                    CoverageStatus.ORPHAN,
                    HttpMethod.PUT,
                    "{{baseUrl}}/reports",
                    "Postman",
                    "",
                    null)),
            List.of(
                new CoverageRow(
                    CoverageStatus.EXCLUDED,
                    HttpMethod.GET,
                    "/actuator/{name}",
                    "OpsController",
                    "app",
                    null)),
            1,
            0,
            1,
            1,
            1);

    String markdown = CoverageReportExporter.toMarkdown(result);

    assertThat(markdown).startsWith("# Endpoint Coverage Report");
    assertThat(markdown)
        .contains(
            "- Covered: 1", "- Uncovered: 0", "- Orphans: 1", "- Excluded: 1", "- Collections: 1");
    assertThat(markdown).contains(MARKDOWN_HEADER, "|---|---|---|---|---|");
    assertThat(markdown).contains("| COVERED | GET | /users/{id} | UserController | app |");
    assertThat(markdown).contains("| ORPHAN | PUT | {{baseUrl}}/reports | Postman |  |");
    assertThat(markdown).contains("| EXCLUDED | GET | /actuator/{name} | OpsController | app |");
    assertThat(markdown).endsWith("\n");
  }

  @Test
  @DisplayName(
      "Given a full coverage result, when it is exported to CSV, then header and every row are rendered")
  void rendersCsvHeaderAndRows() {
    CoverageResult result =
        new CoverageResult(
            List.of(
                new CoverageRow(
                    CoverageStatus.UNCOVERED,
                    HttpMethod.DELETE,
                    "/orders/{id}",
                    "OrderController",
                    "orders",
                    null)),
            List.of(
                new CoverageRow(
                    CoverageStatus.EXCLUDED,
                    HttpMethod.GET,
                    "/actuator",
                    "OpsController",
                    "",
                    null)),
            0,
            1,
            0,
            1,
            0);

    String csv = CoverageReportExporter.toCsv(result);

    assertThat(csv).startsWith(CSV_HEADER + "\n");
    assertThat(csv).contains("UNCOVERED,DELETE,/orders/{id},OrderController,orders");
    assertThat(csv).contains("EXCLUDED,GET,/actuator,OpsController,");
    assertThat(csv).endsWith("\n");
  }

  @ParameterizedTest
  @CsvSource({
    "'/plain/path', '/plain/path'",
    "'/a,b', '\"/a,b\"'",
    "'say \"hi\"', '\"say \"\"hi\"\"\"'",
    "'line1\nline2', '\"line1\nline2\"'",
    "'a,\"b\"', '\"a,\"\"b\"\"\"'"
  })
  @DisplayName(
      "Given a cell with special characters, when CSV is rendered, then the value is correctly escaped")
  void escapesCsvCells(String rawValue, String expectedCell) {
    CoverageRow row =
        new CoverageRow(CoverageStatus.UNCOVERED, HttpMethod.POST, rawValue, "", "", null);
    CoverageResult result = new CoverageResult(List.of(row), List.of(), 0, 1, 0, 0, 0);

    String csv = CoverageReportExporter.toCsv(result);

    assertThat(csv).contains(CSV_HEADER + "\nUNCOVERED,POST," + expectedCell + ",,");
  }

  @Test
  @DisplayName("Given a path containing pipes, when Markdown is rendered, then pipes are escaped")
  void escapesMarkdownCells() {
    CoverageRow row =
        new CoverageRow(CoverageStatus.COVERED, HttpMethod.GET, "/a|b", "Weird|Owner", "m", null);
    CoverageResult result = new CoverageResult(List.of(row), List.of(), 1, 0, 0, 0, 0);

    String markdown = CoverageReportExporter.toMarkdown(result);

    assertThat(markdown).contains("| COVERED | GET | /a\\|b | Weird\\|Owner | m |");
    assertThat(markdown.lines().filter(line -> line.startsWith("| "))).hasSize(2);
  }

  @Test
  @DisplayName(
      "Given an empty coverage result, when it is exported, then only the summary and headers are rendered")
  void rendersEmptyResults() {
    CoverageResult emptyResult = new CoverageResult(List.of(), List.of(), 0, 0, 0, 0, 0);

    String markdown = CoverageReportExporter.toMarkdown(emptyResult);
    String csv = CoverageReportExporter.toCsv(emptyResult);

    assertThat(markdown)
        .contains(
            "- Covered: 0", "- Uncovered: 0", "- Orphans: 0", "- Excluded: 0", "- Collections: 0");
    assertThat(markdown.lines().filter(line -> line.startsWith("| "))).hasSize(1);
    assertThat(csv).isEqualTo(CSV_HEADER + "\n");
  }
}
