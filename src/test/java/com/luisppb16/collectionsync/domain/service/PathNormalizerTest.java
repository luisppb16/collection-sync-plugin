/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */

package com.luisppb16.collectionsync.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.luisppb16.collectionsync.domain.model.Segment;
import com.luisppb16.collectionsync.domain.model.Segment.Literal;
import com.luisppb16.collectionsync.domain.model.Segment.Variable;

@DisplayName("PathNormalizer")
class PathNormalizerTest {

    private static Literal literal(String value) {
        return new Literal(value);
    }

    private static Variable variable(String syntax) {
        return new Variable(syntax);
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("normalizationCases")
    @DisplayName("Given a raw path or URL, when it is normalized, then the documented segments are produced")
    void normalizesDocumentedCases(String raw, List<Segment> expected) {
        List<Segment> segments = PathNormalizer.normalize(raw);

        assertThat(segments).containsExactlyElementsOf(expected);
    }

    private static Stream<Arguments> normalizationCases() {
        return Stream.of(
                Arguments.of("/users", List.of(literal("users"))),
                Arguments.of("/users/", List.of(literal("users"))),
                Arguments.of("", List.of()),
                Arguments.of("   ", List.of()),
                Arguments.of("https://api.example.com/users/17",
                        List.of(literal("users"), literal("17"))),
                Arguments.of("https://api.example.com/users/17?full=true&x=1",
                        List.of(literal("users"), literal("17"))),
                Arguments.of("https://api.example.com/users/17#anchor",
                        List.of(literal("users"), literal("17"))),
                Arguments.of("https://api.example.com", List.of()),
                Arguments.of("{{baseUrl}}/users/:id",
                        List.of(literal("users"), variable(":id"))),
                Arguments.of("{{ _.base_url }}/users/{{id}}",
                        List.of(literal("users"), variable("{{id}}"))),
                Arguments.of("/users/{id}/orders/{orderId:[0-9]+}",
                        List.of(literal("users"), variable("{id}"), literal("orders"),
                                variable("{orderId:[0-9]+}"))),
                Arguments.of("/users/{ id }", List.of(literal("users"), variable("{id}"))),
                Arguments.of("users/{id}", List.of(literal("users"), variable("{id}"))),
                Arguments.of("/users/{id}", List.of(literal("users"), variable("{id}"))),
                Arguments.of("//users//17/", List.of(literal("users"), literal("17"))));
    }

    @ParameterizedTest(name = "{0} is a variable")
    @MethodSource("variableSyntaxes")
    @DisplayName("Given every supported variable dialect, when checked, then it is detected as a variable")
    void detectsVariableSyntaxes(String rawSegment) {
        assertThat(PathNormalizer.isVariable(rawSegment)).isTrue();
    }

    private static Stream<Arguments> variableSyntaxes() {
        return Stream.of(
                Arguments.of("{id}"),
                Arguments.of("{id:[0-9]+}"),
                Arguments.of("{ id }"),
                Arguments.of("{id: [0-9]+}"),
                Arguments.of(":id"),
                Arguments.of(":userId"),
                Arguments.of(":user-id"),
                Arguments.of("{{id}}"),
                Arguments.of("{{ _.id }}"),
                Arguments.of("{{ response.body.id }}"),
                Arguments.of("{{baseUrl}}"));
    }

    @ParameterizedTest(name = "{0} is a literal")
    @MethodSource("literalSegments")
    @DisplayName("Given non-variable segments, when checked, then they are kept as literals")
    void keepsNonVariableSegmentsAsLiterals(String rawSegment) {
        assertThat(PathNormalizer.isVariable(rawSegment)).isFalse();
    }

    private static Stream<Arguments> literalSegments() {
        return Stream.of(
                Arguments.of("users"),
                Arguments.of("v1.0"),
                Arguments.of("17"),
                Arguments.of(":1bad"),
                Arguments.of("a{id}b"),
                Arguments.of("{id"));
    }

    @Test
    @DisplayName("Given a null path, when it is normalized, then it fails fast")
    void failsFastOnNull() {
        assertThatIllegalArgumentException().isThrownBy(() -> PathNormalizer.normalize(null));
    }

    @Test
    @DisplayName("Given a host-only path with scheme, when a relative path has a literal first segment, then it is kept")
    void keepsLiteralFirstSegmentOfRelativePaths() {
        List<Segment> segments = PathNormalizer.normalize("api/users");

        assertThat(segments).containsExactly(literal("api"), literal("users"));
    }
}