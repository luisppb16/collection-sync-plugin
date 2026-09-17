/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */

package com.luisppb16.collectionsync.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Checks the integrity of the resource bundles: every key of the default (English) bundle must be
 * translated in the Spanish bundle (and vice versa), no value of either bundle may be empty, and
 * {@link EndpointCoverageBundle#message} must resolve known keys, including action texts.
 */
@DisplayName("EndpointCoverageBundle")
class EndpointCoverageBundleTest {

  private static final String DEFAULT_BUNDLE = "messages/EndpointCoverageBundle.properties";
  private static final String SPANISH_BUNDLE = "messages/EndpointCoverageBundle_es.properties";

  private static Stream<Named<String>> bundles() {
    return Stream.of(
        Named.of("default (English) bundle", DEFAULT_BUNDLE),
        Named.of("Spanish bundle", SPANISH_BUNDLE));
  }

  private static Properties loadBundle(String bundleName) throws IOException {
    Properties bundle = new Properties();
    try (InputStream stream =
        EndpointCoverageBundleTest.class.getClassLoader().getResourceAsStream(bundleName)) {
      if (stream == null) {
        throw new IllegalStateException("Bundle not found on the classpath: " + bundleName);
      }
      try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
        bundle.load(reader);
      }
    }
    return bundle;
  }

  @Test
  @DisplayName(
      "Given the default and the Spanish bundle, when their keys are compared, then they are identical")
  void bundlesHaveTheSameKeySets() throws IOException {
    Properties englishBundle = loadBundle(DEFAULT_BUNDLE);
    Properties spanishBundle = loadBundle(SPANISH_BUNDLE);

    Set<Object> englishKeys = englishBundle.keySet();
    Set<Object> spanishKeys = spanishBundle.keySet();

    assertThat(englishKeys)
        .as("every default key must be translated in the Spanish bundle")
        .containsExactlyInAnyOrderElementsOf(spanishKeys);
    assertThat(spanishKeys)
        .as("the Spanish bundle must not define keys missing from the default bundle")
        .containsExactlyInAnyOrderElementsOf(englishKeys);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("bundles")
  @DisplayName("Given a resource bundle, when its values are read, then no key is empty")
  void bundleHasNoEmptyValues(String bundleName) throws IOException {
    Properties bundle = loadBundle(bundleName);

    assertThat(bundle)
        .allSatisfy(
            (key, value) -> {
              assertThat(key).asString().isNotBlank();
              assertThat(value).asString().isNotBlank();
            });
  }

  @Test
  @DisplayName(
      "Given a known key, when it is resolved, then a non-empty message with the arguments formatted is returned")
  void resolvesKnownMessageWithArguments() {
    String message =
        EndpointCoverageBundle.message("service.error.collection.parse", "a.json", "boom");

    assertThat(message).isNotBlank();
    assertThat(message).contains("a.json");
    assertThat(message).contains("boom");
  }

  @Test
  @DisplayName(
      "Given the plugin action ids, when their texts and descriptions are resolved, then they are non-empty")
  void resolvesActionTextsAndDescriptions() {
    String[] actionIds = {
      "collectionsync.AnalyzeEndpointsCoverage",
      "collectionsync.UseAsCollection",
      "collectionsync.UseAsOpenApiSource"
    };

    for (String actionId : actionIds) {
      assertThat(EndpointCoverageBundle.actionText(actionId))
          .as("action text of %s", actionId)
          .isNotBlank();
      assertThat(EndpointCoverageBundle.actionDescription(actionId))
          .as("action description of %s", actionId)
          .isNotBlank();
    }
  }
}
