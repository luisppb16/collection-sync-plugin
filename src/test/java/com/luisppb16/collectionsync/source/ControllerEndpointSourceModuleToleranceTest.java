/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */

package com.luisppb16.collectionsync.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.IndexNotReadyException;

import com.luisppb16.collectionsync.domain.model.ApiEndpoint;
import com.luisppb16.collectionsync.domain.model.HttpMethod;

/**
 * Unit test of the per-module degradation of {@link ControllerEndpointSource} (layer 3 of the
 * dumb-mode defence). Pure JUnit: the modules are mocked, so no PSI fixture is needed and the
 * helper can be exercised with stub scan functions.
 */
@DisplayName("ControllerEndpointSource per-module degradation")
class ControllerEndpointSourceModuleToleranceTest {

    @Test
    @DisplayName("Given one module whose scan fails and one healthy module, when the modules are scanned tolerantly, then only the failing module is skipped")
    void skipsOnlyTheFailingModule() {
        Module healthyModule = mockModule("healthy-module");
        Module failingModule = mockModule("failing-module");
        Function<Module, List<ApiEndpoint>> scan = module -> {
            if (failingModule == module) {
                throw IndexNotReadyException.create();
            }
            return List.of(new ApiEndpoint(HttpMethod.GET, "/ping", "com.example.Ping",
                    healthyModule.getName(), null));
        };
        List<String> skippedModules = new ArrayList<>();

        List<ApiEndpoint> endpoints = ControllerEndpointSource.collectSkippingFailedModules(
                List.of(healthyModule, failingModule), scan,
                (module, failure) -> skippedModules.add(module.getName()));

        assertThat(endpoints).hasSize(1);
        assertThat(endpoints.getFirst().moduleName()).isEqualTo("healthy-module");
        assertThat(skippedModules).containsExactly("failing-module");
    }

    @Test
    @DisplayName("Given a scan that is cancelled, when the modules are scanned tolerantly, then the cancellation propagates instead of being swallowed")
    void rethrowsProcessCanceledException() {
        Module module = mockModule("cancelled-module");
        Function<Module, List<ApiEndpoint>> scan = ignored -> {
            throw new ProcessCanceledException();
        };

        assertThatThrownBy(() -> ControllerEndpointSource.collectSkippingFailedModules(
                List.of(module), scan, (failedModule, failure) -> { }))
                .isInstanceOf(ProcessCanceledException.class);
    }

    @Test
    @DisplayName("Given every module scanning cleanly, when the modules are scanned tolerantly, then their endpoints are accumulated in order")
    void accumulatesEndpointsOfEveryModule() {
        Module firstModule = mockModule("first-module");
        Module secondModule = mockModule("second-module");
        Function<Module, List<ApiEndpoint>> scan = module -> List.of(
                new ApiEndpoint(HttpMethod.GET, "/" + module.getName(), "Controller", module.getName(), null));

        List<ApiEndpoint> endpoints = ControllerEndpointSource.collectSkippingFailedModules(
                List.of(firstModule, secondModule), scan, (failedModule, failure) -> { });

        assertThat(endpoints).extracting(ApiEndpoint::moduleName)
                .containsExactly("first-module", "second-module");
    }

    private static Module mockModule(String name) {
        Module module = Mockito.mock(Module.class);
        Mockito.when(module.getName()).thenReturn(name);
        return module;
    }
}