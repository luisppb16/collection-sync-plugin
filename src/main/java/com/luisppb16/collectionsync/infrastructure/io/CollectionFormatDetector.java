/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */
package com.luisppb16.collectionsync.infrastructure.io;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Detects the collection format of a file: Insomnia v4 exports carry {@code __export_format},
 * Postman v2.1 collections carry {@code info._postman_id} or a Postman {@code info.schema}.
 */
public final class CollectionFormatDetector {

  private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
  private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

  private CollectionFormatDetector() {}

  /**
   * Detects whether the file is a Postman v2.1 or an Insomnia v4 collection.
   *
   * @param file the collection file
   * @return the detected format
   * @throws IOException when the file cannot be read
   * @throws UnsupportedCollectionFormatException when no format is recognized
   */
  public static CollectionFormat detect(Path file) throws IOException {
    Objects.requireNonNull(file, "file");
    JsonNode root = readTree(file);
    return detectFromTree(root);
  }

  /** @see #detect(Path) */
  public static CollectionFormat detectFromTree(JsonNode root) {
    Objects.requireNonNull(root, "root");
    if (root.hasNonNull("__export_format")) {
      return CollectionFormat.INSOMNIA;
    }
    JsonNode info = root.path("info");
    boolean postmanSchema = info.path("schema").asText("").contains("postman");
    boolean postmanId = info.has("_postman_id");
    if (postmanSchema || postmanId) {
      return CollectionFormat.POSTMAN;
    }
    throw new UnsupportedCollectionFormatException(
        "The file is neither a Postman v2.1 collection (missing info.schema/info._postman_id) nor an Insomnia v4 export (missing __export_format)");
  }

  private static JsonNode readTree(Path file) throws IOException {
    String fileName = file.getFileName().toString().toLowerCase();
    ObjectMapper mapper = fileName.endsWith(".yaml") || fileName.endsWith(".yml") ? YAML_MAPPER : JSON_MAPPER;
    try {
      return mapper.readTree(file.toFile());
    } catch (IOException firstAttemptFailure) {
      // Content may not match the extension: try the other parser before failing.
      try {
        return (mapper == JSON_MAPPER ? YAML_MAPPER : JSON_MAPPER).readTree(file.toFile());
      } catch (IOException ignoredFailure) {
        throw firstAttemptFailure;
      }
    }
  }
}