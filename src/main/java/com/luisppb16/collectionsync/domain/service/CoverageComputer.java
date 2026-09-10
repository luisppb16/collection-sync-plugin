/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */
package com.luisppb16.collectionsync.domain.service;

import com.luisppb16.collectionsync.domain.model.CoverageReport;
import com.luisppb16.collectionsync.domain.model.CoverageStats;
import com.luisppb16.collectionsync.domain.model.CoveredEndpoint;
import com.luisppb16.collectionsync.domain.model.EndpointDescriptor;
import com.luisppb16.collectionsync.domain.model.HttpMethod;
import com.luisppb16.collectionsync.domain.model.OrphanRequest;
import com.luisppb16.collectionsync.domain.model.RequestDescriptor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Computes the coverage of a collection against the endpoints discovered in the project: which
 * endpoints are covered (and how well), which are missing and which requests are orphans.
 */
public final class CoverageComputer {

  private CoverageComputer() {}

  /**
   * @param endpoints all endpoints discovered in the project
   * @param requests all requests of the active collection
   * @return the report with coverage, missing endpoints, orphans and stats
   */
  public static CoverageReport compute(List<EndpointDescriptor> endpoints, List<RequestDescriptor> requests) {
    Objects.requireNonNull(endpoints, "endpoints");
    Objects.requireNonNull(requests, "requests");
    Set<RequestDescriptor> usedRequests = new LinkedHashSet<>();
    List<CoveredEndpoint> covered = new ArrayList<>();
    List<EndpointDescriptor> missing = new ArrayList<>();

    for (EndpointDescriptor endpoint : endpoints) {
      Optional<CoveredEndpoint> match =
          MatchEngine.findHardMatch(endpoint, requests.stream().filter(request -> !usedRequests.contains(request)).toList());
      if (match.isPresent()) {
        usedRequests.add(match.get().request());
        covered.add(match.get());
      } else {
        missing.add(endpoint);
      }
    }

    List<RequestDescriptor> orphans = requests.stream().filter(request -> !usedRequests.contains(request)).toList();
    List<OrphanRequest> orphanRequests = new ArrayList<>();
    for (RequestDescriptor orphan : orphans) {
      orphanRequests.add(new OrphanRequest(orphan, MatchEngine.suggestCandidates(endpoints, orphan)));
    }

    return new CoverageReport(covered, missing, orphanRequests, buildStats(endpoints, covered, orphans, requests));
  }

  private static CoverageStats buildStats(
      List<EndpointDescriptor> endpoints, List<CoveredEndpoint> covered, List<RequestDescriptor> orphans, List<RequestDescriptor> requests) {
    int totalEndpoints = endpoints.size();
    int coveredEndpoints = covered.size();
    Map<HttpMethod, Long> coveredByMethod = countMethod(covered);
    Map<String, Long> coveredByController = countController(covered);
    return new CoverageStats(
        totalEndpoints,
        coveredEndpoints,
        totalEndpoints - coveredEndpoints,
        orphans.size(),
        countDuplicates(requests),
        totalEndpoints == 0 ? 0.0 : coveredEndpoints * 100.0 / totalEndpoints,
        coveredByMethod,
        coveredByController);
  }

  private static Map<HttpMethod, Long> countMethod(List<CoveredEndpoint> covered) {
    Map<HttpMethod, Long> byMethod = new HashMap<>();
    for (CoveredEndpoint coveredEndpoint : covered) {
      byMethod.merge(coveredEndpoint.request().method(), 1L, Long::sum);
    }
    return byMethod;
  }

  private static Map<String, Long> countController(List<CoveredEndpoint> covered) {
    Map<String, Long> byController = new HashMap<>();
    for (CoveredEndpoint coveredEndpoint : covered) {
      byController.merge(coveredEndpoint.endpoint().controllerClassName(), 1L, Long::sum);
    }
    return byController;
  }

  private static int countDuplicates(List<RequestDescriptor> requests) {
    Set<String> seen = new HashSet<>();
    Set<String> duplicates = new HashSet<>();
    for (RequestDescriptor request : requests) {
      String key = request.method() + " " + request.normalizedTemplate();
      if (!seen.add(key)) {
        duplicates.add(key);
      }
    }
    return duplicates.size();
  }
}