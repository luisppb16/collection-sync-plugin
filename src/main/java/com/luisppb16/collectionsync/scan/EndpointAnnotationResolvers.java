/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */

package com.luisppb16.collectionsync.scan;

import java.util.Optional;

import org.jetbrains.annotations.NotNull;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiArrayInitializerMemberValue;
import com.intellij.psi.PsiLiteralExpression;

/**
 * Shared PSI helpers for {@link EndpointAnnotationResolver} implementations.
 */
final class EndpointAnnotationResolvers {

    private EndpointAnnotationResolvers() {
    }

    /**
     * Reads an annotation member as a string.
     *
     * <p>Supported shapes: a string literal ({@code @Path("users")}), a single-element string
     * array ({@code @RequestMapping("/users")}) and absent members (returns empty).
     *
     * @param annotation annotation to read; must not be null
     * @param attribute  member name, e.g. {@code value}; must not be null
     * @return the trimmed string value, or empty when the member is absent or not a string
     */
    static Optional<String> stringValue(@NotNull PsiAnnotation annotation, @NotNull String attribute) {
        PsiAnnotationMemberValue value = annotation.findAttributeValue(attribute);
        if (value == null) {
            return Optional.empty();
        }
        if (value instanceof PsiArrayInitializerMemberValue array) {
            PsiAnnotationMemberValue[] initializers = array.getInitializers();
            if (initializers.length == 1) {
                value = initializers[0];
            } else {
                return Optional.empty();
            }
        }
        if (value instanceof PsiLiteralExpression literal && literal.getValue() instanceof String text) {
            return Optional.of(text.strip());
        }
        return Optional.empty();
    }
}