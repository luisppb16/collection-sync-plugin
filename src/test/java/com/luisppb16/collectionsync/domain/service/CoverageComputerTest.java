/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */
package com.luisppb16.collectionsync.domain.service;

import static com.luisppb16.collectionsync.domain.service.TestFixtures.endpoint;
import static com.luisppb16.collectionsync.domain.service.TestFixtures.request;
import static org.assertj.core.api.Assertions.assertThat;

import com.luisppb16.collectionsync.domain.model.CoverageLevel;
import com.luisppb16.collectionsync.domain.model.CoverageReport;
import com.luisppb16.collectionsync.domain.model.EndpointDescriptor;
import com.luisppb16.collectionsync.domain.model.HttpMethod;
import com.luisppb16.collectionsync.domain.model.RequestDescriptor;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CoverageComputerTest {

  @Test
  @DisplayName("Given two endpoints and one matching request, when computed, then one is covered and one is missing")
  void separatesCoveredAndMissing() {
    EndpointDescriptor covered = endpoint("UserController", "/users", HttpMethod.GET);
    EndpointDescriptor missing = endpoint("UserController", "/users/{id}", HttpMethod.GET);
    RequestDescriptor request = request("List users", HttpMethod.GET, "{{baseUrl}}/users");

    CoverageReport report = CoverageComputer.compute(List.of(covered, missing), List.of(request));

    assertThat(report.covered()).hasSize(1);
    assertThat(report.covered().get(0).level()).isEqualTo(CoverageLevel.FULL);
    assertThat(report.missing()).containsExactly(missing);
    assertThat(report.stats().coveragePercent()).isEqualTo(50.0);
  }

  @Test
  @DisplayName("Given a request for a removed endpoint, when computed, then it is an orphan with no candidates")
  void reportsOrphanRequests() {
    EndpointDescriptor endpoint = endpoint("UserController", "/users", HttpMethod.GET);
    RequestDescriptor orphan = request("Delete legacy", HttpMethod.DELETE, "{{baseUrl}}/legacy");

    CoverageReport report = CoverageComputer.compute(List.of(endpoint), List.of(orphan));

    assertThat(report.orphans()).hasSize(1);
    assertThat(report.orphans().get(0).request()).isEqualTo(orphan);
    assertThat(report.stats().orphanRequests()).isEqualTo(1);
  }

  @Test
  @DisplayName("Given two identical requests, when computed, then duplicates are counted once")
  void countsDuplicateRequests() {
    RequestDescriptor first = request("List users", HttpMethod.GET, "{{baseUrl}}/users");
    RequestDescriptor second = request("List users (copy)", HttpMethod.GET, "{{baseUrl}}/users");

    CoverageReport report = CoverageComputer.compute(List.of(), List.of(first, second));

    assertThat(report.stats().duplicateRequests()).isEqualTo(1);
  }

  @Test
  @DisplayName("Given a single request matching the second endpoint, when computed, then only that endpoint is covered")
  void assignsEachRequestOnlyOnce() {
    EndpointDescriptor first = endpoint("UserController", "/users/{id}", HttpMethod.GET);
    EndpointDescriptor second = endpoint("UserController", "/users", HttpMethod.GET);
    RequestDescriptor request = request("List users", HttpMethod.GET, "{{baseUrl}}/users");

    CoverageReport report = CoverageComputer.compute(List.of(first, second), List.of(request));

    assertThat(report.covered()).hasSize(1);
    assertThat(report.covered().get(0).endpoint()).isEqualTo(second);
    assertThat(report.missing()).containsExactly(first);
  }

  @Test
  @DisplayName("Given no endpoints and no requests, when computed, then coverage is zero and the report is empty")
  void emptyReportWhenNothingScanned() {
    CoverageReport report = CoverageComputer.compute(List.of(), List.of());

    assertThat(report.covered()).isEmpty();
    assertThat(report.missing()).isEmpty();
    assertThat(report.orphans()).isEmpty();
    assertThat(report.stats().coveragePercent()).isEqualTo(0.0);
  }

  @Test
  @DisplayName("Given covered endpoints, when computed, then stats group by controller and method")
  void statsGroupByControllerAndMethod() {
    EndpointDescriptor userEndpoint = endpoint("UserController", "/users", HttpMethod.GET);
    EndpointDescriptor orderEndpoint = endpoint("OrderController", "/orders", HttpMethod.GET);
    RequestDescriptor userRequest = request("List users", HttpMethod.GET, "{{baseUrl}}/users");
    RequestDescriptor orderRequest = request("List orders", HttpMethod.GET, "{{baseUrl}}/orders");

    CoverageReport report = CoverageComputer.compute(List.of(userEndpoint, orderEndpoint), List.of(userRequest, orderRequest));

    assertThat(report.stats().coveredByController()).containsEntry("UserController", 1L).containsEntry("OrderController", 1L);
    assertThat(report.stats().coveredByMethod()).containsEntry(HttpMethod.GET, 2L);
  }
}