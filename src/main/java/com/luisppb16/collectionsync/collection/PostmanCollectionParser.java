/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */

package com.luisppb16.collectionsync.collection;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
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
 * Parses a Postman v2.1 collection (JSON, or its YAML superset) into {@link ApiRequest}s using the
 * Jackson tree model, read through {@link JsonYaml} (content-based, so the file extension never
 * matters).
 *
 * <p>The expected shape is a root object with an {@code info} object (whose {@code name} becomes
 * the collection name, falling back to the file name without extension when absent) and an {@code
 * item} array. Every entry of {@code item} is either:
 *
 * <ul>
 *   <li>a <b>request</b>, when it has a {@code request} object: the display name comes from the
 *       item's {@code name}, the HTTP method from {@code request.method}, normalized with {@link
 *       HttpMethod#of(String)}, and the URL from {@code request.url}, which may be a plain string
 *       or an object whose {@code raw} field holds the URL (as written by the Postman UI); or
 *   <li>a <b>folder</b>, when it has a nested {@code item} array: the recursion continues into it,
 *       so requests at any depth are collected.
 * </ul>
 *
 * <p>Entries with neither {@code request} nor {@code item} are ignored, as are requests without a
 * usable method or URL. A {@code method} that is present but not text (e.g. a YAML 1.1 value like
 * {@code no} or {@code on}, which parses as a boolean) fails fast with an {@link
 * IllegalArgumentException}, as do an unsupported HTTP method and a root that is not an object with
 * {@code info} and {@code item}.
 */
public final class PostmanCollectionParser implements CollectionParser {

  private static String collectionName(JsonNode info, File file) {
    return Optional.ofNullable(info.path("name").asText(null))
        .map(String::strip)
        .filter(name -> !name.isEmpty())
        .orElseGet(() -> CollectionParsers.fileBaseName(file.getName()));
  }

  private static List<ApiRequest> collectRequests(JsonNode items, String collectionName) {
    return children(items).flatMap(item -> requestsOf(item, collectionName)).toList();
  }

  private static Stream<ApiRequest> requestsOf(JsonNode item, String collectionName) {
    if (item.hasNonNull("request")) {
      return Stream.ofNullable(toRequest(item, collectionName));
    }
    if (item.hasNonNull("item")) {
      return collectRequests(item.get("item"), collectionName).stream();
    }
    return Stream.empty();
  }

  private static ApiRequest toRequest(JsonNode item, String collectionName) {
    JsonNode requestNode = item.get("request");
    JsonNode methodNode = requestNode.path("method");
    if (!methodNode.isMissingNode() && !methodNode.isTextual()) {
      throw new IllegalArgumentException(
          "Request 'method' must be a text value: " + item.path("name").asText(""));
    }
    String method = methodNode.asText("").strip();
    String rawUrl = rawUrl(requestNode.get("url"));
    if (method.isEmpty() || rawUrl.isEmpty()) {
      return null;
    }
    return new ApiRequest(
        HttpMethod.of(method), rawUrl, item.path("name").asText(""), collectionName);
  }

  private static String rawUrl(JsonNode urlNode) {
    String rawUrl =
        switch (urlNode) {
          case null -> "";
          case TextNode text -> text.asText();
          case ObjectNode object -> object.path("raw").asText("");
          default -> "";
        };
    return rawUrl.strip();
  }

  private static Stream<JsonNode> children(JsonNode arrayNode) {
    if (!arrayNode.isArray()) {
      return Stream.empty();
    }
    return StreamSupport.stream(arrayNode.spliterator(), false);
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
    if (root == null || !root.isObject() || !root.hasNonNull("info") || !root.hasNonNull("item")) {
      throw new IllegalArgumentException(
          "Not a Postman v2.1 collection: root object must contain 'info' and 'item': " + file);
    }
    String collectionName = collectionName(root.get("info"), file);
    return collectRequests(root.get("item"), collectionName);
  }
}
