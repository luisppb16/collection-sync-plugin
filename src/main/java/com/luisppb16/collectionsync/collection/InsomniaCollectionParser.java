/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */

package com.luisppb16.collectionsync.collection;

import com.fasterxml.jackson.databind.JsonNode;
import com.luisppb16.collectionsync.domain.model.ApiRequest;
import com.luisppb16.collectionsync.domain.model.HttpMethod;
import com.luisppb16.collectionsync.io.JsonYaml;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.jetbrains.annotations.NotNull;

/**
 * Parses an Insomnia export (YAML or JSON) into {@link ApiRequest}s.
 *
 * <p>Two shapes are accepted:
 *
 * <ul>
 *   <li>a <b>v4/v5 flat export</b>: an object with a {@code resources} array (the format written by
 *       the Insomnia exporter, which also carries {@code _type: export} metadata) or a bare array
 *       of resources; or
 *   <li>a <b>v5 nested collection</b>: an object whose {@code type} starts with {@code
 *       collection.insomnia.rest/} (e.g. {@code collection.insomnia.rest/5.0}), carrying a nested
 *       {@code collection} tree in which folders (nodes with a {@code children} array) hold their
 *       requests and sub-folders at any depth; a node may carry both a usable request and a {@code
 *       children} array, in which case its own request and its children are both collected. A v5
 *       export whose {@code collection} is missing or is not an array fails fast, as do sibling v5
 *       namespaces such as {@code spec.insomnia.rest/} or {@code environment.insomnia.rest/}, which
 *       are not collections.
 * </ul>
 *
 * <p>Both YAML and JSON encodings are handled through {@link JsonYaml} (content-based, so the file
 * extension never matters, and aliases/merge keys are expanded before the tree is built).
 *
 * <p>Requests are the resources/nodes with a usable {@code method} and {@code url}: the HTTP method
 * comes from {@code method}, the URL from {@code url} and the display name from {@code name}. The
 * collection name is taken from the {@code name} of the resource with {@code _type: workspace} (v4)
 * or from the root object's {@code name} (v5), falling back to the file name without extension when
 * there is none. Every other resource type ({@code request_group}, {@code environment}, {@code
 * api_spec}, {@code unit_test}, ...) and the {@code environments} block of a v5 export are ignored,
 * as are requests without a usable method or URL. A {@code method}/{@code url} that is present but
 * not text (e.g. a YAML 1.1 value like {@code no} or {@code on}, which parses as a boolean) fails
 * fast with an {@link IllegalArgumentException}, as do an unsupported HTTP method and a root that
 * matches neither shape.
 */
public final class InsomniaCollectionParser implements CollectionParser {

  private static final String V5_COLLECTION_TYPE_PREFIX = "collection.insomnia.rest/";

  /**
   * Returns whether the given root is an Insomnia v5 collection (its {@code type} starts with
   * {@code collection.insomnia.rest/}).
   *
   * @param root parsed document root; may be null
   * @return whether the root identifies itself as a v5 collection
   */
  static boolean isV5Collection(JsonNode root) {
    return root != null
        && root.isObject()
        && root.path("type").asText().startsWith(V5_COLLECTION_TYPE_PREFIX);
  }

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
    JsonNode methodNode = resource.path("method");
    JsonNode urlNode = resource.path("url");
    if (methodNode.isMissingNode() || urlNode.isMissingNode()) {
      return null;
    }
    if (!methodNode.isTextual() || !urlNode.isTextual()) {
      throw new IllegalArgumentException(
          "Request 'method' and 'url' must be text values: " + resource.path("name").asText(""));
    }
    String method = methodNode.asText().strip();
    String rawUrl = urlNode.asText().strip();
    if (method.isEmpty() || rawUrl.isEmpty()) {
      return null;
    }
    return new ApiRequest(
        HttpMethod.of(method), rawUrl, resource.path("name").asText(""), collectionName);
  }

  private static List<JsonNode> children(JsonNode arrayNode) {
    return StreamSupport.stream(arrayNode.spliterator(), false).toList();
  }

  private static List<ApiRequest> parseV5Collection(JsonNode root, File file) {
    JsonNode collection = root.get("collection");
    if (collection == null || !collection.isArray()) {
      throw new IllegalArgumentException(
          "Not an Insomnia v5 collection: expected a 'collection' array: " + file);
    }
    return collectRequests(collection, v5CollectionName(root, file));
  }

  private static String v5CollectionName(JsonNode root, File file) {
    return Optional.ofNullable(root.path("name").asText(null))
        .map(String::strip)
        .filter(name -> !name.isEmpty())
        .orElseGet(() -> CollectionParsers.fileBaseName(file.getName()));
  }

  private static List<ApiRequest> collectRequests(JsonNode nodes, String collectionName) {
    if (!nodes.isArray()) {
      return List.of();
    }
    return children(nodes).stream().flatMap(node -> requestsOf(node, collectionName)).toList();
  }

  private static Stream<ApiRequest> requestsOf(JsonNode node, String collectionName) {
    Stream<ApiRequest> own = Stream.ofNullable(toRequest(node, collectionName));
    JsonNode children = node.get("children");
    if (children != null && children.isArray()) {
      return Stream.concat(own, collectRequests(children, collectionName).stream());
    }
    return own;
  }

  @Override
  @NotNull
  public List<ApiRequest> parse(@NotNull File file) throws IOException {
    Objects.requireNonNull(file, "file must not be null");
    return parseRoot(JsonYaml.readTree(file), file);
  }

  /**
   * Parses an already-read root tree; shared with {@link CollectionParsers#parse(File)} so a file
   * is only read once on the scan path.
   *
   * @param root parsed document root; must not be null
   * @param file file the root was read from; must not be null
   * @return the collected requests; never null, possibly empty
   */
  List<ApiRequest> parseRoot(@NotNull JsonNode root, @NotNull File file) {
    if (isV5Collection(root)) {
      return parseV5Collection(root, file);
    }
    List<JsonNode> resources = resourcesOf(root, file);
    String collectionName =
        workspaceName(resources).orElseGet(() -> CollectionParsers.fileBaseName(file.getName()));
    return resources.stream()
        .filter(InsomniaCollectionParser::isRequest)
        .flatMap(resource -> Stream.ofNullable(toRequest(resource, collectionName)))
        .toList();
  }
}
