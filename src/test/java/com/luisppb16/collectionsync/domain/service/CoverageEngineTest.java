/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */

package com.luisppb16.collectionsync.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.luisppb16.collectionsync.domain.model.ApiEndpoint;
import com.luisppb16.collectionsync.domain.model.ApiRequest;
import com.luisppb16.collectionsync.domain.model.CoverageResult;
import com.luisppb16.collectionsync.domain.model.CoverageStatus;
import com.luisppb16.collectionsync.domain.model.ExclusionRule;
import com.luisppb16.collectionsync.domain.model.HttpMethod;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("CoverageEngine")
class CoverageEngineTest {

  private List<ApiEndpoint> endpoints;
  private List<ApiRequest> requests;
  private CoverageResult result;

  @BeforeEach
  void setUp() {
    endpoints =
        List.of(
            new ApiEndpoint(HttpMethod.GET, "/users/{id}", "UserController", "app", null),
            new ApiEndpoint(HttpMethod.GET, "/users/me", "UserController", "app", null),
            new ApiEndpoint(HttpMethod.POST, "/users", "UserController", "app", null),
            new ApiEndpoint(HttpMethod.DELETE, "/orders/{id}", "OrderController", "orders", null));
    requests =
        List.of(
            new ApiRequest(
                HttpMethod.GET, "https://api.example.com/users/17", "Get user", "Postman"),
            new ApiRequest(
                HttpMethod.GET, "{{baseUrl}}/users/:userId", "Get user (var)", "Postman"),
            new ApiRequest(HttpMethod.GET, "/users/me", "Get current user", "Postman"),
            new ApiRequest(HttpMethod.POST, "{{baseUrl}}/users", "Create user", "Postman"),
            new ApiRequest(HttpMethod.PUT, "{{baseUrl}}/reports", "Stale report", "Postman"),
            new ApiRequest(HttpMethod.PUT, "{{baseUrl}}/reports", "Duplicate stale", "Insomnia"));
  }

  @Test
  @DisplayName(
      "Given endpoints and requests, when coverage is computed, then every endpoint and orphan is classified")
  void classifiesEndpointsAndOrphans() {
    result = compute(endpoints, requests, List.of());

    assertThat(result.rows()).hasSize(6);
    assertThat(result.coveredCount()).isEqualTo(3);
    assertThat(result.uncoveredCount()).isEqualTo(1);
    assertThat(result.orphanCount()).isEqualTo(2);
    assertThat(result.excludedCount()).isZero();
    assertThat(result.collectionCount()).isEqualTo(2);

    assertThat(containsRow("/orders/{id}", CoverageStatus.UNCOVERED)).isTrue();
    assertThat(containsRow("/users/{id}", CoverageStatus.COVERED)).isTrue();
    assertThat(containsRow("/users/me", CoverageStatus.COVERED)).isTrue();
    assertThat(containsRow("/users", CoverageStatus.COVERED)).isTrue();
    assertThat(containsRow("{{baseUrl}}/reports", CoverageStatus.ORPHAN)).isTrue();
  }

  @Test
  @DisplayName(
      "Given an exclusion rule, when coverage is computed, then the endpoint and its requests disappear")
  void excludesEndpointsAndTheirRequests() {
    result =
        compute(endpoints, requests, List.of(new ExclusionRule(HttpMethod.GET, "/users/{id}")));

    assertThat(result.excludedCount()).isEqualTo(1);
    assertThat(result.excludedRows())
        .singleElement()
        .satisfies(
            row -> {
              assertThat(row.status()).isEqualTo(CoverageStatus.EXCLUDED);
              assertThat(row.path()).isEqualTo("/users/{id}");
            });
    assertThat(result.coveredCount()).isEqualTo(2);
    assertThat(containsRow("/users/{id}", CoverageStatus.COVERED)).isFalse();
    // the requests that covered /users/{id} are hidden with it; only /reports stays orphan
    assertThat(result.orphanCount()).isEqualTo(2);
  }

  @Test
  @DisplayName(
      "Given an exclusion rule with variables, when coverage is computed, then the rule matches structurally")
  void exclusionRulesMatchStructurally() {
    result =
        compute(
            endpoints, requests, List.of(new ExclusionRule(HttpMethod.DELETE, "/orders/:orderId")));

    assertThat(result.excludedCount()).isEqualTo(1);
    assertThat(containsRow("/orders/{id}", CoverageStatus.UNCOVERED)).isFalse();
  }

  @Test
  @DisplayName(
      "Given an exclusion rule with variables, when coverage is computed, then sibling literal endpoints survive")
  void exclusionDoesNotSwallowSiblingLiterals() {
    result =
        compute(endpoints, requests, List.of(new ExclusionRule(HttpMethod.GET, "/users/{id}")));

    assertThat(result.excludedCount()).isEqualTo(1);
    assertThat(containsRow("/users/me", CoverageStatus.COVERED)).isTrue();
  }

  @Test
  @DisplayName("Given empty inputs, when coverage is computed, then the result is empty and valid")
  void computesEmptyResult() {
    result = compute(List.of(), List.of(), List.of());

    assertThat(result.rows()).isEmpty();
    assertThat(result.coveredCount()).isZero();
    assertThat(result.collectionCount()).isZero();
  }

  @Test
  @DisplayName(
      "Given the endpoint /carro and the request {{baseUrl}}/api/test/v1/carro with base path /api/test/v1, "
          + "when coverage is computed, then the endpoint is covered and no request is orphaned")
  void coversWhenPrefixIsOnlyInTheCollection() {
    List<ApiEndpoint> bareEndpoints =
        List.of(new ApiEndpoint(HttpMethod.GET, "/carro", "CarroController", "app", null));
    List<ApiRequest> prefixedRequests =
        List.of(
            new ApiRequest(
                HttpMethod.GET, "{{baseUrl}}/api/test/v1/carro", "Get carro", "Postman"));

    result = CoverageEngine.compute(bareEndpoints, prefixedRequests, List.of(), "/api/test/v1");

    assertThat(result.coveredCount()).isEqualTo(1);
    assertThat(containsRow("/carro", CoverageStatus.COVERED)).isTrue();
    assertThat(result.orphanCount()).isZero();
  }

  @Test
  @DisplayName(
      "Given the endpoint /api/test/v1/carro and the request {{baseUrl}}/carro with base path /api/test/v1, "
          + "when coverage is computed, then the endpoint is covered and no request is orphaned")
  void coversWhenPrefixIsOnlyInTheEndpoint() {
    List<ApiEndpoint> prefixedEndpoints =
        List.of(
            new ApiEndpoint(HttpMethod.GET, "/api/test/v1/carro", "CarroController", "app", null));
    List<ApiRequest> bareRequests =
        List.of(new ApiRequest(HttpMethod.GET, "{{baseUrl}}/carro", "Get carro", "Postman"));

    result = CoverageEngine.compute(prefixedEndpoints, bareRequests, List.of(), "/api/test/v1");

    assertThat(result.coveredCount()).isEqualTo(1);
    assertThat(containsRow("/api/test/v1/carro", CoverageStatus.COVERED)).isTrue();
    assertThat(result.orphanCount()).isZero();
  }

  @Test
  @DisplayName(
      "Given the base path on both sides, when coverage is computed, then the pair still matches")
  void coversWhenPrefixIsOnBothSides() {
    List<ApiEndpoint> prefixedEndpoints =
        List.of(
            new ApiEndpoint(HttpMethod.GET, "/api/test/v1/carro", "CarroController", "app", null));
    List<ApiRequest> prefixedRequests =
        List.of(
            new ApiRequest(
                HttpMethod.GET, "{{baseUrl}}/api/test/v1/carro", "Get carro", "Postman"));

    result = CoverageEngine.compute(prefixedEndpoints, prefixedRequests, List.of(), "/api/test/v1");

    assertThat(result.coveredCount()).isEqualTo(1);
    assertThat(result.orphanCount()).isZero();
  }

  @ParameterizedTest
  @CsvSource({
    // the pair that carries the prefix on both sides keeps matching with the empty base path
    "/api/test/v1/carro, {{baseUrl}}/api/test/v1/carro, true",
    // the user scenario stays unmatched without a configured base path
    "/carro, {{baseUrl}}/api/test/v1/carro, false"
  })
  @DisplayName(
      "Given an empty base path, when coverage is computed, then matching is the plain structural rule")
  void keepsCurrentBehaviourWithEmptyBasePath(
      String endpointPath, String requestUrl, boolean expectedCovered) {
    List<ApiEndpoint> singleEndpoint =
        List.of(new ApiEndpoint(HttpMethod.GET, endpointPath, "CarroController", "app", null));
    List<ApiRequest> singleRequest =
        List.of(new ApiRequest(HttpMethod.GET, requestUrl, "Get carro", "Postman"));

    result = CoverageEngine.compute(singleEndpoint, singleRequest, List.of(), "");

    assertThat(containsRow(endpointPath, CoverageStatus.COVERED)).isEqualTo(expectedCovered);
    assertThat(result.orphanCount()).isEqualTo(expectedCovered ? 0 : 1);
  }

  @Test
  @DisplayName(
      "Given a base path that no side declares, when coverage is computed, then the pair stays uncovered and orphaned")
  void leavesPathsUntouchedWhenBasePathDoesNotMatch() {
    List<ApiEndpoint> bareEndpoints =
        List.of(new ApiEndpoint(HttpMethod.GET, "/carro", "CarroController", "app", null));
    List<ApiRequest> prefixedRequests =
        List.of(
            new ApiRequest(
                HttpMethod.GET, "{{baseUrl}}/api/test/v1/carro", "Get carro", "Postman"));

    result = CoverageEngine.compute(bareEndpoints, prefixedRequests, List.of(), "/api/other");

    assertThat(result.coveredCount()).isZero();
    assertThat(result.uncoveredCount()).isEqualTo(1);
    assertThat(result.orphanCount()).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "Given a rule authored from the full displayed path, when coverage is computed under a base path, "
          + "then the endpoint is excluded, its request is hidden and the sibling survives")
  void keepsExclusionSemanticsUnderBasePathStripping() {
    List<ApiEndpoint> prefixedEndpoints =
        List.of(
            new ApiEndpoint(
                HttpMethod.GET, "/api/test/v1/users/{id}", "UserController", "app", null),
            new ApiEndpoint(
                HttpMethod.GET, "/api/test/v1/users/me", "UserController", "app", null));
    List<ApiRequest> prefixedRequests =
        List.of(
            new ApiRequest(
                HttpMethod.GET, "{{baseUrl}}/api/test/v1/users/17", "Get user", "Postman"),
            new ApiRequest(
                HttpMethod.GET, "{{baseUrl}}/api/test/v1/users/me", "Get me", "Postman"));

    result =
        CoverageEngine.compute(
            prefixedEndpoints,
            prefixedRequests,
            List.of(new ExclusionRule(HttpMethod.GET, "/api/test/v1/users/{id}")),
            "/api/test/v1");

    assertThat(result.excludedCount()).isEqualTo(1);
    assertThat(result.excludedRows())
        .singleElement()
        .satisfies(row -> assertThat(row.path()).isEqualTo("/api/test/v1/users/{id}"));
    assertThat(containsRow("/api/test/v1/users/me", CoverageStatus.COVERED)).isTrue();
    assertThat(result.orphanCount()).isZero();
  }

  @Test
  @DisplayName(
      "Given a rule authored without the base path and an endpoint declared with it, "
          + "when coverage is computed, then the endpoint is still excluded")
  void alignsRulesAuthoredOnEitherSideOfTheBasePath() {
    List<ApiEndpoint> prefixedEndpoints =
        List.of(
            new ApiEndpoint(
                HttpMethod.GET, "/api/test/v1/users/{id}", "UserController", "app", null));
    List<ApiRequest> prefixedRequests =
        List.of(
            new ApiRequest(
                HttpMethod.GET, "{{baseUrl}}/api/test/v1/users/17", "Get user", "Postman"));

    result =
        CoverageEngine.compute(
            prefixedEndpoints,
            prefixedRequests,
            List.of(new ExclusionRule(HttpMethod.GET, "/users/{id}")),
            "/api/test/v1");

    assertThat(result.excludedCount()).isEqualTo(1);
    assertThat(result.orphanCount()).isZero();
  }

  @Test
  @DisplayName("Given a null base path, when coverage is computed, then it fails fast")
  void failsFastOnNullBasePath() {
    assertThatThrownBy(() -> CoverageEngine.compute(endpoints, requests, List.of(), null))
        .isInstanceOf(NullPointerException.class);
  }

  private CoverageResult compute(
      List<ApiEndpoint> endpoints, List<ApiRequest> requests, List<ExclusionRule> exclusions) {
    return CoverageEngine.compute(endpoints, requests, exclusions, "");
  }

  private boolean containsRow(String path, CoverageStatus status) {
    return result.rows().stream()
        .anyMatch(row -> row.path().equals(path) && row.status() == status);
  }
}
