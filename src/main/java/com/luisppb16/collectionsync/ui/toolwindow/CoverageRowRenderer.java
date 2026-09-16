/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */

package com.luisppb16.collectionsync.ui.toolwindow;

import java.util.Objects;

import javax.swing.Icon;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;

import org.jetbrains.annotations.NotNull;

import com.intellij.icons.AllIcons;

import com.luisppb16.collectionsync.domain.model.CoverageStatus;
import com.luisppb16.collectionsync.i18n.EndpointCoverageBundle;

/**
 * Cell renderer of the Status column: an icon per coverage state plus its localized name, resolved
 * through the resource bundle (the domain enum stays UI-agnostic).
 *
 * <p>Icons come from the platform's own {@link AllIcons} set, so they follow the IDE theme.
 */
public final class CoverageRowRenderer extends DefaultTableCellRenderer {

    private static final long serialVersionUID = 1L;

    @Override
    public @NotNull java.awt.Component getTableCellRendererComponent(@NotNull JTable table,
                                                                     Object value,
                                                                     boolean isSelected,
                                                                     boolean hasFocus,
                                                                     int row,
                                                                     int column) {
        super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        if (value instanceof CoverageStatus status) {
            setText(textFor(status));
            setIcon(iconFor(status));
            setHorizontalAlignment(SwingConstants.LEFT);
        }
        return this;
    }

    /**
     * Maps a coverage status to its localized display text.
     *
     * @param status status to render; must not be null
     * @return the localized text; never null
     */
    public static @NotNull String textFor(@NotNull CoverageStatus status) {
        return switch (Objects.requireNonNull(status)) {
            case COVERED -> EndpointCoverageBundle.message("ui.status.covered");
            case UNCOVERED -> EndpointCoverageBundle.message("ui.status.uncovered");
            case ORPHAN -> EndpointCoverageBundle.message("ui.status.orphan");
            case EXCLUDED -> EndpointCoverageBundle.message("ui.status.excluded");
        };
    }

    /**
     * Maps a coverage status to its platform icon.
     *
     * @param status status to render; must not be null
     * @return the icon; never null
     */
    public static @NotNull Icon iconFor(@NotNull CoverageStatus status) {
        return switch (Objects.requireNonNull(status)) {
            case COVERED -> AllIcons.Actions.Checked;
            case UNCOVERED -> AllIcons.General.InspectionsWarning;
            case ORPHAN -> AllIcons.General.Warning;
            case EXCLUDED -> AllIcons.Actions.Cancel;
        };
    }
}