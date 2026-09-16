/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */

package com.luisppb16.collectionsync.i18n;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import com.intellij.AbstractBundle;

/**
 * Access point of every user-visible string of the plugin, backed by the standard resource bundle
 * {@code messages/EndpointCoverageBundle.properties} (English, no suffix) plus its translations
 * (e.g. {@code _es.properties}), resolved against the IDE locale.
 *
 * <p>Action texts and descriptions are not read through this class: the platform resolves them
 * from the same bundle via {@code <actions resource-bundle="messages.EndpointCoverageBundle">} in
 * {@code withJava.xml}, using the {@code action.<actionId>.text} key convention. (Overriding
 * {@code getTemplateText()} is not possible: that method is {@code final} on the 2026.2 platform.)
 * The {@code actionText}/{@code actionDescription} accessors exist to resolve those same keys from
 * code, mainly for tests.
 *
 * <p>Strings are NOT externalized for: developer-facing exceptions (fail-fast messages), the
 * exported Markdown/CSV report (kept in English as a stable, tool-consumable format) and the
 * {@code plugin.xml} description/change-notes (marketplace, kept in English).
 */
public final class EndpointCoverageBundle {

    @NonNls
    private static final String BUNDLE_NAME = "messages.EndpointCoverageBundle";

    private static final AbstractBundle INSTANCE = new AbstractBundle(BUNDLE_NAME) { };

    private EndpointCoverageBundle() {
    }

    /**
     * Resolves a bundle key against the IDE locale, formatting {@code {n}} placeholders when
     * arguments are given.
     *
     * @param key    bundle key; must not be null
     * @param params optional MessageFormat arguments; must not be null
     * @return the localized message; never null
     */
    public static @Nls String message(@NotNull @NonNls String key, @NotNull Object... params) {
        return INSTANCE.getMessage(key, params);
    }

    /**
     * Resolves the text of the action with the given id, using the same key the platform uses for
     * plugin.xml actions: {@code action.<id>.text}.
     *
     * @param actionId action id inside the {@code collectionsync.} package, e.g.
     *                 {@code collectionsync.AnalyzeEndpointsCoverage}; must not be null
     * @return the localized action text; never null
     */
    public static @Nls String actionText(@NotNull @NonNls String actionId) {
        return message("action." + actionId + ".text");
    }

    /**
     * Resolves the description of the action with the given id, using the same key the platform
     * uses for plugin.xml actions: {@code action.<actionId>.description}.
     *
     * @param actionId action id inside the {@code collectionsync.} package, e.g.
     *                 {@code collectionsync.UseAsCollection}; must not be null
     * @return the localized action description; never null
     */
    public static @Nls String actionDescription(@NotNull @NonNls String actionId) {
        return message("action." + actionId + ".description");
    }
}