/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */

package com.luisppb16.collectionsync.settings;

import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.TextBrowseFolderListener;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.TitledSeparator;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBList;
import com.intellij.util.ui.FormBuilder;
import com.luisppb16.collectionsync.domain.model.HttpMethod;
import com.luisppb16.collectionsync.i18n.EndpointCoverageBundle;
import java.awt.Dimension;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;
import java.util.stream.IntStream;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTable;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Swing settings page that edits {@link EndpointCoverageSettings.State}: the automatic scan on
 * project open, the endpoint source type, the OpenAPI document path, the collection files and the
 * exclusion rules.
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
  private javax.swing.DefaultListModel<String> collectionPathsModel;
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

    collectionPathsModel = new javax.swing.DefaultListModel<>();
    JBList<String> collectionPathsList = new JBList<>(collectionPathsModel);
    JPanel collectionsPanel =
        ToolbarDecorator.createDecorator(collectionPathsList)
            .setAddAction(actionButton -> chooseCollectionFile())
            .setRemoveAction(
                actionButton ->
                    Arrays.stream(collectionPathsList.getSelectedIndices())
                        .boxed()
                        .sorted(Comparator.reverseOrder())
                        .forEach(collectionPathsModel::remove))
            .createPanel();

    exclusionsModel =
        new javax.swing.table.DefaultTableModel(
            new Object[] {
              EndpointCoverageBundle.message("settings.exclusions.column.method"),
              EndpointCoverageBundle.message("settings.exclusions.column.path")
            },
            0);
    JTable exclusionsTable = new JTable(exclusionsModel);
    ComboBox<HttpMethod> methodEditor = new ComboBox<>(HttpMethod.values());
    exclusionsTable
        .getColumnModel()
        .getColumn(0)
        .setCellEditor(new javax.swing.DefaultCellEditor(methodEditor));
    exclusionsTable.setPreferredScrollableViewportSize(
        new Dimension(-1, exclusionsTable.getRowHeight() * 5));
    JPanel exclusionsPanel =
        ToolbarDecorator.createDecorator(exclusionsTable)
            .setAddAction(actionButton -> exclusionsModel.addRow(new Object[] {HttpMethod.GET, ""}))
            .setRemoveAction(
                actionButton ->
                    Arrays.stream(exclusionsTable.getSelectedRows())
                        .boxed()
                        .sorted(Comparator.reverseOrder())
                        .forEach(exclusionsModel::removeRow))
            .createPanel();

    mainPanel =
        FormBuilder.createFormBuilder()
            .addComponent(autoScanCheckBox)
            .addLabeledComponent(
                EndpointCoverageBundle.message("settings.source.type.label"), sourceTypeCombo)
            .addLabeledComponent(
                EndpointCoverageBundle.message("settings.openapi.label"), openApiField)
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
        virtualFile -> collectionPathsModel.addElement(virtualFile.getPath()));
  }

  private EndpointCoverageSettings.State stateFromUi() {
    EndpointCoverageSettings.State state = new EndpointCoverageSettings.State();
    state.autoScanOnProjectOpen = autoScanCheckBox.isSelected();
    state.sourceType =
        Objects.requireNonNull(
                (EndpointCoverageSettings.SourceType) sourceTypeCombo.getSelectedItem())
            .name();
    state.openApiFilePath = openApiField.getText().strip();
    state.collectionFilePaths =
        IntStream.range(0, collectionPathsModel.size())
            .mapToObj(collectionPathsModel::get)
            .toList();
    state.exclusions =
        IntStream.range(0, exclusionsModel.getRowCount())
            .mapToObj(
                row ->
                    exclusionEntryOf(
                        exclusionsModel.getValueAt(row, 0), exclusionsModel.getValueAt(row, 1)))
            .toList();
    return state;
  }

  private void populateUi(EndpointCoverageSettings.State state) {
    autoScanCheckBox.setSelected(state.autoScanOnProjectOpen);
    sourceTypeCombo.setSelectedItem(EndpointCoverageSettings.SourceType.from(state.sourceType));
    openApiField.setText(state.openApiFilePath);
    collectionPathsModel.clear();
    state.collectionFilePaths.forEach(collectionPathsModel::addElement);
    exclusionsModel.setRowCount(0);
    state.exclusions.forEach(
        entry -> exclusionsModel.addRow(new Object[] {entry.getMethod(), entry.getPathPattern()}));
  }
}
