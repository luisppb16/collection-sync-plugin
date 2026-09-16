/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */

package com.luisppb16.collectionsync.domain.service;

import java.util.List;
import java.util.stream.IntStream;

import org.jetbrains.annotations.NotNull;

import com.luisppb16.collectionsync.domain.model.Segment;
import com.luisppb16.collectionsync.domain.model.Segment.Literal;
import com.luisppb16.collectionsync.domain.model.Segment.Variable;

/**
 * Structural matcher for normalized path segments.
 *
 * <h2>The matching rules</h2>
 *
 * <p>Two paths match when:
 * <ol>
 *   <li><b>Same segment count.</b> Every path is compared segment by segment from the root; a
 *   path with three segments never matches a path with four.</li>
 *   <li><b>Every position is compatible.</b> At one position:
 *   <ul>
 *     <li>{@code Literal} vs {@code Literal}: equal, case-sensitive text.</li>
 *     <li>{@code Variable} vs anything: match. Variables match by <b>position</b>, never by
 *     parameter name, and a variable also matches a concrete literal value: the request
 *     {@code GET /users/17} covers the endpoint {@code GET /users/{id}}, and the collection
 *     placeholder {@code /users/:userId} covers the endpoint {@code /users/{id}}.</li>
 *   </ul></li>
 * </ol>
 *
 * <p>Combined with {@link PathNormalizer} this makes {@code /users/{id}},
 * {@code /users/:userId}, {@code /users/{{id}}} and {@code /users/17} all equivalent.
 */
public final class PathMatcher {

    private PathMatcher() {
    }

    /**
     * Checks structural equality of two normalized paths (loose rule: a variable matches any
     * segment, including concrete literal values).
     *
     * @param pattern   first normalized path; must not be null
     * @param candidate second normalized path; must not be null
     * @return true when both paths have the same segment count and every position is compatible
     */
    public static boolean matches(List<Segment> pattern, List<Segment> candidate) {
        return pattern.size() == candidate.size()
                && IntStream.range(0, pattern.size())
                .allMatch(position -> looseSegment(pattern.get(position), candidate.get(position)));
    }

    /**
     * Checks strict equality of two normalized paths: a position only matches when both segments
     * are variables, or both are identical literals. Used by exclusion rules, which are generated
     * from the exact endpoint template and must not swallow sibling endpoints such as
     * {@code /users/me} when {@code /users/{id}} is excluded.
     *
     * @param pattern   first normalized path; must not be null
     * @param candidate second normalized path; must not be null
     * @return true when both paths are structurally identical, variable syntax aside
     */
    public static boolean matchesStrict(List<Segment> pattern, List<Segment> candidate) {
        return pattern.size() == candidate.size()
                && IntStream.range(0, pattern.size())
                .allMatch(position -> strictSegment(pattern.get(position), candidate.get(position)));
    }

    private static boolean looseSegment(Segment first, Segment second) {
        return switch (first) {
            case Variable ignored -> true;
            case Literal(var value) -> switch (second) {
                case Variable ignored -> true;
                case Literal(var other) -> value.equals(other);
            };
        };
    }

    private static boolean strictSegment(Segment first, Segment second) {
        return switch (first) {
            case Variable ignored -> second instanceof Variable;
            case Literal(var value) -> second instanceof Literal(var other) && value.equals(other);
        };
    }
}