/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */

package com.luisppb16.collectionsync.domain.model;

import org.jetbrains.annotations.Nullable;

import com.intellij.psi.PsiMethod;

/**
 * A real REST endpoint exposed by the project.
 *
 * <p>Endpoints come from an {@code EndpointSource}: either an OpenAPI 3.x file or a PSI scan of
 * controller classes. When the endpoint was discovered from source code, {@code psiMethod} points
 * to the handler method so the UI can navigate to it.
 *
 * @param method       HTTP method; must not be null
 * @param pathTemplate path as declared, e.g. {@code /users/{id}}; must not be blank
 * @param ownerClass   simple name of the declaring class (controller or OpenAPI tag), or empty
 * @param moduleName   name of the module the endpoint was found in (multi-module support), or empty
 * @param psiMethod    handler method for navigation, or null when the source is an OpenAPI file
 */
public record ApiEndpoint(
        HttpMethod method,
        String pathTemplate,
        String ownerClass,
        String moduleName,
        @Nullable PsiMethod psiMethod) {

    public ApiEndpoint {
        if (method == null) {
            throw new IllegalArgumentException("Endpoint method must not be null");
        }
        if (pathTemplate == null || pathTemplate.isBlank()) {
            throw new IllegalArgumentException("Endpoint path must not be null or blank");
        }
        ownerClass = ownerClass == null ? "" : ownerClass;
        moduleName = moduleName == null ? "" : moduleName;
    }
}