/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */

package com.luisppb16.collectionsync.domain.service;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.jetbrains.annotations.NotNull;

import com.luisppb16.collectionsync.domain.model.ApiEndpoint;
import com.luisppb16.collectionsync.domain.model.ApiRequest;
import com.luisppb16.collectionsync.domain.model.CoverageResult;
import com.luisppb16.collectionsync.domain.model.CoverageRow;
import com.luisppb16.collectionsync.domain.model.CoverageStatus;
import com.luisppb16.collectionsync.domain.model.ExclusionRule;
import com.luisppb16.collectionsync.domain.model.HttpMethod;
import com.luisppb16.collectionsync.domain.model.Segment;

/**
 * Computes the coverage report: for every endpoint, CUBIERTO (covered) or NO_CUBIERTO
 * (uncovered); for every collection request with no matching endpoint, HUÉRFANA (orphan).
 *
 * <h2>Algorithm</h2>
 *
 * <ol>
 *   <li>Every endpoint path, request URL and exclusion pattern is normalized once through
 *   {@link PathNormalizer}.</li>
 *   <li><b>Exclusions first.</b> An endpoint is excluded when some {@link ExclusionRule} has the
 *   same HTTP method and strictly matches its path ({@link PathMatcher#matchesStrict}: variables
 *   only match variables, so excluding {@code /users/{id}} never swallows the sibling endpoint
 *   {@code /users/me}). Excluded endpoints are dropped from the report and counted in
 *   {@link CoverageResult#excludedCount()}.</li>
 *   <li><b>Endpoint classification.</b> A non-excluded endpoint is COVERED when at least one
 *   request has the same HTTP method and a structurally matching path
 *   ({@link PathMatcher#matches}); otherwise it is UNCOVERED. Endpoints are <b>not consumable</b>:
 *   several requests may cover the same endpoint, which is normal in collections (different
 *   payloads for the same route).</li>
 *   <li><b>Orphans.</b> A request is an ORPHAN when it is not excluded (strict match against any
 *   exclusion rule), does not cover an excluded endpoint (loose rule: requests carry concrete
 *   values or placeholders, not the endpoint's variable syntax), and does not match any
 *   non-excluded endpoint.</li>
 * </ol>
 *
 * <p>Excluding an endpoint therefore also hides the requests that would otherwise have covered
 * it, so the report never shows orphans caused only by a user exclusion.
 */
public final class CoverageEngine {

    private static final Comparator<CoverageRow> DISPLAY_ORDER =
            Comparator.comparingInt((CoverageRow row) -> displayRank(row.status()))
                    .thenComparing(CoverageRow::path)
                    .thenComparing(CoverageRow::method)
                    .thenComparing(CoverageRow::owner);

    private record Rule(HttpMethod method, List<Segment> segments) {
    }

    private CoverageEngine() {
    }

    /**
     * Computes the coverage of the given endpoints against the given requests.
     *
     * @param endpoints  real endpoints of the project; must not be null
     * @param requests   collection requests; must not be null
     * @param exclusions user exclusion rules; must not be null
     * @return the report with display-sorted rows and aggregate counts; never null
     */
    public static CoverageResult compute(List<ApiEndpoint> endpoints,
                                         List<ApiRequest> requests,
                                         List<ExclusionRule> exclusions) {
        List<Rule> rules = exclusions.stream()
                .map(rule -> new Rule(rule.method(), rule.segments()))
                .toList();
        List<Rule> excludedEndpoints = endpoints.stream()
                .filter(endpoint -> isExcluded(endpoint, rules))
                .map(endpoint -> new Rule(endpoint.method(), PathNormalizer.normalize(endpoint.pathTemplate())))
                .toList();
        List<ApiEndpoint> excludedEndpointList = endpoints.stream()
                .filter(endpoint -> isExcluded(endpoint, rules))
                .toList();
        List<CoverageRow> excludedRows = excludedEndpointList.stream()
                .map(endpoint -> new CoverageRow(CoverageStatus.EXCLUDED, endpoint.method(), endpoint.pathTemplate(),
                        endpoint.ownerClass(), endpoint.moduleName(), endpoint.psiMethod()))
                .toList();
        List<ApiEndpoint> includedEndpoints = endpoints.stream()
                .filter(endpoint -> !isExcluded(endpoint, rules))
                .toList();

        List<CoverageRow> endpointRows = includedEndpoints.stream()
                .map(endpoint -> toRow(endpoint, requests))
                .toList();
        List<CoverageRow> orphanRows = requests.stream()
                .filter(request -> isOrphan(request, rules, excludedEndpoints, includedEndpoints))
                .map(request -> new CoverageRow(CoverageStatus.ORPHAN, request.method(), request.rawUrl(),
                        request.collectionName(), "", null))
                .toList();

        int covered = (int) endpointRows.stream()
                .filter(row -> row.status() == CoverageStatus.COVERED)
                .count();
        List<CoverageRow> rows = Stream.concat(endpointRows.stream(), orphanRows.stream())
                .sorted(DISPLAY_ORDER)
                .toList();
        List<CoverageRow> sortedExcludedRows = excludedRows.stream()
                .sorted(DISPLAY_ORDER)
                .toList();
        return new CoverageResult(rows, sortedExcludedRows, covered, endpointRows.size() - covered,
                orphanRows.size(), excludedEndpointList.size(), countCollections(requests));
    }

    private static CoverageRow toRow(ApiEndpoint endpoint, List<ApiRequest> requests) {
        List<Segment> segments = PathNormalizer.normalize(endpoint.pathTemplate());
        boolean isCovered = requests.stream().anyMatch(request -> covers(request, endpoint.method(), segments));
        CoverageStatus status = isCovered ? CoverageStatus.COVERED : CoverageStatus.UNCOVERED;
        return new CoverageRow(status, endpoint.method(), endpoint.pathTemplate(),
                endpoint.ownerClass(), endpoint.moduleName(), endpoint.psiMethod());
    }

    private static boolean isExcluded(ApiEndpoint endpoint, List<Rule> rules) {
        Rule shape = new Rule(endpoint.method(), PathNormalizer.normalize(endpoint.pathTemplate()));
        return rules.stream().anyMatch(rule -> matchesStrictly(rule, shape));
    }

    private static boolean isOrphan(ApiRequest request,
                                    List<Rule> rules,
                                    List<Rule> excludedEndpoints,
                                    List<ApiEndpoint> includedEndpoints) {
        List<Segment> segments = PathNormalizer.normalize(request.rawUrl());
        boolean excludedByRule = rules.stream()
                .anyMatch(rule -> request.method() == rule.method()
                        && PathMatcher.matchesStrict(rule.segments(), segments));
        if (excludedByRule) {
            return false;
        }
        boolean coversExcludedEndpoint = excludedEndpoints.stream()
                .anyMatch(rule -> request.method() == rule.method()
                        && PathMatcher.matches(rule.segments(), segments));
        if (coversExcludedEndpoint) {
            return false;
        }
        return includedEndpoints.stream()
                .noneMatch(endpoint -> covers(request, endpoint.method(),
                        PathNormalizer.normalize(endpoint.pathTemplate())));
    }

    private static boolean covers(ApiRequest request, HttpMethod method, List<Segment> endpointSegments) {
        return request.method() == method
                && PathMatcher.matches(endpointSegments, PathNormalizer.normalize(request.rawUrl()));
    }

    private static boolean matchesStrictly(Rule first, Rule second) {
        return first.method() == second.method() && PathMatcher.matchesStrict(first.segments(), second.segments());
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
        return (int) requests.stream()
                .map(ApiRequest::collectionName)
                .distinct()
                .count();
    }
}