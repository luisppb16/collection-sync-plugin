/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */

package com.luisppb16.collectionsync.domain.service;

import com.luisppb16.collectionsync.domain.model.Segment;
import com.luisppb16.collectionsync.domain.model.Segment.Literal;
import com.luisppb16.collectionsync.domain.model.Segment.Variable;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Structural matcher for normalized path segments.
 *
 * <h2>The matching rules</h2>
 *
 * <p>Two paths match when:
 *
 * <ol>
 *   <li><b>Same segment count.</b> Every path is compared segment by segment from the root; a path
 *       with three segments never matches a path with four.
 *   <li><b>Every position is compatible.</b> At one position:
 *       <ul>
 *         <li>{@code Literal} vs {@code Literal}: equal, case-sensitive text.
 *         <li>{@code Variable} vs anything: match. Variables match by <b>position</b>, never by
 *             parameter name, and a variable also matches a concrete literal value: the request
 *             {@code GET /users/17} covers the endpoint {@code GET /users/{id}}, and the collection
 *             placeholder {@code /users/:userId} covers the endpoint {@code /users/{id}}.
 *       </ul>
 * </ol>
 *
 * <p>Combined with {@link PathNormalizer} this makes {@code /users/{id}}, {@code /users/:userId},
 * {@code /users/{{id}}} and {@code /users/17} all equivalent.
 *
 * <p>The same rules also drive <b>prefix stripping</b> ({@link #stripPrefix}): a user-configured
 * base path is removed from the root of a path when its leading segments match loosely, so the same
 * structural comparison then applies to the remainder.
 */
public final class PathMatcher {

  private PathMatcher() {}

  /**
   * Checks structural equality of two normalized paths (loose rule: a variable matches any segment,
   * including concrete literal values).
   *
   * @param pattern first normalized path; must not be null
   * @param candidate second normalized path; must not be null
   * @return true when both paths have the same segment count and every position is compatible
   */
  public static boolean matches(List<Segment> pattern, List<Segment> candidate) {
    return pattern.size() == candidate.size()
        && IntStream.range(0, pattern.size())
            .allMatch(position -> looseSegment(pattern.get(position), candidate.get(position)));
  }

  /**
   * Checks strict equality of two normalized paths: a position only matches when both segments are
   * variables, or both are identical literals. Used by exclusion rules, which are generated from
   * the exact endpoint template and must not swallow sibling endpoints such as {@code /users/me}
   * when {@code /users/{id}} is excluded.
   *
   * @param pattern first normalized path; must not be null
   * @param candidate second normalized path; must not be null
   * @return true when both paths are structurally identical, variable syntax aside
   */
  public static boolean matchesStrict(List<Segment> pattern, List<Segment> candidate) {
    return pattern.size() == candidate.size()
        && IntStream.range(0, pattern.size())
            .allMatch(position -> strictSegment(pattern.get(position), candidate.get(position)));
  }

  /**
   * Removes the given prefix from a normalized path when the path starts with it, leaving the path
   * untouched otherwise.
   *
   * <p>The prefix applies to the <b>root</b> of the path only: the path is stripped when it has at
   * least as many segments as the prefix and the first {@code prefix.size()} positions are
   * compatible under the loose rule of {@link #matches} (a variable matches any segment, literals
   * compare case-sensitively). A mismatch, a path shorter than the prefix or an empty prefix
   * returns the path unchanged: a prefix found mid-path is never removed and stripping never fails.
   *
   * <p>Used to ignore a user-configured base path (e.g. {@code /api/test/v1}) on both sides of the
   * coverage comparison, whichever side declares it: the collection URL, the endpoint template or
   * both. An empty prefix (e.g. the user typed only a host, which normalizes to no segments) is a
   * no-op, so an unset base path behaves exactly like the plain structural match.
   *
   * @param segments normalized path to strip; must not be null
   * @param prefixSegments normalized prefix; must not be null, possibly empty
   * @return the path without the matching prefix, or the path itself when the prefix does not
   *     apply; never null, inputs never mutated
   */
  public static List<Segment> stripPrefix(List<Segment> segments, List<Segment> prefixSegments) {
    int prefixSize = prefixSegments.size();
    if (prefixSize == 0 || segments.size() < prefixSize) {
      return segments;
    }
    boolean startsWithPrefix =
        IntStream.range(0, prefixSize)
            .allMatch(
                position -> looseSegment(prefixSegments.get(position), segments.get(position)));
    return startsWithPrefix ? List.copyOf(segments.subList(prefixSize, segments.size())) : segments;
  }

  private static boolean looseSegment(Segment first, Segment second) {
    return switch (first) {
      case Variable ignored -> true;
      case Literal(var value) ->
          switch (second) {
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
