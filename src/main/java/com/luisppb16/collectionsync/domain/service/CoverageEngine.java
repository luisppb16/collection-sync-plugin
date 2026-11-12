/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */

package com.luisppb16.collectionsync.domain.service;

import com.luisppb16.collectionsync.domain.model.ApiEndpoint;
import com.luisppb16.collectionsync.domain.model.ApiRequest;
import com.luisppb16.collectionsync.domain.model.CoverageResult;
import com.luisppb16.collectionsync.domain.model.CoverageRow;
import com.luisppb16.collectionsync.domain.model.CoverageStatus;
import com.luisppb16.collectionsync.domain.model.ExclusionRule;
import com.luisppb16.collectionsync.domain.model.HttpMethod;
import com.luisppb16.collectionsync.domain.model.Segment;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Computes the coverage report: for every endpoint, COVERED or UNCOVERED; for every collection
 * request with no matching endpoint, ORPHAN.
 *
 * <h2>Algorithm</h2>
 *
 * <ol>
 *   <li>Every endpoint path, request URL and exclusion pattern is normalized once through {@link
 *       PathNormalizer}; when a base path is configured, its normalized segments are stripped from
 *       the root of all three ({@link PathMatcher#stripPrefix}).
 *   <li><b>Exclusions first.</b> An endpoint is excluded when some {@link ExclusionRule} has the
 *       same HTTP method and strictly matches its path ({@link PathMatcher#matchesStrict}).
 *   <li><b>Endpoint classification.</b> A non-excluded endpoint is COVERED when at least one
 *       request has a compatible HTTP method (an {@link HttpMethod#ANY} endpoint matches every
 *       method), a compatible module (an empty module on either side matches everything) and a
 *       structurally matching path ({@link PathMatcher#matches}).
 *   <li><b>Orphans.</b> A request is an ORPHAN when it is not excluded, does not cover an excluded
 *       endpoint and does not match any non-excluded endpoint.
 * </ol>
 */
public final class CoverageEngine {

  private static final Comparator<CoverageRow> DISPLAY_ORDER =
      Comparator.comparingInt((CoverageRow row) -> displayRank(row.status()))
          .thenComparing(CoverageRow::path)
          .thenComparing(CoverageRow::method)
          .thenComparing(CoverageRow::owner);

  private CoverageEngine() {}

  /**
   * Computes the coverage of the given endpoints against the given requests.
   *
   * @param endpoints real endpoints of the project; must not be null
   * @param requests collection requests; must not be null
   * @param exclusions user exclusion rules; must not be null
   * @param basePath user-defined URL root ignored on both sides before matching; must not be null,
   *     may be empty
   * @return the report with display-sorted rows and aggregate counts; never null
   */
  public static CoverageResult compute(
      List<ApiEndpoint> endpoints,
      List<ApiRequest> requests,
      List<ExclusionRule> exclusions,
      String basePath) {
    Objects.requireNonNull(basePath, "basePath must not be null");
    List<Segment> basePathSegments = PathNormalizer.normalize(basePath);
    List<Rule> rules =
        exclusions.stream()
            .map(rule ->
                new Rule(rule.method(), PathMatcher.stripPrefix(rule.segments(), basePathSegments)))
            .toList();
    List<Shape> shapes =
        endpoints.stream()
            .map(endpoint ->
                new Shape(
                    endpoint,
                    new Rule(
                        endpoint.method(),
                        normalizeAgainstBasePath(endpoint.pathTemplate(), basePathSegments))))
            .toList();
    Map<Boolean, List<Shape>> shapesByExcluded =
        shapes.stream()
            .collect(Collectors.partitioningBy(shape -> isExcludedByRules(shape, rules)));
    List<Shape> excludedShapes = shapesByExcluded.get(Boolean.TRUE);
    List<Shape> includedShapes = shapesByExcluded.get(Boolean.FALSE);
    List<RequestShape> requestShapes =
        requests.stream()
            .map(request ->
                new RequestShape(
                    request, normalizeAgainstBasePath(request.rawUrl(), basePathSegments)))
            .toList();

    Map<Integer, List<Shape>> includedBySize = bySize(includedShapes);
    Map<Integer, List<Shape>> excludedBySize = bySize(excludedShapes);

    Set<Shape> covered = Collections.newSetFromMap(new IdentityHashMap<>());
    List<CoverageRow> orphanRows = new ArrayList<>();
    for (RequestShape requestShape : requestShapes) {
      int size = requestShape.segments().size();
      boolean excludedByRule =
          rules.stream().anyMatch(rule -> requestMatchesRule(requestShape, rule));
      boolean coversExcluded =
          excludedBySize.getOrDefault(size, List.of()).stream()
              .anyMatch(shape -> covers(shape, requestShape));
      boolean coversIncluded = false;
      for (Shape shape : includedBySize.getOrDefault(size, List.of())) {
        if (covers(shape, requestShape)) {
          coversIncluded = true;
          covered.add(shape);
        }
      }
      if (!excludedByRule && !coversExcluded && !coversIncluded) {
        orphanRows.add(orphanRowOf(requestShape.request()));
      }
    }

    List<CoverageRow> endpointRows =
        includedShapes.stream()
            .map(shape ->
                rowOf(
                    shape.endpoint(),
                    covered.contains(shape) ? CoverageStatus.COVERED : CoverageStatus.UNCOVERED))
            .toList();
    int coveredCount =
        (int) endpointRows.stream().filter(row -> row.status() == CoverageStatus.COVERED).count();
    List<CoverageRow> rows =
        Stream.concat(endpointRows.stream(), orphanRows.stream()).sorted(DISPLAY_ORDER).toList();
    List<CoverageRow> excludedRows =
        excludedShapes.stream()
            .map(shape -> rowOf(shape.endpoint(), CoverageStatus.EXCLUDED))
            .sorted(DISPLAY_ORDER)
            .toList();
    return new CoverageResult(
        rows,
        excludedRows,
        coveredCount,
        endpointRows.size() - coveredCount,
        orphanRows.size(),
        excludedShapes.size(),
        countCollections(requests));
  }

  private static Map<Integer, List<Shape>> bySize(List<Shape> shapes) {
    return shapes.stream().collect(Collectors.groupingBy(shape -> shape.shape().segments().size()));
  }

  private static boolean isExcludedByRules(Shape shape, List<Rule> rules) {
    return rules.stream().anyMatch(rule -> matchesStrictly(rule, shape.shape()));
  }

  private static boolean requestMatchesRule(RequestShape requestShape, Rule rule) {
    return requestShape.request().method() == rule.method()
        && PathMatcher.matchesStrict(rule.segments(), requestShape.segments());
  }

  private static boolean covers(Shape shape, RequestShape requestShape) {
    return methodCovers(shape.shape().method(), requestShape.request().method())
        && moduleCovers(requestShape.request().moduleName(), shape.endpoint().moduleName())
        && PathMatcher.matches(shape.shape().segments(), requestShape.segments());
  }

  private static boolean methodCovers(HttpMethod endpointMethod, HttpMethod requestMethod) {
    return endpointMethod == HttpMethod.ANY || endpointMethod == requestMethod;
  }

  private static boolean moduleCovers(String requestModule, String endpointModule) {
    return requestModule.isEmpty()
        || endpointModule.isEmpty()
        || requestModule.equals(endpointModule);
  }

  private static boolean matchesStrictly(Rule first, Rule second) {
    return first.method() == second.method()
        && PathMatcher.matchesStrict(first.segments(), second.segments());
  }

  /** Normalizes a raw path and removes the configured base path from its root, when present. */
  private static List<Segment> normalizeAgainstBasePath(
      String raw, List<Segment> basePathSegments) {
    return PathMatcher.stripPrefix(PathNormalizer.normalize(raw), basePathSegments);
  }

  private static CoverageRow rowOf(ApiEndpoint endpoint, CoverageStatus status) {
    return new CoverageRow(
        status,
        endpoint.method(),
        endpoint.pathTemplate(),
        endpoint.ownerClass(),
        endpoint.moduleName(),
        endpoint.psiMethod());
  }

  private static CoverageRow orphanRowOf(ApiRequest request) {
    return new CoverageRow(
        CoverageStatus.ORPHAN,
        request.method(),
        request.rawUrl(),
        request.collectionName(),
        request.moduleName(),
        null);
  }

  private static int displayRank(CoverageStatus status) {
    return switch (status) {
      case UNCOVERED -> 0;
      case ORPHAN -> 1;
      case EXCLUDED -> 2;
      case COVERED -> 3;
    };
  }

  private static int countCollections(List<ApiRequest> requests) {
    return (int) requests.stream().map(ApiRequest::collectionName).distinct().count();
  }

  private record Rule(HttpMethod method, List<Segment> segments) {}

  /** Endpoint paired with its normalized (method, path) shape, computed once per endpoint. */
  private record Shape(ApiEndpoint endpoint, Rule shape) {}

  /** Request paired with its normalized path shape, computed once per request. */
  private record RequestShape(ApiRequest request, List<Segment> segments) {}
}
