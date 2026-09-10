/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */
package com.luisppb16.collectionsync.domain.service;

import static com.luisppb16.collectionsync.domain.service.TestFixtures.endpoint;
import static com.luisppb16.collectionsync.domain.service.TestFixtures.endpointWithParams;
import static com.luisppb16.collectionsync.domain.service.TestFixtures.request;
import static com.luisppb16.collectionsync.domain.service.TestFixtures.requestWithParams;
import static com.luisppb16.collectionsync.domain.service.TestFixtures.userShape;
import static org.assertj.core.api.Assertions.assertThat;

import com.luisppb16.collectionsync.domain.model.CoverageLevel;
import com.luisppb16.collectionsync.domain.model.CoveredEndpoint;
import com.luisppb16.collectionsync.domain.model.EndpointDescriptor;
import com.luisppb16.collectionsync.domain.model.HttpMethod;
import com.luisppb16.collectionsync.domain.model.OrphanCandidate;
import com.luisppb16.collectionsync.domain.model.ParamDescriptor;
import com.luisppb16.collectionsync.domain.model.ParamLocation;
import com.luisppb16.collectionsync.domain.model.RequestDescriptor;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MatchEngineTest {

  @Test
  @DisplayName("Given an endpoint {id} and a Postman request {{id}}, when hard matched, then they match")
  void matchesPostmanVariableAgainstEndpointVariable() {
    EndpointDescriptor endpoint = endpoint("UserController", "/users/{id}", HttpMethod.GET);
    RequestDescriptor request = request("Get user", HttpMethod.GET, "{{baseUrl}}/users/{{id}}");

    Optional<CoveredEndpoint> match = MatchEngine.findHardMatch(endpoint, List.of(request));

    assertThat(match).isPresent();
    assertThat(match.get().level()).isEqualTo(CoverageLevel.FULL);
  }

  @Test
  @DisplayName("Given an endpoint {id:\\d+} and a request {{id}}, when hard matched, then they match")
  void matchesRegexVariable() {
    EndpointDescriptor endpoint = endpoint("UserController", "/users/{id:\\d+}", HttpMethod.GET);
    RequestDescriptor request = request("Get user", HttpMethod.GET, "{{baseUrl}}/users/{{id}}");

    Optional<CoveredEndpoint> match = MatchEngine.findHardMatch(endpoint, List.of(request));

    assertThat(match).isPresent();
  }

  @Test
  @DisplayName("Given a different method, when hard matched, then they do not match")
  void doesNotMatchDifferentMethod() {
    EndpointDescriptor endpoint = endpoint("UserController", "/users/{id}", HttpMethod.DELETE);
    RequestDescriptor request = request("Get user", HttpMethod.GET, "{{baseUrl}}/users/{{id}}");

    Optional<CoveredEndpoint> match = MatchEngine.findHardMatch(endpoint, List.of(request));

    assertThat(match).isEmpty();
  }

  @Test
  @DisplayName("Given an endpoint that accepts all methods, when hard matched, then any method matches")
  void matchesEndpointAcceptingAllMethods() {
    EndpointDescriptor endpoint = endpoint("PingController", "/ping");
    RequestDescriptor request = request("Ping", HttpMethod.POST, "{{baseUrl}}/ping");

    Optional<CoveredEndpoint> match = MatchEngine.findHardMatch(endpoint, List.of(request));

    assertThat(match).isPresent();
  }

  @Test
  @DisplayName("Given a request with all required params and body, when covered, then the level is FULL")
  void fullCoverageWhenEverythingPresent() {
    ParamDescriptor idParam = new ParamDescriptor("id", ParamLocation.PATH, "Long", true);
    ParamDescriptor includeParam = new ParamDescriptor("include", ParamLocation.QUERY, "String", true);
    ParamDescriptor authHeader = new ParamDescriptor("X-Auth", ParamLocation.HEADER, "String", true);
    EndpointDescriptor endpoint = endpointWithParams("UserController", "/users/{id}", List.of(idParam, includeParam, authHeader), userShape(), HttpMethod.POST);
    RequestDescriptor request = requestWithParams(
        "Create user",
        HttpMethod.POST,
        "{{baseUrl}}/users/{{id}}",
        List.of(new ParamDescriptor("include", ParamLocation.QUERY, "String", false)),
        List.of(new ParamDescriptor("X-Auth", ParamLocation.HEADER, "String", false)),
        "{\"name\": \"Ada\", \"email\": \"ada@example.com\"}");

    Optional<CoveredEndpoint> match = MatchEngine.findHardMatch(endpoint, List.of(request));

    assertThat(match).isPresent();
    assertThat(match.get().level()).isEqualTo(CoverageLevel.FULL);
  }

  @Test
  @DisplayName("Given a request with body but missing a required query param, when covered, then the level is PARTIAL")
  void partialCoverageWhenRequiredQueryParamMissing() {
    ParamDescriptor includeParam = new ParamDescriptor("include", ParamLocation.QUERY, "String", true);
    EndpointDescriptor endpoint = endpointWithParams("UserController", "/users", List.of(includeParam), userShape(), HttpMethod.POST);
    RequestDescriptor request = requestWithParams(
        "Create user", HttpMethod.POST, "{{baseUrl}}/users", List.of(), List.of(),
        "{\"name\": \"Ada\"}");

    Optional<CoveredEndpoint> match = MatchEngine.findHardMatch(endpoint, List.of(request));

    assertThat(match).isPresent();
    assertThat(match.get().level()).isEqualTo(CoverageLevel.PARTIAL);
  }

  @Test
  @DisplayName("Given a bare request without params or body, when covered, then the level is BARE")
  void bareCoverageWhenNothingElsePresent() {
    ParamDescriptor includeParam = new ParamDescriptor("include", ParamLocation.QUERY, "String", true);
    EndpointDescriptor endpoint = endpointWithParams("UserController", "/users", List.of(includeParam), userShape(), HttpMethod.POST);
    RequestDescriptor request = request("Create user", HttpMethod.POST, "{{baseUrl}}/users");

    Optional<CoveredEndpoint> match = MatchEngine.findHardMatch(endpoint, List.of(request));

    assertThat(match).isPresent();
    assertThat(match.get().level()).isEqualTo(CoverageLevel.BARE);
  }

  @Test
  @DisplayName("Given an endpoint with no requirements, when covered, then the level is FULL")
  void fullCoverageWhenNothingRequired() {
    EndpointDescriptor endpoint = endpoint("PingController", "/ping", HttpMethod.GET);
    RequestDescriptor request = request("Ping", HttpMethod.GET, "{{baseUrl}}/ping");

    Optional<CoveredEndpoint> match = MatchEngine.findHardMatch(endpoint, List.of(request));

    assertThat(match.get().level()).isEqualTo(CoverageLevel.FULL);
  }

  @Test
  @DisplayName("Given an orphan request with an extra segment, when scored, then a close endpoint is suggested")
  void suggestsCandidatesForOrphans() {
    EndpointDescriptor endpoint = endpoint("UserController", "/users/{id}", HttpMethod.GET);
    RequestDescriptor request = request("Get user detail", HttpMethod.GET, "{{baseUrl}}/users/{{id}}/detail");

    List<OrphanCandidate> candidates = MatchEngine.suggestCandidates(List.of(endpoint), request);

    assertThat(candidates).isNotEmpty();
    assertThat(candidates.get(0).endpoint()).isEqualTo(endpoint);
  }

  @Test
  @DisplayName("Given a completely unrelated request, when scored, then no candidates are suggested")
  void noCandidatesForUnrelatedRequests() {
    EndpointDescriptor endpoint = endpoint("UserController", "/users/{id}", HttpMethod.GET);
    RequestDescriptor request = request("Ping", HttpMethod.GET, "{{baseUrl}}/health/ping");

    List<OrphanCandidate> candidates = MatchEngine.suggestCandidates(List.of(endpoint), request);

    assertThat(candidates).isEmpty();
  }

  @Test
  @DisplayName("Given a template one segment shorter, when scored, then similarity applies a penalty")
  void similarityPenalizesSegmentCountMismatch() {
    EndpointDescriptor endpoint = endpoint("UserController", "/users/{id}", HttpMethod.GET);
    RequestDescriptor matching = request("Get user", HttpMethod.GET, "{{baseUrl}}/users/{{id}}");
    RequestDescriptor shorter = request("List users", HttpMethod.GET, "{{baseUrl}}/users");

    double matchingScore = MatchEngine.similarityScore(endpoint, matching);
    double shorterScore = MatchEngine.similarityScore(endpoint, shorter);

    assertThat(shorterScore).isGreaterThan(0.0).isLessThan(matchingScore);
  }

  @Test
  @DisplayName("Given the best of two requests, when hard matched, then the one with higher coverage wins")
  void bestCoverageWinsWhenMultipleRequestsMatch() {
    ParamDescriptor includeParam = new ParamDescriptor("include", ParamLocation.QUERY, "String", true);
    EndpointDescriptor endpoint = endpointWithParams("UserController", "/users", List.of(includeParam), userShape(), HttpMethod.POST);
    RequestDescriptor bare = request("Create user", HttpMethod.POST, "{{baseUrl}}/users");
    RequestDescriptor full = requestWithParams(
        "Create user (full)", HttpMethod.POST, "{{baseUrl}}/users",
        List.of(new ParamDescriptor("include", ParamLocation.QUERY, "String", false)), List.of(),
        "{\"name\": \"Ada\"}");

    Optional<CoveredEndpoint> match = MatchEngine.findHardMatch(endpoint, List.of(bare, full));

    assertThat(match).isPresent();
    assertThat(match.get().request().name()).isEqualTo("Create user (full)");
  }
}