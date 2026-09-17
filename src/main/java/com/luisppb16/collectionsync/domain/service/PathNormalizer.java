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
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Converts a raw path or URL into the list of {@link Segment}s used by the matching engine.
 *
 * <h2>The normalization algorithm, step by step</h2>
 *
 * <p>The same rules are applied to endpoint paths (OpenAPI paths, controller mappings) and to
 * collection request URLs (Postman, Insomnia), so both sides of the comparison are normalized
 * identically before matching.
 *
 * <ol>
 *   <li><b>Trim.</b> Surrounding whitespace is removed. An empty input yields zero segments.
 *   <li><b>Strip query and fragment.</b> Everything from the first {@code ?} or {@code #} onwards
 *       is discarded: query strings never take part in the path comparison.
 *   <li><b>Strip scheme and host.</b> If the input contains {@code ://} (e.g. {@code
 *       https://api.example.com/users/1}), everything up to the first {@code /} after the scheme is
 *       discarded, leaving only the path. A bare host yields zero segments.
 *   <li><b>Drop host-like variable.</b> Collections frequently write {@code {{baseUrl}}/users/:id}
 *       or {@code {{ _.base_url }}/users} without a scheme, so the host appears as a leading
 *       variable segment of a path that does not start with {@code /}. When the input does not
 *       start with {@code /} and its first segment is variable-like (see rule 6), that first
 *       segment is dropped as a host placeholder. Relative paths starting with a literal segment
 *       are never touched.
 *   <li><b>Split on {@code /}.</b> Empty segments are removed, which in particular ignores a
 *       trailing slash: {@code /users/} and {@code /users} are the same path.
 *   <li><b>Classify each segment</b> as {@link Literal} or {@link Variable}. A whole segment is a
 *       <b>variable</b> when it matches any of these dialects:
 *       <ul>
 *         <li>Handlebars / Postman: {@code {{id}}}
 *         <li>Insomnia templating: {@code {{ _.id }}}, {@code {{ response.body.id }}}
 *         <li>OpenAPI / JAX-RS: {@code {id}} and {@code {id:[0-9]+}} (optional regex part and
 *             optional inner whitespace)
 *         <li>Rails / Sinatra / Insomnia style: {@code :id}
 *       </ul>
 *       Anything else is a <b>literal</b>. The variable syntax is preserved only for display; two
 *       variables are interchangeable regardless of syntax or name.
 * </ol>
 *
 * <p>Literal text is compared case-sensitively ({@code /Users} and {@code /users} are different
 * paths), and variables are never matched by name: {@code /users/{id}} and {@code /users/:userId}
 * normalize to the same structure {@code [Literal("users"), Variable]}.
 */
public final class PathNormalizer {

  private static final Pattern HANDLEBARS = Pattern.compile("^\\{\\{\\s*(.+?)\\s*}}$");
  private static final Pattern BRACES =
      Pattern.compile("^\\{\\s*([^{}\\s]+)\\s*(?::\\s*([^{}]*)\\s*)?}$");
  private static final Pattern COLON = Pattern.compile("^:[A-Za-z_][A-Za-z0-9_-]*$");

  private PathNormalizer() {}

  /**
   * Normalizes a raw path or URL.
   *
   * @param raw raw path or URL as declared by any source; must not be null
   * @return the normalized segments, possibly empty; never null
   * @throws IllegalArgumentException if raw is null
   */
  public static List<Segment> normalize(String raw) {
    if (raw == null) {
      throw new IllegalArgumentException("Path must not be null");
    }
    String path = stripSchemeAndHost(stripQueryAndFragment(raw.strip()));
    if (path.isEmpty()) {
      return List.of();
    }
    List<String> rawSegments = new ArrayList<>(List.of(path.split("/")));
    boolean hostLikeVariable =
        !path.startsWith("/") && rawSegments.size() > 1 && isVariable(rawSegments.getFirst());
    return Stream.ofNullable(
            hostLikeVariable ? rawSegments.subList(1, rawSegments.size()) : rawSegments)
        .flatMap(List::stream)
        .filter(segment -> !segment.isEmpty())
        .map(PathNormalizer::classify)
        .toList();
  }

  private static String stripQueryAndFragment(String path) {
    int cut = path.length();
    int query = path.indexOf('?');
    if (query >= 0) {
      cut = query;
    }
    int fragment = path.indexOf('#');
    if (fragment >= 0 && fragment < cut) {
      cut = fragment;
    }
    return path.substring(0, cut);
  }

  private static String stripSchemeAndHost(String path) {
    int scheme = path.indexOf("://");
    if (scheme < 0) {
      return path;
    }
    int pathStart = path.indexOf('/', scheme + 3);
    return pathStart < 0 ? "" : path.substring(pathStart);
  }

  private static Segment classify(String rawSegment) {
    if (isVariable(rawSegment)) {
      // the syntax is kept for display only; inner whitespace is stripped so that
      // "{ id }" and "{id}" are indistinguishable
      return new Variable(rawSegment.replaceAll("\\s+", ""));
    }
    return new Literal(rawSegment);
  }

  /**
   * Applies the variable-dialect rules of step 6. Exposed so the UI can tell the user whether a
   * typed segment will behave as a variable.
   *
   * @param rawSegment segment text without separators; must not be null
   * @return true if the whole segment is a variable in any supported syntax
   */
  public static boolean isVariable(String rawSegment) {
    return HANDLEBARS.matcher(rawSegment).matches()
        || BRACES.matcher(rawSegment).matches()
        || COLON.matcher(rawSegment).matches();
  }
}
