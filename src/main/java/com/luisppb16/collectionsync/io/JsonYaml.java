/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */

package com.luisppb16.collectionsync.io;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import org.jetbrains.annotations.NotNull;
import org.yaml.snakeyaml.Yaml;

/**
 * Reads a document as a Jackson tree trying JSON first and falling back to YAML (a superset of
 * JSON), so the file extension never matters: JSON documents parse directly and YAML-only documents
 * fail the strict JSON attempt and are retried with the YAML loader.
 *
 * <p>The YAML fallback goes through SnakeYAML instead of Jackson's YAML parser so that anchors,
 * aliases and merge keys are expanded before the tree is built: Jackson's YAML parser keeps them as
 * raw text (an alias becomes the anchor name), which would silently drop aliased collection
 * resources from the coverage scan.
 */
public final class JsonYaml {

  private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

  private JsonYaml() {}

  /**
   * Parses the given content.
   *
   * @param content document content; must not be null
   * @return the parsed root node; never null
   * @throws IOException when the content is neither valid JSON nor valid YAML
   */
  public static @NotNull JsonNode parse(@NotNull String content) throws IOException {
    try {
      return JSON_MAPPER.readTree(content);
    } catch (JsonProcessingException jsonError) {
      return parseYaml(content, jsonError);
    }
  }

  /**
   * Reads and parses the given file.
   *
   * @param file document file; must not be null
   * @return the parsed root node; never null
   * @throws IOException when the file cannot be read or is neither valid JSON nor valid YAML
   */
  public static @NotNull JsonNode readTree(@NotNull File file) throws IOException {
    return parse(Files.readString(file.toPath()));
  }

  private static JsonNode parseYaml(String content, JsonProcessingException jsonError)
      throws IOException {
    try {
      return toTree(new Yaml().load(content));
    } catch (RuntimeException yamlError) {
      IOException failure =
          new IOException("Document is neither valid JSON nor valid YAML", jsonError);
      failure.addSuppressed(yamlError);
      throw failure;
    }
  }

  private static JsonNode toTree(Object document) {
    if (document == null) {
      return MissingNode.getInstance();
    }
    return JSON_MAPPER.convertValue(document, JsonNode.class);
  }
}
