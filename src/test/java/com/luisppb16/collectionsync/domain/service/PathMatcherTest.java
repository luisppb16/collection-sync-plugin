/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */

package com.luisppb16.collectionsync.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.luisppb16.collectionsync.domain.model.Segment;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("PathMatcher")
class PathMatcherTest {

  @ParameterizedTest(name = "{0} vs {1} -> {2}")
  @CsvSource({
    // same structure, different variable syntax and name
    "/users/{id}, /users/:userId, true",
    "/users/{id}, /users/{{id}}, true",
    "/users/{id}, /users/17, true",
    "/users/{id}, /users/{id: [0-9]+}, true",
    "/users/{id}, /users/{{ _.id }}, true",
    // literal equality
    "/users/me, /users/me, true",
    "/users/me, /users/you, false",
    // literals must be equal when no variable is involved
    "/users/{id}, /users/{id}/orders, false",
    "/users/{id}, /users/{id}/orders/{orderId}, false",
    "/orders/{id}, /users/{id}, false",
    // trailing slashes are normalized away before matching
    "/users/{id}, /users/{id}/, true",
    // variables match by position, whatever the surrounding literals do
    "/{x}/b, /a/{y}, true"
  })
  @DisplayName("Given two raw paths, when they are matched, then the structural rule decides")
  void matchesStructurally(String first, String second, boolean expected) {
    boolean result =
        PathMatcher.matches(PathNormalizer.normalize(first), PathNormalizer.normalize(second));

    assertThat(result).isEqualTo(expected);
  }

  @ParameterizedTest
  @CsvSource({
    "/a/{x}, /a/{y}, true",
    "/a/{x}, /a/{y}, true",
    "/a/{x}, /a/b, false",
    "/a/b, /a/{y}, false",
    "/a/{x}/c, /a/{y}/c, true",
    "/a/{x}/c, /a/{y}/d, false"
  })
  @DisplayName(
      "Given an exclusion-style comparison, when matched strictly, then variables only match variables")
  void matchesStrictly(String first, String second, boolean expected) {
    boolean result =
        PathMatcher.matchesStrict(
            PathNormalizer.normalize(first), PathNormalizer.normalize(second));

    assertThat(result).isEqualTo(expected);
  }

  @Test
  @DisplayName(
      "Given empty paths, when they are matched, then they equal each other but no concrete path")
  void matchesEmptyPaths() {
    List<Segment> empty = PathNormalizer.normalize("");

    assertThat(PathMatcher.matches(empty, empty)).isTrue();
    assertThat(PathMatcher.matchesStrict(empty, empty)).isTrue();
    assertThat(PathMatcher.matches(empty, PathNormalizer.normalize("/users"))).isFalse();
  }

  @ParameterizedTest(name = "{0} strip {1} -> {2}")
  @CsvSource({
    // the base path declared only in the collection URL, the user scenario
    "{{baseUrl}}/api/test/v1/carro, /api/test/v1, /carro",
    // a prefix longer than the path leaves the path untouched
    "/carro, /api/test/v1, /carro",
    // equal sizes strip the whole path
    "/api/test/v1, /api/test/v1, ''",
    // an empty prefix is a no-op
    "/api/test/v1/carro, '', /api/test/v1/carro",
    // scheme and host are stripped by the normalizer before the prefix applies
    "https://api.example.com/api/test/v1/carro, /api/test/v1, /carro",
    // a prefix without the leading slash still applies
    "api/test/v1/carro, api/test/v1, /carro",
    // a mismatching literal leaves the path untouched, all or nothing
    "/api/test/v2/carro, /api/test/v1, /api/test/v2/carro",
    // an occurrence mid-path is never a root prefix
    "/prefix/api/test/v1/carro, /api/test/v1, /prefix/api/test/v1/carro",
    // a variable in the prefix matches any segment at that position
    "/api/v1/carro, /api/{{version}}, /carro",
    "/users/17/orders, /users/{id}, /orders",
    // variables match loosely in the prefix positions
    "/users/{id}, /users/:userId, ''",
    // a host-only prefix normalizes to no segments, so it is a no-op
    "{{baseUrl}}/carro, https://api.example.com, {{baseUrl}}/carro"
  })
  @DisplayName(
      "Given a path and a base-path prefix, when the prefix is stripped, then only a full root match is removed")
  void stripsOnlyFullRootMatches(String rawPath, String rawPrefix, String expectedRaw) {
    List<Segment> stripped =
        PathMatcher.stripPrefix(
            PathNormalizer.normalize(rawPath), PathNormalizer.normalize(rawPrefix));

    assertThat(stripped).containsExactlyElementsOf(PathNormalizer.normalize(expectedRaw));
  }

  @Test
  @DisplayName(
      "Given a matching path, when the prefix is stripped, then the inputs stay untouched and the result is immutable")
  void stripDoesNotMutateInputsAndReturnsAnImmutableList() {
    List<Segment> path = new ArrayList<>(PathNormalizer.normalize("/api/test/v1/carro"));
    List<Segment> prefix = new ArrayList<>(PathNormalizer.normalize("/api/test/v1"));

    List<Segment> stripped = PathMatcher.stripPrefix(path, prefix);

    assertThat(stripped).containsExactlyElementsOf(PathNormalizer.normalize("/carro"));
    assertThatThrownBy(() -> stripped.add(new Segment.Literal("extra")))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThat(path).containsExactlyElementsOf(PathNormalizer.normalize("/api/test/v1/carro"));
    assertThat(prefix).containsExactlyElementsOf(PathNormalizer.normalize("/api/test/v1"));
  }
}
