/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */

package com.luisppb16.collectionsync.export;

import com.luisppb16.collectionsync.domain.model.CoverageResult;
import com.luisppb16.collectionsync.domain.model.CoverageRow;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Renders a {@link CoverageResult} as a shareable report in Markdown or CSV format.
 *
 * <p>Both formats contain a summary block and one row per coverage row, including the excluded ones
 * (status {@code EXCLUDED}) so the exported file is a complete picture of the calculation.
 */
public final class CoverageReportExporter {

  private static final String CSV_HEADER = "Status,Method,Path,Owner,Module";
  private static final String MARKDOWN_HEADER = "| Status | Method | Path | Owner | Module |";
  private static final String MARKDOWN_SEPARATOR = "|---|---|---|---|---|";

  private CoverageReportExporter() {}

  /**
   * Renders the report as Markdown: title, summary counts and a table of rows.
   *
   * @param result coverage result to render; must not be null
   * @return the Markdown document; never null
   */
  public static String toMarkdown(CoverageResult result) {
    Objects.requireNonNull(result);
    List<String> lines = new ArrayList<>();
    lines.add("# Endpoint Coverage Report");
    lines.add("");
    lines.add("## Summary");
    lines.add("");
    lines.add("- Covered: " + result.coveredCount());
    lines.add("- Uncovered: " + result.uncoveredCount());
    lines.add("- Orphans: " + result.orphanCount());
    lines.add("- Excluded: " + result.excludedCount());
    lines.add("- Collections: " + result.collectionCount());
    lines.add("");
    lines.add("## Rows");
    lines.add("");
    lines.add(MARKDOWN_HEADER);
    lines.add(MARKDOWN_SEPARATOR);
    allRows(result).map(CoverageReportExporter::toMarkdownRow).forEach(lines::add);
    return String.join("\n", lines) + "\n";
  }

  /**
   * Renders the report as CSV (RFC 4180 style): header and one row per coverage row, with commas,
   * quotes and line breaks correctly escaped.
   *
   * @param result coverage result to render; must not be null
   * @return the CSV document; never null
   */
  public static String toCsv(CoverageResult result) {
    Objects.requireNonNull(result);
    List<String> lines = new ArrayList<>();
    lines.add(CSV_HEADER);
    allRows(result).map(CoverageReportExporter::toCsvRow).forEach(lines::add);
    return String.join("\n", lines) + "\n";
  }

  private static Stream<CoverageRow> allRows(CoverageResult result) {
    return Stream.concat(result.rows().stream(), result.excludedRows().stream());
  }

  private static String toMarkdownRow(CoverageRow row) {
    return "| %s | %s | %s | %s | %s |"
        .formatted(
            row.status(),
            row.method(),
            markdownEscape(row.path()),
            markdownEscape(row.owner()),
            markdownEscape(row.module()));
  }

  private static String toCsvRow(CoverageRow row) {
    return Stream.of(
            row.status().name(), row.method().name(), row.path(), row.owner(), row.module())
        .map(CoverageReportExporter::csvEscape)
        .reduce((first, second) -> first + "," + second)
        .orElse("");
  }

  /**
   * Escapes a cell for Markdown: pipes are backslash-escaped and line breaks flattened, so a value
   * can never break the table structure.
   */
  private static String markdownEscape(String value) {
    return value.replace("|", "\\|").replace("\r", " ").replace("\n", " ");
  }

  /**
   * Escapes a cell for CSV: when the value contains a comma, a quote or a line break it is quoted
   * and every quote is doubled, as RFC 4180 requires.
   */
  private static String csvEscape(String value) {
    String escaped = value.replace("\"", "\"\"");
    boolean needsQuoting =
        escaped.contains(",")
            || escaped.contains("\"")
            || escaped.contains("\n")
            || escaped.contains("\r");
    return needsQuoting ? '"' + escaped + '"' : escaped;
  }
}
