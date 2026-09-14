/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */
package com.luisppb16.collectionsync.infrastructure.psi;

import static org.assertj.core.api.Assertions.assertThat;

import com.intellij.psi.PsiClass;
import com.luisppb16.collectionsync.domain.model.EndpointDescriptor;
import com.luisppb16.collectionsync.domain.model.HttpMethod;
import com.luisppb16.collectionsync.domain.model.ParamLocation;
import com.luisppb16.collectionsync.infrastructure.psi.common.DtoShapeResolver;
import com.luisppb16.collectionsync.infrastructure.psi.spring.SpringEndpointScanner;
import java.util.List;

/**
 * Spring scanner over inline PSI fixtures. JUnit3 style is required by the platform test
 * framework; the Given-When-Then contract lives in the test method names and bodies.
 */
public class SpringEndpointScannerTest extends PsiTestCase {

  private SpringEndpointScanner scanner;
  private PsiTestHelper fixtures;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    fixtures = new PsiTestHelper(myFixture);
    fixtures.addClass("src/org/springframework/web/bind/annotation/RequestMethod.java", PsiFixtures.REQUEST_METHOD_ENUM);
    fixtures.addClass("src/com/example/fixtures/UserDto.java", PsiFixtures.USER_DTO);
    scanner = new SpringEndpointScanner(new DtoShapeResolver(getProject()));
  }

  public void testDiscoversGetMappingWithBasePathAndRegexVariable() {
    // Given a controller with a class-level @RequestMapping and a @GetMapping with a regex variable.
    PsiClass controller = fixtures.addClass("src/com/example/fixtures/UserController.java", PsiFixtures.USER_CONTROLLER);

    List<EndpointDescriptor> endpoints = scanner.scanClass(controller);

    // When scanned, then the joined template keeps the variable and its regex.
    assertThat(endpoints).hasSize(4);
    EndpointDescriptor getUser = endpoints.get(0);
    assertThat(getUser.pathTemplate()).isEqualTo("/api/v1/users/{id:\\d+}");
    assertThat(getUser.httpMethods()).containsExactly(HttpMethod.GET);
    assertThat(getUser.framework().name()).isEqualTo("SPRING");
  }

  public void testReadsPathAndQueryParameters() {
    // Given the getUser method with a required path variable and an optional query param.
    PsiClass controller = fixtures.addClass("src/com/example/fixtures/UserController.java", PsiFixtures.USER_CONTROLLER);

    List<EndpointDescriptor> endpoints = scanner.scanClass(controller);

    // When scanned, then params carry name, location and requiredness.
    EndpointDescriptor getUser = endpoints.get(0);
    assertThat(getUser.params()).hasSize(2);
    assertThat(getUser.params().get(0).name()).isEqualTo("id");
    assertThat(getUser.params().get(0).location()).isEqualTo(ParamLocation.PATH);
    assertThat(getUser.params().get(0).required()).isTrue();
    assertThat(getUser.params().get(1).name()).isEqualTo("expand");
    assertThat(getUser.params().get(1).location()).isEqualTo(ParamLocation.QUERY);
    assertThat(getUser.params().get(1).required()).isFalse();
  }

  public void testResolvesRequestBodyShapeAndConsumesProduces() {
    // Given the createUser method with @RequestBody of a record and consumes/produces values.
    PsiClass controller = fixtures.addClass("src/com/example/fixtures/UserController.java", PsiFixtures.USER_CONTROLLER);

    List<EndpointDescriptor> endpoints = scanner.scanClass(controller);

    // When scanned, then the body shape is the record and media types are read.
    EndpointDescriptor createUser = endpoints.get(1);
    assertThat(createUser.httpMethods()).containsExactly(HttpMethod.POST);
    assertThat(createUser.consumes()).isEqualTo("application/json");
    assertThat(createUser.produces()).isEqualTo("application/json");
    assertThat(createUser.requestBody()).isNotNull();
    assertThat(createUser.requestBody().kind().name()).isEqualTo("RECORD");
    assertThat(createUser.requestBody().properties()).hasSize(3);
  }

  public void testRequestMappingWithMethodAttributeMapsSingleMethod() {
    // Given the search method annotated with @RequestMapping(method = RequestMethod.GET).
    PsiClass controller = fixtures.addClass("src/com/example/fixtures/UserController.java", PsiFixtures.USER_CONTROLLER);

    List<EndpointDescriptor> endpoints = scanner.scanClass(controller);

    // When scanned, then the enum constant resolves to a single GET.
    EndpointDescriptor search = endpoints.get(2);
    assertThat(search.pathTemplate()).isEqualTo("/api/v1/users/search");
    assertThat(search.httpMethods()).containsExactly(HttpMethod.GET);
    assertThat(search.params()).hasSize(1);
    assertThat(search.params().get(0).location()).isEqualTo(ParamLocation.HEADER);
    assertThat(search.params().get(0).name()).isEqualTo("X-Auth");
  }

  public void testIgnoresFeignClientsAndPlainClasses() {
    // Given a Feign client and a class without mapping annotations.
    PsiClass feign = fixtures.addClass("src/com/example/fixtures/AdminClient.java", PsiFixtures.FEIGN_CLIENT);
    PsiClass plain = fixtures.addClass("src/com/example/fixtures/PlainController.java", PsiFixtures.PLAIN_CLASS);

    List<EndpointDescriptor> feignEndpoints = scanner.scanClass(feign);
    List<EndpointDescriptor> plainEndpoints = scanner.scanClass(plain);

    // When scanned, then both are skipped.
    assertThat(feignEndpoints).isEmpty();
    assertThat(plainEndpoints).isEmpty();
  }
}