/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */

package com.luisppb16.collectionsync.ui.toolwindow;

import com.luisppb16.collectionsync.domain.model.CoverageRow;
import com.luisppb16.collectionsync.domain.model.CoverageStatus;
import com.luisppb16.collectionsync.i18n.EndpointCoverageBundle;
import java.util.List;
import javax.swing.table.AbstractTableModel;
import org.jetbrains.annotations.NotNull;

/**
 * Table model of the coverage tool window: one row per {@link CoverageRow} with the columns Status,
 * Method, Path, Owner and Module.
 *
 * <p>The model exposes the {@link CoverageRow} of every model index through {@link #rowAt(int)}, so
 * renderers, filters and actions work with the domain object instead of re-parsing cells. Excluded
 * rows are loaded by the panel together with the regular ones; the status filter decides whether
 * they are visible. Column names come from the resource bundle, resolved on each
 * {@link #getColumnName(int)} call.
 */
public final class CoverageTableModel extends AbstractTableModel {

  /** Column indexes, shared by the panel when wiring renderers and comparators. */
  public static final int STATUS_COLUMN = 0;

  public static final int METHOD_COLUMN = 1;
  public static final int PATH_COLUMN = 2;
  public static final int OWNER_COLUMN = 3;
  public static final int MODULE_COLUMN = 4;

  private static final int COLUMN_COUNT = 5;

  private static final long serialVersionUID = 1L;

  private transient List<CoverageRow> rows = List.of();

  /**
   * Replaces the displayed rows.
   *
   * @param newRows rows to display; must not be null
   */
  public void setRows(@NotNull List<CoverageRow> newRows) {
    rows = List.copyOf(newRows);
    fireTableDataChanged();
  }

  /**
   * Returns the domain row at the given model index (not a view index).
   *
   * @param modelRowIndex model row index
   * @return the row; never null
   * @throws IllegalArgumentException if the index is out of range
   */
  public @NotNull CoverageRow rowAt(int modelRowIndex) {
    if (modelRowIndex < 0 || modelRowIndex >= rows.size()) {
      throw new IllegalArgumentException("Coverage row index out of range: " + modelRowIndex);
    }
    return rows.get(modelRowIndex);
  }

  @Override
  public int getRowCount() {
    return rows.size();
  }

  @Override
  public int getColumnCount() {
    return COLUMN_COUNT;
  }

  @Override
  public @NotNull String getColumnName(int columnIndex) {
    return switch (columnIndex) {
      case STATUS_COLUMN -> EndpointCoverageBundle.message("ui.column.status");
      case METHOD_COLUMN -> EndpointCoverageBundle.message("ui.column.method");
      case PATH_COLUMN -> EndpointCoverageBundle.message("ui.column.path");
      case OWNER_COLUMN -> EndpointCoverageBundle.message("ui.column.owner");
      case MODULE_COLUMN -> EndpointCoverageBundle.message("ui.column.module");
      default -> throw new IllegalArgumentException("Unknown coverage column: " + columnIndex);
    };
  }

  @Override
  public @NotNull Class<?> getColumnClass(int columnIndex) {
    return columnIndex == STATUS_COLUMN ? CoverageStatus.class : String.class;
  }

  @Override
  public boolean isCellEditable(int rowIndex, int columnIndex) {
    return false;
  }

  @Override
  public @NotNull Object getValueAt(int rowIndex, int columnIndex) {
    CoverageRow row = rows.get(rowIndex);
    return switch (columnIndex) {
      case STATUS_COLUMN -> row.status();
      case METHOD_COLUMN -> row.method().name();
      case PATH_COLUMN -> row.path();
      case OWNER_COLUMN -> row.owner();
      case MODULE_COLUMN -> row.module();
      default -> throw new IllegalArgumentException("Unknown coverage column: " + columnIndex);
    };
  }
}
