/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */

package com.luisppb16.collectionsync.settings;

import static org.assertj.core.api.Assertions.assertThat;

import com.luisppb16.collectionsync.domain.model.HttpMethod;
import com.luisppb16.collectionsync.settings.EndpointCoverageSettings.ExclusionEntry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("EndpointCoverageConfigurable")
class EndpointCoverageConfigurableTest {

  @Test
  @DisplayName(
      "Given a method cell edited through the combo box, when it is mapped to a state entry, then the enum is converted to its name")
  void mapsEnumMethodCellToName() {
    ExclusionEntry entry =
        EndpointCoverageConfigurable.exclusionEntryOf(HttpMethod.DELETE, "/users/{id}");

    assertThat(entry.getMethod()).isEqualTo("DELETE");
    assertThat(entry.getPathPattern()).isEqualTo("/users/{id}");
  }

  @Test
  @DisplayName(
      "Given method and path cells populated from persisted text, when they are mapped to a state entry, then the text is kept")
  void mapsTextCellsAsIs() {
    ExclusionEntry entry = EndpointCoverageConfigurable.exclusionEntryOf("POST", "/ping");

    assertThat(entry.getMethod()).isEqualTo("POST");
    assertThat(entry.getPathPattern()).isEqualTo("/ping");
  }

  @Test
  @DisplayName(
      "Given null cells, when they are mapped to a state entry, then empty strings are persisted")
  void mapsNullCellsToEmptyStrings() {
    ExclusionEntry entry = EndpointCoverageConfigurable.exclusionEntryOf(null, null);

    assertThat(entry.getMethod()).isEmpty();
    assertThat(entry.getPathPattern()).isEmpty();
  }
}
