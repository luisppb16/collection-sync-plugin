/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */

package com.luisppb16.collectionsync.settings;

import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.TextBrowseFolderListener;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.TitledSeparator;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import com.luisppb16.collectionsync.domain.model.HttpMethod;
import com.luisppb16.collectionsync.i18n.EndpointCoverageBundle;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTable;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Swing settings page that edits {@link EndpointCoverageSettings.State}: the automatic scan on
 * project open, the endpoint source type, the OpenAPI document path, the base path ignored on both
 * sides when matching, the collection files and the exclusion rules.
 *
 * <p>The diff logic (is/apply/reset) goes through the pure {@link
 * EndpointCoverageSettings#equalsState}, comparing the persisted state against the state
 * reconstructed from the UI components.
 */
public final class EndpointCoverageConfigurable implements Configurable {

  private final Project project;

  private ComboBox<EndpointCoverageSettings.SourceType> sourceTypeCombo;
  private JBCheckBox autoScanCheckBox;
  private TextFieldWithBrowseButton openApiField;
  private JBTextField basePathField;
  private javax.swing.table.DefaultTableModel collectionModel;
  private javax.swing.table.DefaultTableModel exclusionsModel;
  private JPanel mainPanel;

  /**
   * @param project project whose settings are edited; must not be null
   */
  public EndpointCoverageConfigurable(@NotNull Project project) {
    this.project = Objects.requireNonNull(project);
  }

  /**
   * Maps one exclusion table row into a persisted entry. The method cell holds either a {@link
   * HttpMethod} (cell editor, add action) or its name as text (populated state), so it is converted
   * instead of cast.
   *
   * @param methodCell raw value of the Method cell; may be null
   * @param pathCell raw value of the Path pattern cell; may be null
   * @return the persisted entry; never null
   */
  static EndpointCoverageSettings.ExclusionEntry exclusionEntryOf(
      @Nullable Object methodCell, @Nullable Object pathCell) {
    String method =
        methodCell instanceof HttpMethod httpMethod
            ? httpMethod.name()
            : Objects.toString(methodCell, "");
    return new EndpointCoverageSettings.ExclusionEntry(method, Objects.toString(pathCell, ""));
  }

  private static String autoModuleLabel() {
    return EndpointCoverageBundle.message("settings.collections.module.auto");
  }

  private static HttpMethod[] exclusionMethods() {
    return Arrays.stream(HttpMethod.values())
        .filter(method -> method != HttpMethod.ANY)
        .toArray(HttpMethod[]::new);
  }

  @Override
  public @Nls(capitalization = Nls.Capitalization.Title) String getDisplayName() {
    return EndpointCoverageBundle.message("settings.display.name");
  }

  @Override
  public @Nullable JComponent createComponent() {
    buildUi();
    reset();
    return mainPanel;
  }

  @Override
  public boolean isModified() {
    EndpointCoverageSettings settings = EndpointCoverageSettings.getInstance(project);
    return !EndpointCoverageSettings.equalsState(settings.getState(), stateFromUi());
  }

  @Override
  public void apply() {
    EndpointCoverageSettings.getInstance(project).setState(stateFromUi());
  }

  @Override
  public void reset() {
    populateUi(EndpointCoverageSettings.getInstance(project).getState());
  }

  private void buildUi() {
    autoScanCheckBox =
        new JBCheckBox(EndpointCoverageBundle.message("settings.auto.scan.label"), true);
    sourceTypeCombo = new ComboBox<>(EndpointCoverageSettings.SourceType.values());
    openApiField = new TextFieldWithBrowseButton();
    openApiField.addBrowseFolderListener(
        new TextBrowseFolderListener(
            FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor()
                .withTitle(EndpointCoverageBundle.message("settings.openapi.chooser.title"))
                .withDescription(
                    EndpointCoverageBundle.message("settings.openapi.chooser.description"))));
    basePathField = new JBTextField();
    basePathField.setToolTipText(EndpointCoverageBundle.message("settings.base.path.comment"));

    collectionModel =
        new javax.swing.table.DefaultTableModel(
            new Object[] {
              EndpointCoverageBundle.message("settings.collections.column.path"),
              EndpointCoverageBundle.message("settings.collections.column.module")
            },
            0) {
          @Override
          public boolean isCellEditable(int row, int column) {
            return column == 1;
          }
        };
    JTable collectionTable = new JTable(collectionModel);
    ComboBox<String> moduleEditor = new ComboBox<>(moduleOptions());
    collectionTable
        .getColumnModel()
        .getColumn(1)
        .setCellEditor(new javax.swing.DefaultCellEditor(moduleEditor));
    collectionTable.setPreferredScrollableViewportSize(
        new Dimension(-1, collectionTable.getRowHeight() * 5));
    JPanel collectionsPanel =
        ToolbarDecorator.createDecorator(collectionTable)
            .setAddAction(actionButton -> chooseCollectionFile())
            .setRemoveAction(actionButton -> removeSelectedRows(collectionTable, collectionModel))
            .createPanel();

    exclusionsModel =
        new javax.swing.table.DefaultTableModel(
            new Object[] {
              EndpointCoverageBundle.message("settings.exclusions.column.method"),
              EndpointCoverageBundle.message("settings.exclusions.column.path")
            },
            0);
    JTable exclusionsTable = new JTable(exclusionsModel);
    ComboBox<HttpMethod> methodEditor = new ComboBox<>(exclusionMethods());
    exclusionsTable
        .getColumnModel()
        .getColumn(0)
        .setCellEditor(new javax.swing.DefaultCellEditor(methodEditor));
    exclusionsTable.setPreferredScrollableViewportSize(
        new Dimension(-1, exclusionsTable.getRowHeight() * 5));
    JPanel exclusionsPanel =
        ToolbarDecorator.createDecorator(exclusionsTable)
            .setAddAction(actionButton -> exclusionsModel.addRow(new Object[] {HttpMethod.GET, ""}))
            .setRemoveAction(actionButton -> removeSelectedRows(exclusionsTable, exclusionsModel))
            .createPanel();

    mainPanel =
        FormBuilder.createFormBuilder()
            .addComponent(autoScanCheckBox)
            .addLabeledComponent(
                EndpointCoverageBundle.message("settings.source.type.label"), sourceTypeCombo)
            .addLabeledComponent(
                EndpointCoverageBundle.message("settings.openapi.label"), openApiField)
            .addLabeledComponent(
                EndpointCoverageBundle.message("settings.base.path.label"), basePathField)
            .addComponent(
                new TitledSeparator(EndpointCoverageBundle.message("settings.collections.title")))
            .addComponent(collectionsPanel)
            .addComponent(
                new TitledSeparator(EndpointCoverageBundle.message("settings.exclusions.title")))
            .addComponent(exclusionsPanel)
            .addComponentFillVertically(new JPanel(), 0)
            .getPanel();
  }

  private void chooseCollectionFile() {
    FileChooser.chooseFile(
        FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor(),
        project,
        null,
        virtualFile ->
            collectionModel.addRow(new Object[] {virtualFile.getPath(), autoModuleLabel()}));
  }

  private void removeSelectedRows(JTable table, javax.swing.table.DefaultTableModel model) {
    Arrays.stream(table.getSelectedRows())
        .boxed()
        .sorted(Comparator.reverseOrder())
        .forEach(model::removeRow);
  }

  private String[] moduleOptions() {
    Set<String> modules = new TreeSet<>();
    Arrays.stream(ModuleManager.getInstance(project).getModules())
        .map(Module::getName)
        .forEach(modules::add);
    EndpointCoverageSettings.getInstance(project).getCollectionBindings().stream()
        .map(EndpointCoverageSettings.CollectionBinding::getModuleName)
        .filter(module -> module != null && !module.isBlank())
        .forEach(modules::add);
    return Stream.concat(Stream.of(autoModuleLabel()), modules.stream()).toArray(String[]::new);
  }

  private EndpointCoverageSettings.State stateFromUi() {
    EndpointCoverageSettings.State state = new EndpointCoverageSettings.State();
    state.autoScanOnProjectOpen = autoScanCheckBox.isSelected();
    state.sourceType =
        Objects.requireNonNull(
                (EndpointCoverageSettings.SourceType) sourceTypeCombo.getSelectedItem())
            .name();
    state.openApiFilePath = openApiField.getText().strip();
    state.basePath = basePathField.getText().strip();
    List<String> collectionPaths = new ArrayList<>();
    List<EndpointCoverageSettings.CollectionBinding> collectionBindings = new ArrayList<>();
    String autoModule = autoModuleLabel();
    for (int row = 0; row < collectionModel.getRowCount(); row++) {
      String path = Objects.toString(collectionModel.getValueAt(row, 0), "").strip();
      if (path.isEmpty()) {
        continue;
      }
      collectionPaths.add(path);
      String module = Objects.toString(collectionModel.getValueAt(row, 1), "").strip();
      if (!module.isEmpty() && !module.equals(autoModule)) {
        collectionBindings.add(new EndpointCoverageSettings.CollectionBinding(path, module));
      }
    }
    state.collectionFilePaths = collectionPaths;
    state.collectionBindings = collectionBindings;
    state.exclusions =
        IntStream.range(0, exclusionsModel.getRowCount())
            .mapToObj(row ->
                exclusionEntryOf(
                    exclusionsModel.getValueAt(row, 0), exclusionsModel.getValueAt(row, 1)))
            .toList();
    return state;
  }

  private void populateUi(EndpointCoverageSettings.State state) {
    autoScanCheckBox.setSelected(state.autoScanOnProjectOpen);
    sourceTypeCombo.setSelectedItem(EndpointCoverageSettings.SourceType.from(state.sourceType));
    openApiField.setText(state.openApiFilePath);
    basePathField.setText(state.basePath);
    Map<String, String> bindings =
        EndpointCoverageSettings.getInstance(project).collectionBindingsByPath();
    String autoModule = autoModuleLabel();
    collectionModel.setRowCount(0);
    for (String path : state.collectionFilePaths) {
      String module = bindings.get(path);
      collectionModel.addRow(
          new Object[] {path, module == null || module.isBlank() ? autoModule : module});
    }
    exclusionsModel.setRowCount(0);
    state.exclusions.forEach(
        entry -> exclusionsModel.addRow(new Object[] {entry.getMethod(), entry.getPathPattern()}));
  }
}
