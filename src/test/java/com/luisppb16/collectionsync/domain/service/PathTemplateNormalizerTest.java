/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */
package com.luisppb16.collectionsync.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.luisppb16.collectionsync.domain.service.PathTemplateNormalizer.Literal;
import com.luisppb16.collectionsync.domain.service.PathTemplateNormalizer.PathTemplate;
import com.luisppb16.collectionsync.domain.service.PathTemplateNormalizer.Segment;
import com.luisppb16.collectionsync.domain.service.PathTemplateNormalizer.Variable;
import com.luisppb16.collectionsync.domain.service.PathTemplateNormalizer.Wildcard;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PathTemplateNormalizerTest {

  @Test
  @DisplayName("Given a Spring template with regex, when normalized, then the name survives without the regex")
  void normalizesSpringRegexVariable() {
    PathTemplate template = PathTemplateNormalizer.normalizeEndpointTemplate("/users", "/{id:\\d+}");

    assertThat(template.segments())
        .hasSize(2)
        .satisfiesExactly(
            first -> assertThat(((Literal) first).value()).isEqualTo("users"),
            second -> assertThat(((Variable) second).name()).isEqualTo("id"));
  }

  @Test
  @DisplayName("Given a JAX-RS plain variable, when normalized, then it is a Variable segment")
  void normalizesJaxrsVariable() {
    PathTemplate template = PathTemplateNormalizer.normalizeEndpointTemplate("/books", "/{isbn}");

    List<Segment> segments = template.segments();
    assertThat(segments).hasSize(2);
    assertThat(segments.get(1)).isEqualTo(new Variable("isbn"));
  }

  @Test
  @DisplayName("Given a colon-style variable, when normalized, then it is a Variable segment")
  void normalizesColonVariable() {
    PathTemplate template = PathTemplateNormalizer.normalizeEndpointTemplate("", ":id");

    List<Segment> segments = template.segments();
    assertThat(segments).hasSize(1);
    assertThat(segments.get(0)).isEqualTo(new Variable("id"));
  }

  @Test
  @DisplayName("Given a wildcard, when normalized, then it is a Wildcard segment")
  void normalizesWildcard() {
    PathTemplate template = PathTemplateNormalizer.normalizeEndpointTemplate("/static", "/**");

    List<Segment> segments = template.segments();
    assertThat(segments).hasSize(2);
    assertThat(segments.get(1)).isInstanceOf(Wildcard.class);
  }

  @Test
  @DisplayName("Given a Postman URL with host placeholder and query, when normalized, then host and query are dropped")
  void normalizesPostmanUrl() {
    PathTemplate template = PathTemplateNormalizer.normalizeRequestUrl("{{baseUrl}}/api/users/{{id}}?include=roles");

    List<Segment> segments = template.segments();
    assertThat(segments).hasSize(3);
    assertThat(segments.get(0)).isEqualTo(new Literal("api"));
    assertThat(segments.get(1)).isEqualTo(new Literal("users"));
    assertThat(segments.get(2)).isEqualTo(new Variable("id"));
  }

  @Test
  @DisplayName("Given an Insomnia URL with _. variables, when normalized, then the property name survives")
  void normalizesInsomniaUrl() {
    PathTemplate template = PathTemplateNormalizer.normalizeRequestUrl("{{ _.base_url }}/api/users/{{ _.userId }}");

    List<Segment> segments = template.segments();
    assertThat(segments).hasSize(3);
    assertThat(segments.get(2)).isEqualTo(new Variable("userId"));
  }

  @Test
  @DisplayName("Given a resolved URL with scheme and host, when normalized, then only the path remains")
  void normalizesResolvedUrl() {
    PathTemplate template = PathTemplateNormalizer.normalizeRequestUrl("https://api.example.com/v1/ping");

    List<Segment> segments = template.segments();
    assertThat(segments).hasSize(2);
    assertThat(segments.get(0)).isEqualTo(new Literal("v1"));
    assertThat(segments.get(1)).isEqualTo(new Literal("ping"));
  }

  @Test
  @DisplayName("Given a root path, when normalized, then the template has no segments")
  void normalizesRootPath() {
    PathTemplate template = PathTemplateNormalizer.normalizeEndpointTemplate("/", "");

    assertThat(template.segments()).isEmpty();
  }

  @Test
  @DisplayName("Given duplicate slashes, when normalized, then empty segments are skipped")
  void skipsEmptySegments() {
    PathTemplate template = PathTemplateNormalizer.normalizeEndpointTemplate("/api//users", "/");

    assertThat(template.segments()).hasSize(2);
  }

  @Test
  @DisplayName("Given a blank request URL, when normalized, then it fails fast")
  void rejectsBlankRequestUrl() {
    assertThatIllegalArgumentException().isThrownBy(() -> PathTemplateNormalizer.normalizeRequestUrl("  "));
  }
}