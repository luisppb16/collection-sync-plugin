/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */

package com.luisppb16.collectionsync.settings;

import static org.assertj.core.api.Assertions.assertThat;

import com.luisppb16.collectionsync.domain.model.ExclusionRule;
import com.luisppb16.collectionsync.domain.model.HttpMethod;
import com.luisppb16.collectionsync.settings.EndpointCoverageSettings.ExclusionEntry;
import com.luisppb16.collectionsync.settings.EndpointCoverageSettings.SourceType;
import com.luisppb16.collectionsync.settings.EndpointCoverageSettings.State;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("EndpointCoverageSettings")
class EndpointCoverageSettingsTest {

  private EndpointCoverageSettings settings;

  @BeforeEach
  void setUp() {
    settings = new EndpointCoverageSettings();
  }

  @Test
  @DisplayName(
      "Given a fresh settings instance, when the state is read, then every field holds its default")
  void exposesDefaultsOnFreshState() {
    State state = settings.getState();

    assertThat(state.sourceType).isEqualTo("CONTROLLER_ANNOTATIONS");
    assertThat(state.openApiFilePath).isEmpty();
    assertThat(state.collectionFilePaths).isEmpty();
    assertThat(state.exclusions).isEmpty();
    assertThat(state.autoScanOnProjectOpen).isTrue();
    assertThat(settings.isAutoScanOnProjectOpen()).isTrue();
    assertThat(settings.toRules()).isEmpty();
  }

  @Test
  @DisplayName(
      "Given a persisted state with the automatic scan toggled, when it round-trips, then the value is kept")
  void roundTripsAutoScanOnProjectOpen() {
    State disabledState = new State();
    disabledState.autoScanOnProjectOpen = false;

    settings.setState(disabledState);

    assertThat(settings.isAutoScanOnProjectOpen()).isFalse();
    assertThat(settings.getState().autoScanOnProjectOpen).isFalse();

    settings.loadState(new State());

    assertThat(settings.isAutoScanOnProjectOpen()).isTrue();
  }

  @ParameterizedTest
  @ValueSource(strings = {"OPEN_API", "CONTROLLER_ANNOTATIONS"})
  @DisplayName(
      "Given a known persisted source type, when it is parsed, then it maps to its constant")
  void parsesKnownSourceTypes(String raw) {
    assertThat(SourceType.from(raw)).isEqualTo(SourceType.valueOf(raw));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"  ", "GRAPEFRUIT"})
  @DisplayName(
      "Given an unknown persisted source type, when it is parsed, then it falls back to CONTROLLER_ANNOTATIONS")
  void fallsBackToDefaultSourceTypes(String raw) {
    assertThat(SourceType.from(raw)).isEqualTo(SourceType.CONTROLLER_ANNOTATIONS);
  }

  @Test
  @DisplayName(
      "Given a partially written state, when it is loaded, then null lists and strings are normalized")
  void normalizesNullsOnLoadState() {
    State loadedState = new State();
    loadedState.sourceType = null;
    loadedState.openApiFilePath = null;
    loadedState.collectionFilePaths = null;
    loadedState.exclusions = null;

    settings.loadState(loadedState);

    State state = settings.getState();
    assertThat(state.sourceType).isEqualTo("CONTROLLER_ANNOTATIONS");
    assertThat(state.openApiFilePath).isEmpty();
    assertThat(state.collectionFilePaths).isEmpty();
    assertThat(state.exclusions).isEmpty();
    assertThat(settings.toRules()).isEmpty();
  }

  @Test
  @DisplayName(
      "Given loaded state lists, when they are read back, then mutations do not leak into the settings")
  void copiesLoadedLists() {
    State loadedState = new State();
    loadedState.collectionFilePaths = new ArrayList<>(List.of("postman.json"));
    loadedState.exclusions =
        new ArrayList<>(List.of(new ExclusionEntry("GET", "/actuator/{name}")));

    settings.loadState(loadedState);
    loadedState.collectionFilePaths.clear();
    loadedState.exclusions.clear();

    assertThat(settings.getState().collectionFilePaths).containsExactly("postman.json");
    assertThat(settings.getState().exclusions).hasSize(1);
  }

  @Test
  @DisplayName(
      "Given serialized exclusion entries, when rules are derived, then each valid entry becomes a domain rule")
  void convertsEntriesToRules() {
    State loadedState = new State();
    loadedState.exclusions =
        new ArrayList<>(
            List.of(
                new ExclusionEntry("get", "/actuator/{name}"),
                new ExclusionEntry("POST", "/internal/*")));
    settings.loadState(loadedState);

    assertThat(settings.toRules())
        .containsExactly(
            new ExclusionRule(HttpMethod.GET, "/actuator/{name}"),
            new ExclusionRule(HttpMethod.POST, "/internal/*"));
  }

  @ParameterizedTest
  @CsvSource({
    "'', /actuator, false",
    "'GET', '', false",
    "'TELEPORT', /actuator, false",
    "'GET', /actuator/{name}, true"
  })
  @DisplayName(
      "Given entries of mixed validity, when rules are derived, then only fully valid entries survive")
  void skipsInvalidEntriesWhenConverting(String method, String pathPattern, boolean expectedValid) {
    State loadedState = new State();
    loadedState.exclusions = new ArrayList<>(List.of(new ExclusionEntry(method, pathPattern)));
    settings.loadState(loadedState);

    List<ExclusionRule> rules = settings.toRules();

    if (expectedValid) {
      assertThat(rules).containsExactly(new ExclusionRule(HttpMethod.GET, "/actuator/{name}"));
    } else {
      assertThat(rules).isEmpty();
    }
  }

  @Test
  @DisplayName(
      "Given a null entry among valid ones, when rules are derived, then the null is skipped")
  void skipsNullEntriesWhenConverting() {
    State loadedState = new State();
    loadedState.exclusions =
        new ArrayList<>(Arrays.asList(null, new ExclusionEntry("GET", "/actuator")));
    settings.loadState(loadedState);

    assertThat(settings.toRules()).containsExactly(new ExclusionRule(HttpMethod.GET, "/actuator"));
  }

  @Test
  @DisplayName("Given two states, when they are compared, then only equivalent states are equal")
  void detectsStateDifferences() {
    State firstState = new State();
    State secondState = new State();

    assertThat(EndpointCoverageSettings.equalsState(firstState, secondState)).isTrue();

    secondState.sourceType = "OPEN_API";
    assertThat(EndpointCoverageSettings.equalsState(firstState, secondState)).isFalse();

    secondState.sourceType = "CONTROLLER_ANNOTATIONS";
    secondState.openApiFilePath = "   ";
    assertThat(EndpointCoverageSettings.equalsState(firstState, secondState)).isTrue();

    secondState.collectionFilePaths = new ArrayList<>(List.of("postman.json"));
    assertThat(EndpointCoverageSettings.equalsState(firstState, secondState)).isFalse();

    secondState.collectionFilePaths = new ArrayList<>();
    secondState.exclusions = new ArrayList<>(List.of(new ExclusionEntry("GET", "/actuator")));
    assertThat(EndpointCoverageSettings.equalsState(firstState, secondState)).isFalse();

    secondState.exclusions = new ArrayList<>();
    secondState.autoScanOnProjectOpen = false;
    assertThat(EndpointCoverageSettings.equalsState(firstState, secondState)).isFalse();
  }

  @Test
  @DisplayName("Given null states, when they are compared, then only two nulls are equal")
  void handlesNullStatesInComparison() {
    assertThat(EndpointCoverageSettings.equalsState(null, null)).isTrue();
    assertThat(EndpointCoverageSettings.equalsState(null, new State())).isFalse();
    assertThat(EndpointCoverageSettings.equalsState(new State(), null)).isFalse();
  }

  @Test
  @DisplayName(
      "Given an entry bean, when accessors are used, then values round-trip and equality holds")
  void roundTripsExclusionEntries() {
    ExclusionEntry entry = new ExclusionEntry("GET", "/actuator");

    entry.setMethod("POST");
    entry.setPathPattern("/internal");

    assertThat(entry.getMethod()).isEqualTo("POST");
    assertThat(entry.getPathPattern()).isEqualTo("/internal");
    assertThat(entry).isEqualTo(new ExclusionEntry("POST", "/internal"));
    assertThat(entry).isNotEqualTo(new ExclusionEntry("GET", "/internal"));
    assertThat(entry).hasSameHashCodeAs(new ExclusionEntry("POST", "/internal"));
  }
}
