/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */

package com.luisppb16.collectionsync.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.luisppb16.collectionsync.domain.model.ApiEndpoint;
import com.luisppb16.collectionsync.domain.model.ApiRequest;
import com.luisppb16.collectionsync.domain.model.CoverageResult;
import com.luisppb16.collectionsync.domain.model.CoverageStatus;
import com.luisppb16.collectionsync.domain.model.ExclusionRule;
import com.luisppb16.collectionsync.domain.model.HttpMethod;

@DisplayName("CoverageEngine")
class CoverageEngineTest {

    private List<ApiEndpoint> endpoints;
    private List<ApiRequest> requests;
    private CoverageResult result;

    @BeforeEach
    void setUp() {
        endpoints = List.of(
                new ApiEndpoint(HttpMethod.GET, "/users/{id}", "UserController", "app", null),
                new ApiEndpoint(HttpMethod.GET, "/users/me", "UserController", "app", null),
                new ApiEndpoint(HttpMethod.POST, "/users", "UserController", "app", null),
                new ApiEndpoint(HttpMethod.DELETE, "/orders/{id}", "OrderController", "orders", null));
        requests = List.of(
                new ApiRequest(HttpMethod.GET, "https://api.example.com/users/17", "Get user", "Postman"),
                new ApiRequest(HttpMethod.GET, "{{baseUrl}}/users/:userId", "Get user (var)", "Postman"),
                new ApiRequest(HttpMethod.GET, "/users/me", "Get current user", "Postman"),
                new ApiRequest(HttpMethod.POST, "{{baseUrl}}/users", "Create user", "Postman"),
                new ApiRequest(HttpMethod.PUT, "{{baseUrl}}/reports", "Stale report", "Postman"),
                new ApiRequest(HttpMethod.PUT, "{{baseUrl}}/reports", "Duplicate stale", "Insomnia"));
    }

    @Test
    @DisplayName("Given endpoints and requests, when coverage is computed, then every endpoint and orphan is classified")
    void classifiesEndpointsAndOrphans() {
        result = CoverageEngine.compute(endpoints, requests, List.of());

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
    @DisplayName("Given an exclusion rule, when coverage is computed, then the endpoint and its requests disappear")
    void excludesEndpointsAndTheirRequests() {
        result = CoverageEngine.compute(endpoints, requests, List.of(new ExclusionRule(HttpMethod.GET, "/users/{id}")));

        assertThat(result.excludedCount()).isEqualTo(1);
        assertThat(result.excludedRows())
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.status()).isEqualTo(CoverageStatus.EXCLUDED);
                    assertThat(row.path()).isEqualTo("/users/{id}");
                });
        assertThat(result.coveredCount()).isEqualTo(2);
        assertThat(containsRow("/users/{id}", CoverageStatus.COVERED)).isFalse();
        // the requests that covered /users/{id} are hidden with it; only /reports stays orphan
        assertThat(result.orphanCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("Given an exclusion rule with variables, when coverage is computed, then the rule matches structurally")
    void exclusionRulesMatchStructurally() {
        result = CoverageEngine.compute(endpoints, requests,
                List.of(new ExclusionRule(HttpMethod.DELETE, "/orders/:orderId")));

        assertThat(result.excludedCount()).isEqualTo(1);
        assertThat(containsRow("/orders/{id}", CoverageStatus.UNCOVERED)).isFalse();
    }

    @Test
    @DisplayName("Given an exclusion rule with variables, when coverage is computed, then sibling literal endpoints survive")
    void exclusionDoesNotSwallowSiblingLiterals() {
        result = CoverageEngine.compute(endpoints, requests, List.of(new ExclusionRule(HttpMethod.GET, "/users/{id}")));

        assertThat(result.excludedCount()).isEqualTo(1);
        assertThat(containsRow("/users/me", CoverageStatus.COVERED)).isTrue();
    }

    @Test
    @DisplayName("Given empty inputs, when coverage is computed, then the result is empty and valid")
    void computesEmptyResult() {
        result = CoverageEngine.compute(List.of(), List.of(), List.of());

        assertThat(result.rows()).isEmpty();
        assertThat(result.coveredCount()).isZero();
        assertThat(result.collectionCount()).isZero();
    }

    private boolean containsRow(String path, CoverageStatus status) {
        return result.rows().stream()
                .anyMatch(row -> row.path().equals(path) && row.status() == status);
    }
}