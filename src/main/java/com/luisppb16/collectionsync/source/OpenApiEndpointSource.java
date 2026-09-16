/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */

package com.luisppb16.collectionsync.source;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;

import com.luisppb16.collectionsync.domain.model.ApiEndpoint;
import com.luisppb16.collectionsync.domain.model.HttpMethod;

/**
 * {@link EndpointSource} that reads the real endpoints from a local OpenAPI 3.x document
 * (JSON or YAML).
 *
 * <h2>The extraction algorithm, step by step</h2>
 *
 * <ol>
 *   <li><b>Path resolution.</b> The document path comes from the project settings. When it is
 *   {@code null}, blank, or no file exists at that path, the source yields no endpoints instead of
 *   failing: an empty (or unset) setting is a normal state of a not-yet-configured project.</li>
 *   <li><b>Format detection.</b> Files with a {@code .yaml} or {@code .yml} extension are parsed as
 *   YAML; any other file is first parsed as JSON and, when that fails, retried as YAML.</li>
 *   <li><b>Paths traversal.</b> The {@code paths} object is iterated; every key is a path template
 *   kept exactly as declared (e.g. {@code /users/{id}}) and its value is a <i>path item</i>.</li>
 *   <li><b>Operation extraction.</b> From each path item, only the lowercase HTTP operation members
 *   ({@code get}, {@code post}, {@code put}, {@code delete}, {@code patch}, {@code head},
 *   {@code options}) produce an {@link ApiEndpoint}: {@code parameters}, {@code servers},
 *   {@code summary}, {@code description}, {@code $ref} and any other non-operation key are ignored.</li>
 * </ol>
 *
 * <p>An unparseable document is treated like a missing one: the source yields no endpoints, so a
 * broken specification never blocks the coverage calculation.
 */
public final class OpenApiEndpointSource implements EndpointSource {

    private static final Logger LOG = Logger.getInstance(OpenApiEndpointSource.class);
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());
    private static final Set<String> OPERATION_KEYS =
            Set.of("get", "post", "put", "delete", "patch", "head", "options");
    private static final String OWNER_PREFIX = "<openapi>";

    private final String openApiFilePath;

    /**
     * @param openApiFilePath absolute path of the OpenAPI document, as configured in the project
     *                        settings (the settings dialog selects it with the platform file
     *                        chooser); a relative path would be resolved against the IDE working
     *                        directory, not the project root, and is not supported; may be null
     *                        or blank when the source is not configured yet
     */
    public OpenApiEndpointSource(String openApiFilePath) {
        this.openApiFilePath = openApiFilePath;
    }

    @Override
    public @NotNull List<ApiEndpoint> collect(@NotNull Project project) {
        Objects.requireNonNull(project);
        if (openApiFilePath == null || openApiFilePath.isBlank()) {
            return List.of();
        }
        File openApiFile = new File(openApiFilePath);
        if (!openApiFile.isFile()) {
            LOG.warn("OpenAPI document not found at the configured path: " + openApiFilePath);
            return List.of();
        }
        return readEndpoints(openApiFile);
    }

    private List<ApiEndpoint> readEndpoints(File openApiFile) {
        try {
            return endpointsOf(readTree(openApiFile), openApiFile.getName());
        } catch (IOException e) {
            LOG.warn("Failed to read the OpenAPI document '" + openApiFile.getName() + "': " + e.getMessage(), e);
            return List.of();
        }
    }

    private JsonNode readTree(File openApiFile) throws IOException {
        if (isYaml(openApiFile.getName())) {
            return YAML_MAPPER.readTree(openApiFile);
        }
        try {
            return JSON_MAPPER.readTree(openApiFile);
        } catch (IOException jsonFailure) {
            return YAML_MAPPER.readTree(openApiFile);
        }
    }

    private static boolean isYaml(String fileName) {
        String lowerCaseName = fileName.toLowerCase(Locale.ROOT);
        return lowerCaseName.endsWith(".yaml") || lowerCaseName.endsWith(".yml");
    }

    private static List<ApiEndpoint> endpointsOf(JsonNode root, String fileName) {
        String ownerClass = OWNER_PREFIX + " " + fileName;
        return root.path("paths").properties().stream()
                .flatMap(pathEntry -> endpointsOf(pathEntry.getKey(), pathEntry.getValue(), ownerClass).stream())
                .toList();
    }

    private static List<ApiEndpoint> endpointsOf(String pathTemplate, JsonNode pathItem, String ownerClass) {
        return pathItem.properties().stream()
                .filter(operationEntry -> OPERATION_KEYS.contains(operationEntry.getKey()))
                .map(operationEntry -> new ApiEndpoint(HttpMethod.of(operationEntry.getKey()), pathTemplate,
                        ownerClass, "", null))
                .toList();
    }
}