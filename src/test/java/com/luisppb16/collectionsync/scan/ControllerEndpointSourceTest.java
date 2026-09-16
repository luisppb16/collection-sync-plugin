/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */

package com.luisppb16.collectionsync.scan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;

import com.luisppb16.collectionsync.domain.model.ApiEndpoint;
import com.luisppb16.collectionsync.domain.model.HttpMethod;
import com.luisppb16.collectionsync.source.ControllerEndpointSource;
import com.luisppb16.collectionsync.test.PsiTestCase;

@DisplayName("ControllerEndpointSource")
class ControllerEndpointSourceTest extends PsiTestCase {

    private static final String SPRING_PACKAGE = "package org.springframework.web.bind.annotation;";

    private static final String JAX_RS_PACKAGE = "package jakarta.ws.rs;";

    private static final List<String> SPRING_STUBS = List.of(
            SPRING_PACKAGE
                    + " public enum RequestMethod { GET, HEAD, POST, PUT, PATCH, DELETE, OPTIONS, TRACE }",
            SPRING_PACKAGE
                    + " public @interface RequestMapping { String[] value() default {}; String[] path() default {};"
                    + " RequestMethod[] method() default {}; }",
            SPRING_PACKAGE
                    + " public @interface GetMapping { String[] value() default {}; String[] path() default {}; }",
            SPRING_PACKAGE
                    + " public @interface PostMapping { String[] value() default {}; String[] path() default {}; }",
            SPRING_PACKAGE
                    + " public @interface PutMapping { String[] value() default {}; String[] path() default {}; }",
            SPRING_PACKAGE
                    + " public @interface DeleteMapping { String[] value() default {}; String[] path() default {}; }",
            SPRING_PACKAGE
                    + " public @interface PatchMapping { String[] value() default {}; String[] path() default {}; }");

    private static final List<String> JAX_RS_STUBS = List.of(
            JAX_RS_PACKAGE + " public @interface Path { String value(); }",
            JAX_RS_PACKAGE + " public @interface GET { }",
            JAX_RS_PACKAGE + " public @interface POST { }",
            JAX_RS_PACKAGE + " public @interface PUT { }",
            JAX_RS_PACKAGE + " public @interface DELETE { }",
            JAX_RS_PACKAGE + " public @interface PATCH { }",
            JAX_RS_PACKAGE + " public @interface HEAD { }",
            JAX_RS_PACKAGE + " public @interface OPTIONS { }");

    private static final String SPRING_CONTROLLER = """
            package com.example;

            @org.springframework.web.bind.annotation.RestController
            @org.springframework.web.bind.annotation.RequestMapping("/api")
            public class UserController {

                @org.springframework.web.bind.annotation.GetMapping("/users/{id}")
                public java.lang.String getUser(long id) {
                    return null;
                }

                @org.springframework.web.bind.annotation.PostMapping("/users")
                public java.lang.String createUser() {
                    return null;
                }

                @org.springframework.web.bind.annotation.RequestMapping(value = "/users/{id}",
                        method = org.springframework.web.bind.annotation.RequestMethod.PUT)
                public java.lang.String updateUser(long id) {
                    return null;
                }

                private void validateRequest() {
                }
            }
            """;

    private static final String SPRING_CONTROLLER_WITH_SLASH_EDGE_CASES = """
            package com.example;

            @org.springframework.web.bind.annotation.RestController
            @org.springframework.web.bind.annotation.RequestMapping("/api/")
            public class ItemController {

                @org.springframework.web.bind.annotation.GetMapping("/items")
                public java.lang.String listItems() {
                    return null;
                }

                @org.springframework.web.bind.annotation.PostMapping
                public java.lang.String createItem() {
                    return null;
                }
            }
            """;

    private static final String JAX_RS_CONTROLLER = """
            package com.example;

            @jakarta.ws.rs.Path("/orders")
            public class OrderResource {

                @jakarta.ws.rs.GET
                public java.lang.String listOrders() {
                    return null;
                }

                @jakarta.ws.rs.POST
                @jakarta.ws.rs.Path("/{orderId}")
                public java.lang.String createOrder() {
                    return null;
                }
            }
            """;

    private static final String NON_CONTROLLER = """
            package com.example;

            public class PlainService {

                public void doWork() {
                }
            }
            """;

    private Module module;

    @BeforeEach
    void addFixtureClasses() {
        List.of(SPRING_STUBS, JAX_RS_STUBS).forEach(stubs -> stubs.forEach(this::addClass));
        addClass(SPRING_CONTROLLER);
        addClass(SPRING_CONTROLLER_WITH_SLASH_EDGE_CASES);
        addClass(JAX_RS_CONTROLLER);
        addClass(NON_CONTROLLER);
        module = getFixture().getModule();
    }

    @Test
    @DisplayName("Given Spring and JAX-RS controllers, when endpoints are collected, then every mapped route is extracted with its owner and handler")
    void collectsEndpointsFromSpringAndJaxRsControllers() {
        ControllerEndpointSource source =
                new ControllerEndpointSource(List.of(new SpringAnnotationResolver(), new JaxRsAnnotationResolver()));

        List<ApiEndpoint> endpoints = sorted(source.collect(getFixture().getProject()));

        assertThat(endpoints).extracting(ApiEndpoint::method, ApiEndpoint::pathTemplate).containsExactly(
                tuple(HttpMethod.POST, "/api"),
                tuple(HttpMethod.GET, "/api/items"),
                tuple(HttpMethod.POST, "/api/users"),
                tuple(HttpMethod.GET, "/api/users/{id}"),
                tuple(HttpMethod.PUT, "/api/users/{id}"),
                tuple(HttpMethod.GET, "/orders"),
                tuple(HttpMethod.POST, "/orders/{orderId}"));
        assertThat(endpoints).allSatisfy(endpoint -> {
            assertThat(endpoint.moduleName()).isEqualTo(module.getName());
            assertThat(endpoint.psiMethod()).isNotNull();
        });
    }

    @Test
    @DisplayName("Given a collected endpoint, when its owner and handler are inspected, then they point to the declaring class and method")
    void resolvesOwnerClassAndPsiMethod() {
        ControllerEndpointSource source =
                new ControllerEndpointSource(List.of(new SpringAnnotationResolver(), new JaxRsAnnotationResolver()));

        List<ApiEndpoint> endpoints = sorted(source.collect(getFixture().getProject()));

        ApiEndpoint getUserEndpoint = endpoints.stream()
                .filter(endpoint -> "/api/users/{id}".equals(endpoint.pathTemplate())
                        && HttpMethod.GET.equals(endpoint.method()))
                .findFirst().orElseThrow();
        assertThat(getUserEndpoint.ownerClass()).isEqualTo("com.example.UserController");
        assertThat(psiMethodName(getUserEndpoint.psiMethod())).isEqualTo("getUser");

        ApiEndpoint createOrderEndpoint = endpoints.stream()
                .filter(endpoint -> "/orders/{orderId}".equals(endpoint.pathTemplate()))
                .findFirst().orElseThrow();
        assertThat(createOrderEndpoint.ownerClass()).isEqualTo("com.example.OrderResource");
        assertThat(psiMethodName(createOrderEndpoint.psiMethod())).isEqualTo("createOrder");
    }

    @Test
    @DisplayName("Given only the Spring resolver, when endpoints are collected, then JAX-RS resources produce nothing")
    void collectsOnlyEndpointsOfConfiguredFrameworks() {
        ControllerEndpointSource springOnlySource =
                new ControllerEndpointSource(List.of(new SpringAnnotationResolver()));

        List<ApiEndpoint> endpoints = sorted(springOnlySource.collect(getFixture().getProject()));

        assertThat(endpoints).extracting(ApiEndpoint::pathTemplate)
                .doesNotContain("/orders", "/orders/{orderId}");
        assertThat(endpoints).hasSize(5);
    }

    @Test
    @DisplayName("Given a resolver that fails because the index is not ready, when endpoints are collected, then the scan degrades to an empty result instead of crashing")
    void degradesToEmptyResultWhenIndexIsNotReady() {
        ControllerEndpointSource source =
                new ControllerEndpointSource(List.of(new FailingAnnotationResolver()));

        List<ApiEndpoint> endpoints = source.collect(getFixture().getProject());

        assertThat(endpoints).isEmpty();
    }

    private static final class FailingAnnotationResolver implements EndpointAnnotationResolver {

        @Override
        public boolean isController(PsiClass psiClass) {
            throw IndexNotReadyException.create();
        }

        @Override
        public String basePath(PsiClass psiClass) {
            return "";
        }

        @Override
        public List<HttpMethod> methods(PsiMethod psiMethod) {
            return List.of();
        }

        @Override
        public String methodPath(PsiMethod psiMethod) {
            return "";
        }
    }

    private void addClass(String source) {
        getFixture().addClass(source);
    }

    private String psiMethodName(PsiMethod psiMethod) {
        // PSI access from a background thread requires a read action.
        return ApplicationManager.getApplication().runReadAction((Computable<String>) psiMethod::getName);
    }

    private List<ApiEndpoint> sorted(List<ApiEndpoint> endpoints) {
        Comparator<ApiEndpoint> byPathThenMethod = Comparator.comparing(ApiEndpoint::pathTemplate)
                .thenComparing(endpoint -> endpoint.method().name());
        return endpoints.stream().sorted(byPathThenMethod).toList();
    }
}