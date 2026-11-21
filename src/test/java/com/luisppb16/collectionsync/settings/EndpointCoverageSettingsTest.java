/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */

package com.luisppb16.collectionsync.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.luisppb16.collectionsync.domain.model.ExclusionRule;
import com.luisppb16.collectionsync.domain.model.HttpMethod;
import com.luisppb16.collectionsync.settings.EndpointCoverageSettings.CollectionBasePath;
import com.luisppb16.collectionsync.settings.EndpointCoverageSettings.CollectionBinding;
import com.luisppb16.collectionsync.settings.EndpointCoverageSettings.ExclusionEntry;
import com.luisppb16.collectionsync.settings.EndpointCoverageSettings.SourceType;
import com.luisppb16.collectionsync.settings.EndpointCoverageSettings.State;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
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
    assertThat(state.basePath).isEmpty();
    assertThat(state.collectionFilePaths).isEmpty();
    assertThat(state.collectionBasePaths).isEmpty();
    assertThat(state.exclusions).isEmpty();
    assertThat(state.autoScanOnProjectOpen).isTrue();
    assertThat(settings.getBasePath()).isEmpty();
    assertThat(settings.collectionBasePathsByPath()).isEmpty();
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
    loadedState.basePath = null;
    loadedState.collectionFilePaths = null;
    loadedState.collectionBasePaths = null;
    loadedState.exclusions = null;

    settings.loadState(loadedState);

    State state = settings.getState();
    assertThat(state.sourceType).isEqualTo("CONTROLLER_ANNOTATIONS");
    assertThat(state.openApiFilePath).isEmpty();
    assertThat(state.basePath).isEmpty();
    assertThat(state.collectionFilePaths).isEmpty();
    assertThat(state.collectionBasePaths).isEmpty();
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

    secondState.basePath = "/api/v1";
    assertThat(EndpointCoverageSettings.equalsState(firstState, secondState)).isFalse();

    secondState.basePath = "  ";
    assertThat(EndpointCoverageSettings.equalsState(firstState, secondState)).isTrue();

    secondState.collectionFilePaths = new ArrayList<>(List.of("postman.json"));
    assertThat(EndpointCoverageSettings.equalsState(firstState, secondState)).isFalse();

    secondState.collectionFilePaths = new ArrayList<>();
    secondState.exclusions = new ArrayList<>(List.of(new ExclusionEntry("GET", "/actuator")));
    assertThat(EndpointCoverageSettings.equalsState(firstState, secondState)).isFalse();

    secondState.exclusions = new ArrayList<>();
    secondState.collectionBasePaths =
        new ArrayList<>(List.of(new CollectionBasePath("/repo/users.json", "/service-a")));
    assertThat(EndpointCoverageSettings.equalsState(firstState, secondState)).isFalse();

    secondState.collectionBasePaths = new ArrayList<>();
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
      "Given a state with a base path, when it is copied for update, then the base path is carried over")
  void copiesBasePathWhenUpdatingState() {
    State current = new State();
    current.basePath = "/api/test/v1";

    State updated = EndpointCoverageSettings.copyForUpdate(current);

    assertThat(updated.basePath).isEqualTo("/api/test/v1");
  }

  @Test
  @DisplayName(
      "Given a state with a collection binding, when it is copied for update, then the binding is not shared")
  void doesNotShareCollectionBindingsWhenUpdatingState() {
    State current = new State();
    current.collectionFilePaths.add("/repo/users.json");
    current.collectionBindings.add(new CollectionBinding("/repo/users.json", "service-a"));

    State updated = EndpointCoverageSettings.copyForUpdate(current);
    updated.collectionBindings.getFirst().setModuleName("service-b");

    assertThat(current.collectionBindings.getFirst().getModuleName()).isEqualTo("service-a");
  }

  @Test
  @DisplayName(
      "Given a state with an exclusion, when it is copied for update, then the entry is not shared")
  void doesNotShareExclusionEntriesWhenUpdatingState() {
    State current = new State();
    current.exclusions.add(new ExclusionEntry("GET", "/users/{id}"));

    State updated = EndpointCoverageSettings.copyForUpdate(current);
    updated.exclusions.getFirst().setPathPattern("/other");

    assertThat(current.exclusions.getFirst().getPathPattern()).isEqualTo("/users/{id}");
  }

  @Test
  @DisplayName(
      "Given a configured base path, when the state is stored and read back, then the value is kept")
  void roundTripsBasePath() {
    State configured = new State();
    configured.basePath = "/api/test/v1";

    settings.setState(configured);

    assertThat(settings.getBasePath()).isEqualTo("/api/test/v1");
    assertThat(settings.getState().basePath).isEqualTo("/api/test/v1");
  }

  @Test
  @DisplayName(
      "Given per-collection base paths, when the state is loaded, then only known collections with a non-blank value survive")
  void normalizesCollectionBasePathsOnLoad() {
    State loadedState = new State();
    loadedState.collectionFilePaths = new ArrayList<>(List.of("/repo/a.json", "/repo/b.json"));
    loadedState.collectionBasePaths =
        new ArrayList<>(
            Arrays.asList(
                new CollectionBasePath("/repo/a.json", " /service-a "),
                new CollectionBasePath("/repo/b.json", "   "),
                new CollectionBasePath("/repo/unknown.json", "/service-x"),
                null));

    settings.loadState(loadedState);

    assertThat(settings.collectionBasePathsByPath())
        .containsExactly(Map.entry("/repo/a.json", "/service-a"));
  }

  @Test
  @DisplayName(
      "Given a state with a collection base path, when it is copied for update, then the entry is not shared")
  void doesNotShareCollectionBasePathsWhenUpdatingState() {
    State current = new State();
    current.collectionFilePaths.add("/repo/users.json");
    current.collectionBasePaths.add(new CollectionBasePath("/repo/users.json", "/service-a"));

    State updated = EndpointCoverageSettings.copyForUpdate(current);
    updated.collectionBasePaths.getFirst().setBasePath("/service-b");

    assertThat(current.collectionBasePaths.getFirst().getBasePath()).isEqualTo("/service-a");
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

  @Test
  @DisplayName(
      "Given collection bindings of mixed validity, when the state is loaded, then only known, bound and unique paths survive")
  void normalizesCollectionBindingsOnLoad() {
    State loadedState = new State();
    loadedState.collectionFilePaths = new ArrayList<>(List.of("/repo/a.json", "/repo/b.json"));
    loadedState.collectionBindings =
        new ArrayList<>(
            Arrays.asList(
                new CollectionBinding("/repo/a.json", "service-a"),
                new CollectionBinding("/repo/a.json", "service-duplicate"),
                new CollectionBinding("/repo/unknown.json", "service-x"),
                new CollectionBinding(null, "service-y"),
                new CollectionBinding("/repo/b.json", "   "),
                null));

    settings.loadState(loadedState);

    assertThat(settings.collectionBindingsByPath())
        .containsExactly(Map.entry("/repo/a.json", "service-a"));
  }

  @Test
  @DisplayName(
      "Given the no-argument entry bean, when setters are used, then it behaves like the value constructor")
  void supportsDefaultConstructionOfEntries() {
    ExclusionEntry entry = new ExclusionEntry();

    entry.setMethod("GET");
    entry.setPathPattern("/actuator");

    assertThat(entry).isEqualTo(new ExclusionEntry("GET", "/actuator"));
    assertThat(new ExclusionEntry().equals(new ExclusionEntry())).isTrue();
  }

  @Test
  @DisplayName(
      "Given a collection binding bean, when accessors are used, then values round-trip and equality holds")
  void roundTripsCollectionBindings() {
    CollectionBinding binding = new CollectionBinding("/repo/users.json", "service-a");

    binding.setPath("/repo/orders.json");
    binding.setModuleName("service-b");

    assertThat(binding.getPath()).isEqualTo("/repo/orders.json");
    assertThat(binding.getModuleName()).isEqualTo("service-b");
    assertThat(binding).isEqualTo(new CollectionBinding("/repo/orders.json", "service-b"));
    assertThat(binding).isNotEqualTo(new CollectionBinding("/repo/users.json", "service-b"));
    assertThat(binding).hasSameHashCodeAs(new CollectionBinding("/repo/orders.json", "service-b"));
    assertThat(binding).isNotEqualTo("another type");
    assertThat(new CollectionBinding()).isNotEqualTo(binding);
  }

  @Test
  @DisplayName(
      "Given a collection base path bean, when accessors are used, then values round-trip and equality holds")
  void roundTripsCollectionBasePathBeans() {
    CollectionBasePath entry = new CollectionBasePath("/repo/users.json", "/service-a");

    entry.setPath("/repo/orders.json");
    entry.setBasePath("/service-b");

    assertThat(entry.getPath()).isEqualTo("/repo/orders.json");
    assertThat(entry.getBasePath()).isEqualTo("/service-b");
    assertThat(entry).isEqualTo(new CollectionBasePath("/repo/orders.json", "/service-b"));
    assertThat(entry).isNotEqualTo(new CollectionBasePath("/repo/users.json", "/service-b"));
    assertThat(entry).hasSameHashCodeAs(new CollectionBasePath("/repo/orders.json", "/service-b"));
    assertThat(entry).isNotEqualTo("another type");
    assertThat(new CollectionBasePath()).isNotEqualTo(entry);
  }

  @Test
  @DisplayName(
      "Given a persisted source type and OpenAPI path, when they round-trip, then the typed accessors reflect them")
  void exposesSourceTypeAndOpenApiPath() {
    State configured = new State();
    configured.sourceType = SourceType.OPEN_API.name();
    configured.openApiFilePath = "/repo/openapi.yaml";

    settings.setState(configured);

    assertThat(settings.getSourceType()).isEqualTo(SourceType.OPEN_API);
    assertThat(settings.getOpenApiFilePath()).isEqualTo("/repo/openapi.yaml");
  }

  @Test
  @DisplayName(
      "Given collection paths and bindings, when they are read, then the accessors expose copies and the by-path map")
  void exposesCollectionPathsAndBindings() {
    State configured = new State();
    configured.collectionFilePaths.add("/repo/users.json");
    configured.collectionBindings.add(new CollectionBinding("/repo/users.json", "service-a"));

    settings.setState(configured);

    assertThat(settings.getCollectionFilePaths()).containsExactly("/repo/users.json");
    assertThat(settings.getCollectionBindings())
        .extracting(CollectionBinding::getModuleName)
        .containsExactly("service-a");
    assertThat(settings.collectionBindingsByPath())
        .containsExactly(Map.entry("/repo/users.json", "service-a"));
    assertThat(settings.getCollectionFilePaths()).isUnmodifiable();
    assertThat(settings.getCollectionBindings()).isUnmodifiable();
  }

  @Test
  @DisplayName(
      "Given existing settings, when they are updated, then the mutation is applied and previous values survive")
  void appliesUpdatesAndKeepsPreviousValues() {
    State configured = new State();
    configured.basePath = "/api/v1";
    settings.setState(configured);

    settings.update(updated -> {
      updated.openApiFilePath = "/repo/openapi.yaml";
      updated.collectionFilePaths.add("/repo/users.json");
    });

    assertThat(settings.getBasePath()).isEqualTo("/api/v1");
    assertThat(settings.getOpenApiFilePath()).isEqualTo("/repo/openapi.yaml");
    assertThat(settings.getCollectionFilePaths()).containsExactly("/repo/users.json");
  }

  @Test
  @DisplayName("Given a null update mutation, when it is applied, then it fails fast")
  void rejectsNullUpdateMutation() {
    assertThatIllegalArgumentException().isThrownBy(() -> settings.update(null));
  }

  @Test
  @DisplayName(
      "Given a source type, when it is rendered, then a localized label instead of the persisted name is used")
  void rendersLocalizedSourceTypeNames() {
    assertThat(SourceType.OPEN_API.toString())
        .isNotBlank()
        .isNotEqualTo(SourceType.OPEN_API.name());
    assertThat(SourceType.CONTROLLER_ANNOTATIONS.toString()).isNotBlank();
    assertThat(SourceType.OPEN_API_AND_CONTROLLERS.toString()).isNotBlank();
  }
}
