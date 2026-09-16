/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */

package com.luisppb16.collectionsync.collection;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.luisppb16.collectionsync.domain.model.ApiRequest;
import com.luisppb16.collectionsync.domain.model.HttpMethod;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.jetbrains.annotations.NotNull;

/**
 * Parses an Insomnia v4/v5 export (YAML or JSON) into {@link ApiRequest}s.
 *
 * <p>The expected shape is either an object with a {@code resources} array (the format written by
 * the Insomnia exporter, which also carries {@code _type: export} metadata) or a bare array of
 * resources. Because {@code jackson-dataformat-yaml} also parses JSON, both YAML and JSON encodings
 * are handled by the same mapper.
 *
 * <p>Resources with {@code _type: request} become requests: the HTTP method comes from {@code
 * method}, the URL from {@code url} and the display name from {@code name}. The collection name is
 * taken from the {@code name} of the resource with {@code _type: workspace}, falling back to the
 * file name without extension when there is none. Every other resource type ({@code request_group},
 * {@code environment}, {@code api_spec}, {@code unit_test}, ...) is ignored, as are request
 * resources without a usable method or URL. An unsupported HTTP method fails fast with an {@link
 * IllegalArgumentException}, as does a root that is neither an object with {@code resources} nor an
 * array.
 */
public final class InsomniaCollectionParser implements CollectionParser {

  private static final ObjectMapper YAML_MAPPER = new YAMLMapper();

  private static List<JsonNode> resourcesOf(JsonNode root, File file) {
    if (root == null || root.isMissingNode()) {
      throw new IllegalArgumentException("Insomnia collection file is empty: " + file);
    }
    if (root.isArray()) {
      return children(root);
    }
    if (root.isObject() && root.hasNonNull("resources") && root.get("resources").isArray()) {
      return children(root.get("resources"));
    }
    throw new IllegalArgumentException(
        "Not an Insomnia v4/v5 export: expected a 'resources' array or a bare resource array: "
            + file);
  }

  private static Optional<String> workspaceName(List<JsonNode> resources) {
    return resources.stream()
        .filter(InsomniaCollectionParser::isWorkspace)
        .map(resource -> resource.path("name").asText("").strip())
        .filter(name -> !name.isEmpty())
        .findFirst();
  }

  private static boolean isWorkspace(JsonNode resource) {
    return "workspace".equals(resource.path("_type").asText());
  }

  private static boolean isRequest(JsonNode resource) {
    return "request".equals(resource.path("_type").asText());
  }

  private static ApiRequest toRequest(JsonNode resource, String collectionName) {
    String method = resource.path("method").asText("").strip();
    String rawUrl = resource.path("url").asText("").strip();
    if (method.isEmpty() || rawUrl.isEmpty()) {
      return null;
    }
    return new ApiRequest(
        HttpMethod.of(method), rawUrl, resource.path("name").asText(""), collectionName);
  }

  private static List<JsonNode> children(JsonNode arrayNode) {
    return StreamSupport.stream(arrayNode.spliterator(), false).toList();
  }

  @Override
  @NotNull
  public List<ApiRequest> parse(@NotNull File file) throws IOException {
    Objects.requireNonNull(file, "file must not be null");
    JsonNode root = YAML_MAPPER.readTree(file);
    List<JsonNode> resources = resourcesOf(root, file);
    String collectionName =
        workspaceName(resources).orElseGet(() -> CollectionParsers.fileBaseName(file.getName()));
    return resources.stream()
        .filter(InsomniaCollectionParser::isRequest)
        .flatMap(resource -> Stream.ofNullable(toRequest(resource, collectionName)))
        .toList();
  }
}
