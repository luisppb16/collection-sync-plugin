/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */

package com.luisppb16.collectionsync.settings;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.luisppb16.collectionsync.domain.model.ExclusionRule;
import com.luisppb16.collectionsync.domain.model.HttpMethod;
import com.luisppb16.collectionsync.i18n.EndpointCoverageBundle;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;

/**
 * Project-level settings of the Endpoint Coverage Tracker: where the endpoints come from, which
 * collection files to parse and which endpoints are always excluded from the report.
 *
 * <p>The state is persisted per project and is always <b>null-safe</b>: every list defaults to
 * empty and every string field defaults to the empty string, both on a fresh project and after
 * loading a partially written XML file.
 */
@Service(Service.Level.PROJECT)
@State(name = "EndpointCoverageSettings", storages = @Storage("endpoint-coverage.xml"))
public final class EndpointCoverageSettings
    implements PersistentStateComponent<EndpointCoverageSettings.State> {

  private State state = new State();

  /**
   * Returns the settings instance of the given project.
   *
   * @param project target project; must not be null
   * @return the project-level settings service; never null
   */
  public static EndpointCoverageSettings getInstance(@NotNull Project project) {
    return project.getService(EndpointCoverageSettings.class);
  }

  /**
   * Reports whether two states are equivalent for the settings UI diff.
   *
   * @param first first state; may be null
   * @param second second state; may be null
   * @return true when both are null or every field matches (source type normalized, OpenAPI path
   *     compared stripped)
   */
  public static boolean equalsState(State first, State second) {
    if (first == null || second == null) {
      return first == second;
    }
    return SourceType.from(first.sourceType) == SourceType.from(second.sourceType)
        && first.openApiFilePath.strip().equals(second.openApiFilePath.strip())
        && first.collectionFilePaths.equals(second.collectionFilePaths)
        && first.exclusions.equals(second.exclusions)
        && first.autoScanOnProjectOpen == second.autoScanOnProjectOpen;
  }

  /**
   * Returns a mutable copy of the given state, to edit and store back through {@link
   * #setState(State)}: the whole state is replaced so every change is persisted atomically. Shared
   * by every writer (settings UI, tool window, context menu actions) so a new state field is copied
   * in exactly one place.
   *
   * @param current state to copy; must not be null
   * @return the editable copy; never null
   */
  public static @NotNull State copyForUpdate(@NotNull State current) {
    Objects.requireNonNull(current, "current must not be null");
    State updated = new State();
    updated.sourceType = current.sourceType;
    updated.openApiFilePath = current.openApiFilePath;
    updated.collectionFilePaths = new ArrayList<>(current.collectionFilePaths);
    updated.exclusions = new ArrayList<>(current.exclusions);
    updated.autoScanOnProjectOpen = current.autoScanOnProjectOpen;
    return updated;
  }

  private static Optional<ExclusionRule> toRule(ExclusionEntry entry) {
    if (entry == null || isBlank(entry.getMethod()) || isBlank(entry.getPathPattern())) {
      return Optional.empty();
    }
    try {
      return Optional.of(
          new ExclusionRule(HttpMethod.of(entry.getMethod()), entry.getPathPattern()));
    } catch (IllegalArgumentException unsupportedMethod) {
      return Optional.empty();
    }
  }

  private static State normalized(State loadedState) {
    State normalizedState = new State();
    normalizedState.sourceType =
        loadedState.sourceType == null
            ? SourceType.CONTROLLER_ANNOTATIONS.name()
            : loadedState.sourceType;
    normalizedState.openApiFilePath =
        loadedState.openApiFilePath == null ? "" : loadedState.openApiFilePath;
    normalizedState.collectionFilePaths =
        new ArrayList<>(
            loadedState.collectionFilePaths == null ? List.of() : loadedState.collectionFilePaths);
    normalizedState.exclusions =
        new ArrayList<>(loadedState.exclusions == null ? List.of() : loadedState.exclusions);
    normalizedState.autoScanOnProjectOpen = loadedState.autoScanOnProjectOpen;
    return normalizedState;
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  @Override
  public @NotNull State getState() {
    return state;
  }

  /**
   * Replaces the whole state (used by the settings UI on apply), normalizing nulls.
   *
   * @param newState state to store; must not be null
   */
  public void setState(@NotNull State newState) {
    state = normalized(newState);
  }

  @Override
  public void loadState(@NotNull State loadedState) {
    state = normalized(loadedState);
  }

  /**
   * @return the configured endpoint source type; never null
   */
  public SourceType getSourceType() {
    return SourceType.from(state.sourceType);
  }

  /**
   * @return the configured OpenAPI document path; empty when unset
   */
  public String getOpenApiFilePath() {
    return state.openApiFilePath;
  }

  /**
   * @return a defensive copy of the configured collection file paths; never null
   */
  public List<String> getCollectionFilePaths() {
    return List.copyOf(state.collectionFilePaths);
  }

  /**
   * @return whether the coverage scan runs automatically when the project opens; never read from a
   *     null (the primitive field cannot be null, so hand-edited or legacy XML without the
   *     attribute keeps the {@code true} default)
   */
  public boolean isAutoScanOnProjectOpen() {
    return state.autoScanOnProjectOpen;
  }

  /**
   * Converts the serialized exclusion entries into domain rules.
   *
   * <p>Entries with a null/blank method or path pattern, or with an unsupported HTTP method, are
   * skipped instead of failing: persisted XML may be hand-edited and must never break the scan.
   *
   * @return the valid exclusion rules; never null, possibly empty
   */
  public List<ExclusionRule> toRules() {
    return state.exclusions.stream().flatMap(entry -> toRule(entry).stream()).toList();
  }

  /** Origin of the real endpoints compared against the collections. */
  public enum SourceType {
    /** Endpoints are read from a local OpenAPI 3.x document. */
    OPEN_API,
    /** Endpoints are discovered scanning controller annotations (Spring MVC, JAX-RS). */
    CONTROLLER_ANNOTATIONS;

    /**
     * Parses a persisted source type.
     *
     * @param raw persisted value; may be null or unknown (e.g. hand-edited XML)
     * @return the matching constant, or {@link #CONTROLLER_ANNOTATIONS} for unknown values
     */
    public static SourceType from(String raw) {
      if (raw == null || raw.isBlank()) {
        return CONTROLLER_ANNOTATIONS;
      }
      try {
        return SourceType.valueOf(raw);
      } catch (IllegalArgumentException unknownValue) {
        return CONTROLLER_ANNOTATIONS;
      }
    }

    /**
     * Localized display name shown in the settings combo box, resolved from the resource bundle.
     * Persistence never uses this value: it is always stored as {@link #name()}.
     *
     * @return the localized display name; never null
     */
    @Override
    public @NotNull String toString() {
      return EndpointCoverageBundle.message("settings.source.type." + name());
    }
  }

  /** Serializable exclusion entry; kept as a public bean because of IntelliJ XML serialization. */
  public static final class ExclusionEntry {

    private String method;
    private String pathPattern;

    public ExclusionEntry() {}

    public ExclusionEntry(String method, String pathPattern) {
      this.method = method;
      this.pathPattern = pathPattern;
    }

    public String getMethod() {
      return method;
    }

    public void setMethod(String method) {
      this.method = method;
    }

    public String getPathPattern() {
      return pathPattern;
    }

    public void setPathPattern(String pathPattern) {
      this.pathPattern = pathPattern;
    }

    @Override
    public boolean equals(Object other) {
      return other instanceof ExclusionEntry entry
          && Objects.equals(method, entry.method)
          && Objects.equals(pathPattern, entry.pathPattern);
    }

    @Override
    public int hashCode() {
      return Objects.hash(method, pathPattern);
    }
  }

  /** Persisted project state; public fields are the XML-serialized values. */
  public static final class State {

    /** Name of the {@link SourceType} in use; defaults to {@code CONTROLLER_ANNOTATIONS}. */
    public String sourceType = SourceType.CONTROLLER_ANNOTATIONS.name();

    /** Absolute or project-relative path of the OpenAPI document; empty when unset. */
    public String openApiFilePath = "";

    /** Paths of the collection files (Postman/Insomnia) to parse; never null. */
    public List<String> collectionFilePaths = new ArrayList<>();

    /** Exclusion rules as serialized entries; never null. */
    public List<ExclusionEntry> exclusions = new ArrayList<>();

    /**
     * Whether the tool window triggers a coverage scan on its first open in this project; defaults
     * to {@code true} so the table is never empty on open.
     */
    public boolean autoScanOnProjectOpen = true;
  }
}
