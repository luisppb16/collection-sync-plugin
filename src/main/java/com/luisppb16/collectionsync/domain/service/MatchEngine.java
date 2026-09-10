/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */
package com.luisppb16.collectionsync.domain.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.luisppb16.collectionsync.domain.model.CoverageLevel;
import com.luisppb16.collectionsync.domain.model.CoveredEndpoint;
import com.luisppb16.collectionsync.domain.model.EndpointDescriptor;
import com.luisppb16.collectionsync.domain.model.HttpMethod;
import com.luisppb16.collectionsync.domain.model.OrphanCandidate;
import com.luisppb16.collectionsync.domain.model.ParamDescriptor;
import com.luisppb16.collectionsync.domain.model.ParamLocation;
import com.luisppb16.collectionsync.domain.model.RequestDescriptor;
import com.luisppb16.collectionsync.domain.service.PathTemplateNormalizer.Literal;
import com.luisppb16.collectionsync.domain.service.PathTemplateNormalizer.PathTemplate;
import com.luisppb16.collectionsync.domain.service.PathTemplateNormalizer.Segment;
import com.luisppb16.collectionsync.domain.service.PathTemplateNormalizer.Variable;
import com.luisppb16.collectionsync.domain.service.PathTemplateNormalizer.Wildcard;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Matches collection requests against discovered endpoints.
 *
 * <p>Hard matching (method + identical template) determines whether an endpoint is covered and at
 * which {@link CoverageLevel}. Flexible scoring (0..1, threshold {@value #MATCH_THRESHOLD}) is used
 * to suggest candidate endpoints for orphan requests.
 */
public final class MatchEngine {

  /** Similarity score above which a flexible match is considered a real match. */
  public static final double MATCH_THRESHOLD = 0.85;

  private static final double WEIGHT_TEMPLATE = 0.6;
  private static final double WEIGHT_METHOD = 0.15;
  private static final double WEIGHT_QUERY = 0.15;
  private static final double WEIGHT_HEADERS = 0.05;
  private static final double WEIGHT_BODY = 0.05;
  private static final double SEGMENT_COUNT_PENALTY = 0.9;

  private MatchEngine() {}

  /**
   * Finds the request that covers the endpoint with the best coverage level.
   *
   * @param endpoint the endpoint to cover
   * @param requests candidate requests from the collection
   * @return the best covering request, or empty
   */
  public static Optional<CoveredEndpoint> findHardMatch(EndpointDescriptor endpoint, List<RequestDescriptor> requests) {
    Objects.requireNonNull(endpoint, "endpoint");
    Objects.requireNonNull(requests, "requests");
    PathTemplate endpointTemplate = PathTemplateNormalizer.normalizeEndpointTemplate(endpoint.basePath(), endpoint.pathTemplate());
    Optional<RequestDescriptor> best = requests.stream()
        .filter(request -> methodCompatible(request.method(), endpoint.httpMethods()))
        .filter(request -> templatesMatch(endpointTemplate, PathTemplateNormalizer.normalizeRequestUrl(request.rawUrl())))
        .max(Comparator.comparingInt((RequestDescriptor request) -> coverageLevel(endpoint, request).ordinal()).reversed());
    return best.map(request -> new CoveredEndpoint(endpoint, request, coverageLevel(endpoint, request)));
  }

  /** @return true when the request method is accepted by the endpoint. */
  public static boolean methodCompatible(HttpMethod requestMethod, List<HttpMethod> endpointMethods) {
    return endpointMethods.isEmpty() || endpointMethods.contains(requestMethod);
  }

  /**
   * Compares two templates segment by segment: literals must be equal, variables match any
   * variable, wildcards match anything.
   */
  public static boolean templatesMatch(PathTemplate first, PathTemplate second) {
    Objects.requireNonNull(first, "first");
    Objects.requireNonNull(second, "second");
    if (first.segments().size() != second.segments().size()) {
      return false;
    }
    for (int index = 0; index < first.segments().size(); index++) {
      if (!segmentsMatch(first.segments().get(index), second.segments().get(index))) {
        return false;
      }
    }
    return true;
  }

  /** @return the quality of the coverage the request gives to the endpoint. */
  public static CoverageLevel coverageLevel(EndpointDescriptor endpoint, RequestDescriptor request) {
    Objects.requireNonNull(endpoint, "endpoint");
    Objects.requireNonNull(request, "request");
    boolean bodySatisfied = requestBodySatisfied(endpoint, request);
    boolean querySatisfied = requiredParamsSatisfied(endpoint, request, ParamLocation.QUERY);
    boolean headersSatisfied = requiredParamsSatisfied(endpoint, request, ParamLocation.HEADER);
    if (bodySatisfied && querySatisfied && headersSatisfied) {
      return CoverageLevel.FULL;
    }
    boolean partial = request.body() != null && !isEmptyBody(request.body())
        || !request.query().isEmpty()
        || !request.headers().isEmpty();
    return partial ? CoverageLevel.PARTIAL : CoverageLevel.BARE;
  }

  /**
   * Computes the similarity score (0..1) between an endpoint and a request, used to suggest
   * candidates for orphan requests.
   */
  public static double similarityScore(EndpointDescriptor endpoint, RequestDescriptor request) {
    Objects.requireNonNull(endpoint, "endpoint");
    Objects.requireNonNull(request, "request");
    PathTemplate endpointTemplate = PathTemplateNormalizer.normalizeEndpointTemplate(endpoint.basePath(), endpoint.pathTemplate());
    PathTemplate requestTemplate = PathTemplateNormalizer.normalizeRequestUrl(request.rawUrl());
    double templateScore = templateSimilarity(endpointTemplate, requestTemplate);
    if (templateScore == 0.0) {
      return 0.0;
    }
    double methodScore = methodCompatible(request.method(), endpoint.httpMethods()) ? 1.0 : 0.0;
    double queryScore = nameOverlapScore(queryNames(endpoint.params()), queryNames(request.query()));
    double headerScore = nameOverlapScore(queryNames(endpoint.params()), headerNames(request.headers()));
    double bodyScore = endpoint.requestBody() != null && requestBodySatisfied(endpoint, request) ? 1.0 : 0.0;
    return WEIGHT_TEMPLATE * templateScore
        + WEIGHT_METHOD * methodScore
        + WEIGHT_QUERY * queryScore
        + WEIGHT_HEADERS * headerScore
        + WEIGHT_BODY * bodyScore;
  }

  /**
   * Suggests the best matching endpoints for a request that no longer maps to any of them.
   *
   * @param endpoints all endpoints of the project
   * @param request the orphan request
   * @return up to three candidates ordered by descending score
   */
  public static List<OrphanCandidate> suggestCandidates(List<EndpointDescriptor> endpoints, RequestDescriptor request) {
    Objects.requireNonNull(endpoints, "endpoints");
    Objects.requireNonNull(request, "request");
    return endpoints.stream()
        .map(endpoint -> new OrphanCandidate(endpoint, similarityScore(endpoint, request)))
        .filter(candidate -> candidate.score() > 0.0)
        .sorted(Comparator.comparingDouble(OrphanCandidate::score).reversed())
        .limit(3)
        .toList();
  }

  private static double templateSimilarity(PathTemplate endpointTemplate, PathTemplate requestTemplate) {
    int endpointSize = endpointTemplate.segments().size();
    int requestSize = requestTemplate.segments().size();
    if (endpointSize == requestSize) {
      return matchedSegmentFraction(endpointTemplate, requestTemplate);
    }
    if (Math.abs(endpointSize - requestSize) == 1) {
      return bestAlignedScore(endpointTemplate, requestTemplate) * SEGMENT_COUNT_PENALTY;
    }
    return 0.0;
  }

  private static double bestAlignedScore(PathTemplate endpointTemplate, PathTemplate requestTemplate) {
    List<PathTemplate> endpointAlignments = alignments(endpointTemplate);
    List<PathTemplate> requestAlignments = alignments(requestTemplate);
    double best = 0.0;
    for (PathTemplate endpointAlignment : endpointAlignments) {
      if (endpointAlignment.segments().size() == requestTemplate.segments().size()) {
        best = Math.max(best, matchedSegmentFraction(endpointAlignment, requestTemplate));
      }
    }
    for (PathTemplate requestAlignment : requestAlignments) {
      if (endpointTemplate.segments().size() == requestAlignment.segments().size()) {
        best = Math.max(best, matchedSegmentFraction(endpointTemplate, requestAlignment));
      }
    }
    return best;
  }

  private static List<PathTemplate> alignments(PathTemplate template) {
    List<PathTemplate> alignments = new ArrayList<>();
    for (int skip = 0; skip < template.segments().size(); skip++) {
      List<Segment> segments = new ArrayList<>(template.segments());
      segments.remove(skip);
      alignments.add(new PathTemplate(segments));
    }
    return alignments;
  }

  private static double matchedSegmentFraction(PathTemplate endpointTemplate, PathTemplate requestTemplate) {
    if (endpointTemplate.segments().size() != requestTemplate.segments().size()) {
      return 0.0;
    }
    if (endpointTemplate.segments().isEmpty()) {
      return 1.0;
    }
    int matched = 0;
    for (int index = 0; index < endpointTemplate.segments().size(); index++) {
      if (segmentsMatch(endpointTemplate.segments().get(index), requestTemplate.segments().get(index))) {
        matched++;
      }
    }
    return matched / (double) endpointTemplate.segments().size();
  }

  private static boolean segmentsMatch(Segment first, Segment second) {
    if (first instanceof Wildcard || second instanceof Wildcard) {
      return true;
    }
    if (first instanceof Variable && second instanceof Variable) {
      return true;
    }
    if (first instanceof Literal firstLiteral && second instanceof Literal secondLiteral) {
      return firstLiteral.value().equals(secondLiteral.value());
    }
    return false;
  }

  private static boolean requestBodySatisfied(EndpointDescriptor endpoint, RequestDescriptor request) {
    if (endpoint.requestBody() == null) {
      return true;
    }
    JsonNode body = request.body();
    return body != null && !body.isNull() && !body.isEmpty();
  }

  private static boolean isEmptyBody(JsonNode body) {
    return body == null || body.isNull() || body.isEmpty();
  }

  private static boolean requiredParamsSatisfied(EndpointDescriptor endpoint, RequestDescriptor request, ParamLocation location) {
    Set<String> requiredNames = endpoint.params().stream()
        .filter(param -> param.location() == location && param.required())
        .map(ParamDescriptor::name)
        .collect(Collectors.toSet());
    if (requiredNames.isEmpty()) {
      return true;
    }
    List<ParamDescriptor> present = location == ParamLocation.QUERY ? request.query() : request.headers();
    Set<String> presentNames = present.stream().map(ParamDescriptor::name).collect(Collectors.toSet());
    return presentNames.containsAll(requiredNames);
  }

  private static Set<String> queryNames(List<ParamDescriptor> params) {
    return params.stream()
        .filter(param -> param.location() == ParamLocation.QUERY)
        .map(ParamDescriptor::name)
        .collect(Collectors.toSet());
  }

  private static Set<String> headerNames(List<ParamDescriptor> headers) {
    return headers.stream()
        .filter(param -> param.location() == ParamLocation.HEADER)
        .map(ParamDescriptor::name)
        .collect(Collectors.toSet());
  }

  private static double nameOverlapScore(Set<String> endpointNames, Set<String> requestNames) {
    if (endpointNames.isEmpty()) {
      return requestNames.isEmpty() ? 1.0 : 0.0;
    }
    if (requestNames.isEmpty()) {
      return 0.0;
    }
    long shared = endpointNames.stream().filter(requestNames::contains).count();
    int union = endpointNames.size() + requestNames.size() - (int) shared;
    return union == 0 ? 0.0 : shared / (double) union;
  }
}