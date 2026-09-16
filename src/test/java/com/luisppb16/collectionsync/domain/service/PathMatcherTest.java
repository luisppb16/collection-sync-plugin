/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */

package com.luisppb16.collectionsync.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.luisppb16.collectionsync.domain.model.Segment;

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
            "/{x}/b, /a/{y}, true"})
    @DisplayName("Given two raw paths, when they are matched, then the structural rule decides")
    void matchesStructurally(String first, String second, boolean expected) {
        boolean result = PathMatcher.matches(
                PathNormalizer.normalize(first), PathNormalizer.normalize(second));

        assertThat(result).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "/a/{x}, /a/{y}, true",
            "/a/{x}, /a/{y}, true",
            "/a/{x}, /a/b, false",
            "/a/b, /a/{y}, false",
            "/a/{x}/c, /a/{y}/c, true",
            "/a/{x}/c, /a/{y}/d, false"})
    @DisplayName("Given an exclusion-style comparison, when matched strictly, then variables only match variables")
    void matchesStrictly(String first, String second, boolean expected) {
        boolean result = PathMatcher.matchesStrict(
                PathNormalizer.normalize(first), PathNormalizer.normalize(second));

        assertThat(result).isEqualTo(expected);
    }

    @Test
    @DisplayName("Given empty paths, when they are matched, then they equal each other but no concrete path")
    void matchesEmptyPaths() {
        List<Segment> empty = PathNormalizer.normalize("");

        assertThat(PathMatcher.matches(empty, empty)).isTrue();
        assertThat(PathMatcher.matchesStrict(empty, empty)).isTrue();
        assertThat(PathMatcher.matches(empty, PathNormalizer.normalize("/users"))).isFalse();
    }
}