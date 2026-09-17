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
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Computes the coverage report: for every endpoint, CUBIERTO (covered) or NO_CUBIERTO (uncovered);
 * for every collection request with no matching endpoint, HUÉRFANA (orphan).
 *
 * <h2>Algorithm</h2>
 *
 * <ol>
 *   <li>Every endpoint path, request URL and exclusion pattern is normalized once through {@link
 *       PathNormalizer}.
 *   <li><b>Exclusions first.</b> An endpoint is excluded when some {@link ExclusionRule} has the
 *       same HTTP method and strictly matches its path ({@link PathMatcher#matchesStrict}:
 *       variables only match variables, so excluding {@code /users/{id}} never swallows the sibling
 *       endpoint {@code /users/me}). Excluded endpoints are dropped from the report and counted in
 *       {@link CoverageResult#excludedCount()}.
 *   <li><b>Endpoint classification.</b> A non-excluded endpoint is COVERED when at least one
 *       request has the same HTTP method and a structurally matching path ({@link
 *       PathMatcher#matches}); otherwise it is UNCOVERED. Endpoints are <b>not consumable</b>:
 *       several requests may cover the same endpoint, which is normal in collections (different
 *       payloads for the same route).
 *   <li><b>Orphans.</b> A request is an ORPHAN when it is not excluded (strict match against any
 *       exclusion rule), does not cover an excluded endpoint (loose rule: requests carry concrete
 *       values or placeholders, not the endpoint's variable syntax), and does not match any
 *       non-excluded endpoint.
 * </ol>
 *
 * <p>Excluding an endpoint therefore also hides the requests that would otherwise have covered it,
 * so the report never shows orphans caused only by a user exclusion.
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
   * @return the report with display-sorted rows and aggregate counts; never null
   */
  public static CoverageResult compute(
      List<ApiEndpoint> endpoints, List<ApiRequest> requests, List<ExclusionRule> exclusions) {
    List<Rule> rules =
        exclusions.stream().map(rule -> new Rule(rule.method(), rule.segments())).toList();
    List<Shape> shapes =
        endpoints.stream()
            .map(
                endpoint ->
                    new Shape(
                        endpoint,
                        new Rule(
                            endpoint.method(), PathNormalizer.normalize(endpoint.pathTemplate()))))
            .toList();
    List<Shape> excludedShapes =
        shapes.stream().filter(shape -> isExcludedByRules(shape, rules)).toList();
    List<Shape> includedShapes =
        shapes.stream().filter(shape -> !isExcludedByRules(shape, rules)).toList();
    List<RequestShape> requestShapes =
        requests.stream()
            .map(request -> new RequestShape(request, PathNormalizer.normalize(request.rawUrl())))
            .toList();

    List<CoverageRow> excludedRows =
        excludedShapes.stream()
            .map(shape -> rowOf(shape.endpoint(), CoverageStatus.EXCLUDED))
            .toList();
    List<CoverageRow> endpointRows =
        includedShapes.stream().map(shape -> toRow(shape, requestShapes)).toList();
    List<CoverageRow> orphanRows =
        requestShapes.stream()
            .filter(requestShape -> isOrphan(requestShape, rules, excludedShapes, includedShapes))
            .map(requestShape -> orphanRowOf(requestShape.request()))
            .toList();

    int covered =
        (int) endpointRows.stream().filter(row -> row.status() == CoverageStatus.COVERED).count();
    List<CoverageRow> rows =
        Stream.concat(endpointRows.stream(), orphanRows.stream()).sorted(DISPLAY_ORDER).toList();
    List<CoverageRow> sortedExcludedRows = excludedRows.stream().sorted(DISPLAY_ORDER).toList();
    return new CoverageResult(
        rows,
        sortedExcludedRows,
        covered,
        endpointRows.size() - covered,
        orphanRows.size(),
        excludedShapes.size(),
        countCollections(requests));
  }

  private static CoverageRow toRow(Shape shape, List<RequestShape> requestShapes) {
    boolean isCovered =
        requestShapes.stream().anyMatch(requestShape -> covers(shape, requestShape));
    return rowOf(shape.endpoint(), isCovered ? CoverageStatus.COVERED : CoverageStatus.UNCOVERED);
  }

  private static boolean isExcludedByRules(Shape shape, List<Rule> rules) {
    return rules.stream().anyMatch(rule -> matchesStrictly(rule, shape.shape()));
  }

  private static boolean isOrphan(
      RequestShape requestShape,
      List<Rule> rules,
      List<Shape> excludedShapes,
      List<Shape> includedShapes) {
    boolean excludedByRule =
        rules.stream()
            .anyMatch(
                rule ->
                    requestShape.request().method() == rule.method()
                        && PathMatcher.matchesStrict(rule.segments(), requestShape.segments()));
    if (excludedByRule) {
      return false;
    }
    if (excludedShapes.stream().anyMatch(shape -> covers(shape, requestShape))) {
      return false;
    }
    return includedShapes.stream().noneMatch(shape -> covers(shape, requestShape));
  }

  private static boolean covers(Shape shape, RequestShape requestShape) {
    return requestShape.request().method() == shape.shape().method()
        && PathMatcher.matches(shape.shape().segments(), requestShape.segments());
  }

  private static boolean matchesStrictly(Rule first, Rule second) {
    return first.method() == second.method()
        && PathMatcher.matchesStrict(first.segments(), second.segments());
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
        "",
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
