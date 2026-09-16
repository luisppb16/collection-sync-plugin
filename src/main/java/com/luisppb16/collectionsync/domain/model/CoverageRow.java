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
 * One row of the coverage table shown in the tool window.
 *
 * <p>Rows of status {@link CoverageStatus#ORPHAN} represent collection requests: {@code owner} is
 * the collection name and {@code psiMethod} is null. Endpoint rows carry the declaring class as
 * {@code owner} and, when discovered from source code, the handler {@link PsiMethod}.
 *
 * @param status    classification; must not be null
 * @param method    HTTP method; must not be null
 * @param path      path template of the endpoint or raw path of the request; never blank
 * @param owner     declaring class (endpoints) or collection name (orphan requests)
 * @param module    module name for endpoints discovered from source, empty otherwise
 * @param psiMethod handler method for navigation, or null
 */
public record CoverageRow(
        CoverageStatus status,
        HttpMethod method,
        String path,
        String owner,
        String module,
        @Nullable PsiMethod psiMethod) {

    public CoverageRow {
        if (status == null || method == null) {
            throw new IllegalArgumentException("Coverage row status and method must not be null");
        }
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("Coverage row path must not be null or blank");
        }
        owner = owner == null ? "" : owner;
        module = module == null ? "" : module;
    }
}