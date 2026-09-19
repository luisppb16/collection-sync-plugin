/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */

package com.luisppb16.collectionsync.ui.toolwindow;

import com.intellij.icons.AllIcons;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.fileChooser.FileSaverDescriptor;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiInvalidElementAccessException;
import com.intellij.psi.PsiMethod;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.JBUI;
import com.luisppb16.collectionsync.coverage.AutoScanPolicy;
import com.luisppb16.collectionsync.coverage.CoverageService;
import com.luisppb16.collectionsync.domain.model.CoverageResult;
import com.luisppb16.collectionsync.domain.model.CoverageRow;
import com.luisppb16.collectionsync.domain.model.CoverageStatus;
import com.luisppb16.collectionsync.domain.model.HttpMethod;
import com.luisppb16.collectionsync.domain.model.Segment;
import com.luisppb16.collectionsync.domain.service.PathMatcher;
import com.luisppb16.collectionsync.domain.service.PathNormalizer;
import com.luisppb16.collectionsync.export.CoverageReportExporter;
import com.luisppb16.collectionsync.i18n.EndpointCoverageBundle;
import com.luisppb16.collectionsync.settings.EndpointCoverageSettings;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.ListSelectionModel;
import javax.swing.RowFilter;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.table.TableRowSorter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Main panel of the "Endpoint Coverage" tool window: toolbar (rescan, collections, export),
 * filters, summary, coverage table and its actions (navigation and exclusions).
 *
 * <p>Every UI mutation happens on the EDT; the heavy work is delegated to {@link CoverageService},
 * which scans in the background and calls back on the EDT.
 */
public final class CoveragePanel extends JPanel {

  private static final String NOTIFICATION_GROUP_ID = "CollectionSync Notifications";
  private static final long serialVersionUID = 1L;
  private final transient Project project;
  private final transient CoverageService coverageService;
  private final transient EndpointCoverageSettings settings;
  private final CoverageTableModel tableModel = new CoverageTableModel();
  private final transient TableRowSorter<CoverageTableModel> rowSorter =
      new TableRowSorter<>(tableModel);
  private final SearchTextField queryField = new SearchTextField(false);
  private final ComboBox<StatusFilter> statusFilterCombo = new ComboBox<>(StatusFilter.values());
  private final JLabel summaryLabel =
      new JLabel(EndpointCoverageBundle.message("toolwindow.summary.empty"));
  private final JBTable table = new JBTable(tableModel);
  private JButton exportButton;

  /**
   * Stays {@code true} while the last scans keep reporting zero endpoints, so the warning is
   * reported once per run of empty scans instead of on every refresh.
   */
  private boolean zeroEndpointsNotified;

  /**
   * Builds the panel and schedules the first scan so the table is never empty on open, unless the
   * user disabled the automatic scan: then the panel starts in its empty state and only the manual
   * scans (toolbar, Tools menu, context actions) populate it.
   *
   * @param project owning project; must not be null
   */
  public CoveragePanel(@NotNull Project project) {
    super(new BorderLayout());
    this.project = Objects.requireNonNull(project);
    this.coverageService = project.getService(CoverageService.class);
    this.settings = EndpointCoverageSettings.getInstance(project);
    buildUi();
    if (AutoScanPolicy.shouldAutoScan(settings.isAutoScanOnProjectOpen(), project)) {
      rescan();
    }
  }

  private static boolean matchesQuery(@NotNull CoverageRow row, @NotNull String query) {
    return Stream.of(row.path(), row.owner(), row.module())
        .map(field -> field.toLowerCase(Locale.ROOT))
        .anyMatch(field -> field.contains(query));
  }

  // ------------------------------------------------------------------ build

  private static @NotNull Path withExtension(@NotNull Path selected, @NotNull String extension) {
    String name = selected.getFileName().toString();
    return name.toLowerCase(Locale.ROOT).endsWith("." + extension)
        ? selected
        : selected.resolveSibling(name + "." + extension);
  }

  /**
   * Reports whether a persisted exclusion entry refers to the given coverage row, comparing the
   * method and the normalized paths strictly. Broken entries (hand-edited XML) never match.
   */
  private static boolean sameExclusion(
      @NotNull EndpointCoverageSettings.ExclusionEntry entry,
      @NotNull HttpMethod method,
      @NotNull List<Segment> rowSegments) {
    try {
      return HttpMethod.of(entry.getMethod()) == method
          && PathMatcher.matchesStrict(
              PathNormalizer.normalize(entry.getPathPattern()), rowSegments);
    } catch (IllegalArgumentException brokenEntry) {
      return false;
    }
  }

  private void buildUi() {
    add(northPanel(), BorderLayout.NORTH);
    add(new JBScrollPane(table), BorderLayout.CENTER);
    configureTable();
  }

  private @NotNull JPanel northPanel() {
    JPanel north = new JPanel(new GridBagLayout());
    GridBagConstraints constraints = new GridBagConstraints();
    constraints.gridx = 0;
    constraints.weightx = 1.0;
    constraints.fill = GridBagConstraints.HORIZONTAL;

    JPanel actionsRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
    actionsRow.add(
        actionButton(
            EndpointCoverageBundle.message("toolwindow.button.rescan"),
            AllIcons.Actions.Refresh,
            actionEvent -> rescan()));
    actionsRow.add(
        actionButton(
            EndpointCoverageBundle.message("toolwindow.button.choose.collections"),
            AllIcons.Actions.ListFiles,
            actionEvent -> chooseCollections()));
    JButton exportButton =
        actionButton(
            EndpointCoverageBundle.message("toolwindow.button.export"),
            AllIcons.General.Export,
            actionEvent -> showExportMenu());
    actionsRow.add(exportButton);
    this.exportButton = exportButton;

    JPanel filterRow = new JPanel(new BorderLayout(8, 0));
    statusFilterCombo.addActionListener(actionEvent -> applyFilters());
    queryField
        .getTextEditor()
        .getDocument()
        .addDocumentListener(
            new DocumentAdapter() {
              @Override
              protected void textChanged(@NotNull DocumentEvent documentEvent) {
                applyFilters();
              }
            });
    queryField
        .getTextEditor()
        .getEmptyText()
        .setText(EndpointCoverageBundle.message("toolwindow.filter.empty.text"));
    filterRow.add(statusFilterCombo, BorderLayout.WEST);
    filterRow.add(queryField, BorderLayout.CENTER);

    constraints.gridy = 0;
    constraints.insets = JBUI.insets(8, 8, 4, 8);
    north.add(summaryLabel, constraints);
    constraints.gridy = 1;
    constraints.insets = JBUI.insets(0, 8, 4, 8);
    north.add(actionsRow, constraints);
    constraints.gridy = 2;
    constraints.insets = JBUI.insets(0, 8, 8, 8);
    north.add(filterRow, constraints);
    return north;
  }

  // ------------------------------------------------------------------ scan and filters

  private @NotNull JButton actionButton(
      @NotNull String text, @NotNull Icon icon, @NotNull Consumer<ActionEvent> handler) {
    JButton button = new JButton(text, icon);
    button.addActionListener(handler::accept);
    return button;
  }

  private void configureTable() {
    rowSorter.setComparator(
        CoverageTableModel.STATUS_COLUMN,
        Comparator.comparing((CoverageStatus status) -> status.name()));
    table.setRowSorter(rowSorter);
    table
        .getColumnModel()
        .getColumn(CoverageTableModel.STATUS_COLUMN)
        .setCellRenderer(new CoverageRowRenderer());
    table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    table.addMouseListener(
        new MouseAdapter() {
          @Override
          public void mouseClicked(@NotNull MouseEvent event) {
            if (event.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(event)) {
              navigateTo(selectedRow());
            }
          }

          @Override
          public void mousePressed(@NotNull MouseEvent event) {
            showContextMenu(event);
          }

          @Override
          public void mouseReleased(@NotNull MouseEvent event) {
            showContextMenu(event);
          }
        });
  }

  private void rescan() {
    summaryLabel.setText(EndpointCoverageBundle.message("toolwindow.summary.scanning"));
    coverageService.scanAsync(this::refresh);
  }

  /** Refreshes the summary and the table from the last scan snapshot; EDT only. */
  public void refresh() {
    CoverageService.ScanOutput output = coverageService.lastScan();
    if (output == null) {
      summaryLabel.setText(EndpointCoverageBundle.message("toolwindow.summary.empty"));
      tableModel.setRows(List.of());
      return;
    }
    CoverageResult result = output.result();
    summaryLabel.setText(
        EndpointCoverageBundle.message(
            "toolwindow.summary.format",
            result.coveredCount(),
            result.uncoveredCount(),
            result.orphanCount(),
            result.excludedCount(),
            result.collectionCount()));
    tableModel.setRows(
        Stream.concat(result.rows().stream(), result.excludedRows().stream()).toList());
    // One snapshot: result, issues and missing paths all come from the same finished scan.
    notifyErrors(output.errors(), output.missingCollectionPaths());
    notifyZeroEndpoints(result);
    applyFilters();
  }

  private void applyFilters() {
    rowSorter.setRowFilter(buildRowFilter());
  }

  private @Nullable RowFilter<CoverageTableModel, Integer> buildRowFilter() {
    String query = queryField.getText().strip().toLowerCase(Locale.ROOT);
    CoverageStatus selectedStatus = selectedStatus();
    if (query.isEmpty() && selectedStatus == null) {
      return null;
    }
    return new RowFilter<>() {
      @Override
      public boolean include(Entry<? extends CoverageTableModel, ? extends Integer> entry) {
        CoverageRow row = entry.getModel().rowAt(entry.getIdentifier());
        return (selectedStatus == null || row.status() == selectedStatus)
            && (query.isEmpty() || matchesQuery(row, query));
      }
    };
  }

  // ------------------------------------------------------------------ navigation

  private @Nullable CoverageStatus selectedStatus() {
    StatusFilter filter =
        (StatusFilter)
            Objects.requireNonNull(
                statusFilterCombo.getSelectedItem(), "status filter must be selected");
    return filter.status();
  }

  private void navigateTo(@Nullable CoverageRow row) {
    if (row == null) {
      return;
    }
    if (row.psiMethod() != null) {
      navigateToMethod(row.psiMethod());
      return;
    }
    if (settings.getSourceType() == EndpointCoverageSettings.SourceType.OPEN_API) {
      openOpenApiDocument();
    }
  }

  private void navigateToMethod(@NotNull PsiMethod psiMethod) {
    try {
      navigateToLiveMethod(psiMethod);
    } catch (PsiInvalidElementAccessException staleRow) {
      // The row keeps the PsiMethod of the last scan; the source file may have been
      // deleted or moved since then.
      notifyWarning(EndpointCoverageBundle.message("toolwindow.notification.source.gone"));
    }
  }

  private void navigateToLiveMethod(@NotNull PsiMethod psiMethod) {
    PsiFile containingFile = psiMethod.getContainingFile();
    if (containingFile == null || containingFile.getVirtualFile() == null) {
      notifyWarning(EndpointCoverageBundle.message("toolwindow.notification.source.gone"));
      return;
    }
    new OpenFileDescriptor(project, containingFile.getVirtualFile(), psiMethod.getTextOffset())
        .navigate(true);
  }

  // ------------------------------------------------------------------ collections and export

  private void openOpenApiDocument() {
    String path = settings.getOpenApiFilePath();
    if (path.isBlank()) {
      return;
    }
    VirtualFile openApiFile = LocalFileSystem.getInstance().findFileByNioFile(Path.of(path));
    if (openApiFile != null) {
      new OpenFileDescriptor(project, openApiFile, 0).navigate(true);
    }
  }

  private void chooseCollections() {
    VirtualFile[] selected =
        FileChooser.chooseFiles(
            FileChooserDescriptorFactory.createMultipleFilesNoJarsDescriptor()
                .withTitle(
                    EndpointCoverageBundle.message("toolwindow.filechooser.collections.title"))
                .withDescription(
                    EndpointCoverageBundle.message(
                        "toolwindow.filechooser.collections.description")),
            project,
            null);
    if (selected.length == 0) {
      return;
    }
    EndpointCoverageSettings.State updated =
        EndpointCoverageSettings.copyForUpdate(settings.getState());
    updated.collectionFilePaths =
        new ArrayList<>(
            Stream.concat(
                    settings.getCollectionFilePaths().stream(),
                    Arrays.stream(selected).map(VirtualFile::getPath))
                .distinct()
                .toList());
    settings.setState(updated);
    rescan();
  }

  private void showExportMenu() {
    showExportMenu(exportButton);
  }

  private void showExportMenu(@NotNull JComponent anchor) {
    CoverageResult result = coverageService.lastResult();
    if (result == null) {
      notifyWarning(EndpointCoverageBundle.message("toolwindow.notification.no.data.to.export"));
      return;
    }
    JPopupMenu menu = new JPopupMenu();
    menu.add(
        menuItem(
            EndpointCoverageBundle.message("toolwindow.menu.export.markdown"),
            actionEvent -> exportReport(result, "md", CoverageReportExporter::toMarkdown)));
    menu.add(
        menuItem(
            EndpointCoverageBundle.message("toolwindow.menu.export.csv"),
            actionEvent -> exportReport(result, "csv", CoverageReportExporter::toCsv)));
    menu.show(anchor, 0, anchor.getHeight());
  }

  private @NotNull JMenuItem menuItem(
      @NotNull String text, @NotNull Consumer<ActionEvent> handler) {
    JMenuItem item = new JMenuItem(text);
    item.addActionListener(handler::accept);
    return item;
  }

  private void exportReport(
      @NotNull CoverageResult result,
      @NotNull String extension,
      @NotNull Function<CoverageResult, String> renderer) {
    VirtualFile target =
        FileChooser.chooseFile(
            new FileSaverDescriptor(
                EndpointCoverageBundle.message("toolwindow.filechooser.export.title"),
                EndpointCoverageBundle.message(
                    "toolwindow.filechooser.export.description",
                    extension.toUpperCase(Locale.ROOT)),
                extension),
            project,
            null);
    if (target == null) {
      return;
    }
    // Rendering and writing the report are background work: never block the EDT.
    ApplicationManager.getApplication()
        .executeOnPooledThread(() -> writeReport(result, extension, renderer, target));
  }

  private void writeReport(
      @NotNull CoverageResult result,
      @NotNull String extension,
      @NotNull Function<CoverageResult, String> renderer,
      @NotNull VirtualFile target) {
    Path reportFile = withExtension(Path.of(target.getPath()), extension);
    try {
      Files.writeString(reportFile, renderer.apply(result));
      // Expired when the project closes while the report is written: notifying a disposed
      // project would throw.
      ApplicationManager.getApplication()
          .invokeLater(
              () ->
                  notifyInfo(
                      EndpointCoverageBundle.message(
                          "toolwindow.notification.export.success", reportFile)),
              project.getDisposed());
    } catch (IOException failedExport) {
      ApplicationManager.getApplication()
          .invokeLater(
              () ->
                  notifyWarning(
                      EndpointCoverageBundle.message(
                          "toolwindow.notification.export.failed", failedExport.getMessage())),
              project.getDisposed());
    }
  }

  // ------------------------------------------------------------------ exclusions

  private void showContextMenu(@NotNull MouseEvent event) {
    if (!event.isPopupTrigger()) {
      return;
    }
    int viewRow = table.rowAtPoint(event.getPoint());
    if (viewRow < 0) {
      return;
    }
    table.setRowSelectionInterval(viewRow, viewRow);
    JPopupMenu menu = contextMenuFor(rowAtView(viewRow));
    if (menu.getComponentCount() > 0) {
      menu.show(table, event.getX(), event.getY());
    }
  }

  private @NotNull JPopupMenu contextMenuFor(@Nullable CoverageRow row) {
    JPopupMenu menu = new JPopupMenu();
    if (row == null) {
      return menu;
    }
    switch (row.status()) {
      case COVERED, UNCOVERED ->
          menu.add(
              menuItem(
                  EndpointCoverageBundle.message("toolwindow.action.exclude"),
                  actionEvent -> exclude(row)));
      case EXCLUDED ->
          menu.add(
              menuItem(
                  EndpointCoverageBundle.message("toolwindow.action.remove.exclusion"),
                  actionEvent -> removeExclusion(row)));
      case ORPHAN -> {
        // Collection requests cannot be excluded from the coverage calculation.
      }
    }
    return menu;
  }

  private void exclude(@NotNull CoverageRow row) {
    EndpointCoverageSettings.State updated =
        EndpointCoverageSettings.copyForUpdate(settings.getState());
    updated.exclusions.add(
        new EndpointCoverageSettings.ExclusionEntry(row.method().name(), row.path()));
    settings.setState(updated);
    rescan();
  }

  private void removeExclusion(@NotNull CoverageRow row) {
    List<Segment> rowSegments = PathNormalizer.normalize(row.path());
    EndpointCoverageSettings.State updated =
        EndpointCoverageSettings.copyForUpdate(settings.getState());
    updated.exclusions.removeIf(entry -> sameExclusion(entry, row.method(), rowSegments));
    settings.setState(updated);
    rescan();
  }

  private @Nullable CoverageRow rowAtView(int viewRow) {
    int modelRow = table.convertRowIndexToModel(viewRow);
    return tableModel.rowAt(modelRow);
  }

  // ------------------------------------------------------------------ helpers

  private @Nullable CoverageRow selectedRow() {
    int viewRow = table.getSelectedRow();
    return viewRow < 0 ? null : rowAtView(viewRow);
  }

  private void notifyErrors(@NotNull List<String> errors, @NotNull List<String> missingPaths) {
    if (errors.isEmpty()) {
      return;
    }
    Notification warning = createNotification(NotificationType.WARNING, String.join("\n", errors));
    if (!missingPaths.isEmpty()) {
      warning.addAction(
          NotificationAction.create(
              EndpointCoverageBundle.message("toolwindow.notification.remove.missing.collections"),
              (anActionEvent, currentNotification) -> removeMissingCollections()));
    }
    warning.notify(project);
  }

  /**
   * Warns when the last scan found no endpoints at all, so an empty report is never silent: a 0%
   * coverage with no endpoints means the project scan found nothing, not that nothing is covered.
   * The warning is reported once per run of zero-endpoint scans, not on every refresh.
   */
  private void notifyZeroEndpoints(CoverageResult result) {
    boolean zeroEndpoints =
        result.coveredCount() == 0 && result.uncoveredCount() == 0 && result.excludedCount() == 0;
    if (zeroEndpoints && !zeroEndpointsNotified) {
      notifyWarning(EndpointCoverageBundle.message("service.notification.zero.endpoints"));
    }
    zeroEndpointsNotified = zeroEndpoints;
  }

  /** Removes the persisted collection paths whose file no longer exists and rescans; EDT only. */
  private void removeMissingCollections() {
    List<String> allPaths = settings.getCollectionFilePaths();
    // The existence filter stats the disk, which must stay off the EDT (a stale network mount
    // would freeze the IDE); the state update and the rescan stay on the EDT.
    ApplicationManager.getApplication()
        .executeOnPooledThread(
            () -> {
              List<String> existingPaths = CoverageService.existingCollectionPaths(allPaths);
              int removedCount = allPaths.size() - existingPaths.size();
              ApplicationManager.getApplication()
                  .invokeLater(
                      () -> removeMissingCollections(existingPaths, removedCount),
                      project.getDisposed());
            });
  }

  private void removeMissingCollections(@NotNull List<String> existingPaths, int removedCount) {
    if (removedCount > 0) {
      EndpointCoverageSettings.State updated =
          EndpointCoverageSettings.copyForUpdate(settings.getState());
      updated.collectionFilePaths = new ArrayList<>(existingPaths);
      settings.setState(updated);
      notifyInfo(
          EndpointCoverageBundle.message(
              "toolwindow.notification.missing.collections.removed", removedCount));
    }
    // Rescan either way: the table may be stale even when nothing was removed.
    rescan();
  }

  private @NotNull Notification createNotification(
      @NotNull NotificationType type, @NotNull String message) {
    return NotificationGroupManager.getInstance()
        .getNotificationGroup(NOTIFICATION_GROUP_ID)
        .createNotification(message, type);
  }

  private void notifyInfo(@NotNull String message) {
    createNotification(NotificationType.INFORMATION, message).notify(project);
  }

  private void notifyWarning(@NotNull String message) {
    createNotification(NotificationType.WARNING, message).notify(project);
  }

  /**
   * Filter entry of the status combo; {@link #ALL} disables the status condition. The label key is
   * resolved through the resource bundle so the combo is localized.
   */
  private enum StatusFilter {
    ALL("toolwindow.filter.all", null),
    COVERED("ui.status.covered", CoverageStatus.COVERED),
    UNCOVERED("ui.status.uncovered", CoverageStatus.UNCOVERED),
    ORPHAN("ui.status.orphan", CoverageStatus.ORPHAN),
    EXCLUDED("ui.status.excluded", CoverageStatus.EXCLUDED);

    private final @NotNull String labelKey;
    private final @Nullable CoverageStatus status;

    StatusFilter(@NotNull String labelKey, @Nullable CoverageStatus status) {
      this.labelKey = labelKey;
      this.status = status;
    }

    /**
     * @return the filtered status, or null to show every row
     */
    @Nullable
    CoverageStatus status() {
      return status;
    }

    @Override
    public @NotNull String toString() {
      return EndpointCoverageBundle.message(labelKey);
    }
  }
}
