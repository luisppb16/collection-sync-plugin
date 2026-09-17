/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */

package com.luisppb16.collectionsync.collection;

import com.fasterxml.jackson.databind.JsonNode;
import com.luisppb16.collectionsync.domain.model.ApiRequest;
import com.luisppb16.collectionsync.io.JsonYaml;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/**
 * Factory that selects the {@link CollectionParser} matching a collection file, plus a one-shot
 * parse entry point.
 *
 * <p>The format is decided from the parsed content alone (JSON is tried first, with a YAML
 * fallback, so the file extension never matters): an object with {@code info} and {@code item} is a
 * Postman v2.1 collection, an object whose {@code type} starts with {@code
 * collection.insomnia.rest/} is an Insomnia v5 collection, and an object with {@code resources} (or
 * a bare resource array) is an Insomnia v4/v5 export. Anything else fails fast with an {@link
 * IllegalArgumentException}; content that does not even parse fails with an {@link IOException}.
 *
 * <p>{@link #parse(File)} reads the file once and hands the already-parsed tree to the matching
 * parser; {@link #forFile(File)} + {@link CollectionParser#parse(File)} is the composable variant
 * that reads the file twice (once to select the parser, once to parse it), which is fine when the
 * parser instance is kept and reused.
 */
public final class CollectionParsers {

  private CollectionParsers() {}

  /**
   * Parses the given collection file in a single read: the content decides the format and the
   * matching parser interprets the already-parsed tree.
   *
   * @param file collection file to parse; must not be null
   * @return the collected requests; never null, possibly empty
   * @throws IOException if the file cannot be read or parses neither as JSON nor as YAML
   * @throws IllegalArgumentException if the parsed content follows neither collection format
   */
  public static @NotNull List<ApiRequest> parse(@NotNull File file) throws IOException {
    Objects.requireNonNull(file, "file must not be null");
    JsonNode root = JsonYaml.readTree(file);
    return switch (formatOf(root)) {
      case POSTMAN -> new PostmanCollectionParser().parseRoot(root, file);
      case INSOMNIA -> new InsomniaCollectionParser().parseRoot(root, file);
    };
  }

  /**
   * Creates the parser for the given collection file.
   *
   * @param file collection file to inspect; must not be null
   * @return the parser matching the file's format; never null
   * @throws IOException if the file cannot be read or does not parse as JSON nor YAML
   * @throws IllegalArgumentException if the parsed content follows neither collection format
   */
  public static CollectionParser forFile(@NotNull File file) throws IOException {
    Objects.requireNonNull(file, "file must not be null");
    return switch (detectFormat(Files.readString(file.toPath()))) {
      case POSTMAN -> new PostmanCollectionParser();
      case INSOMNIA -> new InsomniaCollectionParser();
    };
  }

  /**
   * Detects the collection format of a content. Kept content-based (instead of file-based) so the
   * detection rules can be tested without touching the file system.
   *
   * @param content file content; must not be null
   * @return the detected format; never null
   * @throws IOException if the content cannot be parsed as JSON nor YAML
   * @throws IllegalArgumentException if the parsed content follows neither collection format
   */
  static Format detectFormat(String content) throws IOException {
    return formatOf(JsonYaml.parse(content));
  }

  /**
   * Returns the file name without its last extension.
   *
   * @param fileName file name; must not be null
   * @return the base name; never null
   */
  static String fileBaseName(String fileName) {
    Objects.requireNonNull(fileName, "fileName must not be null");
    int dot = fileName.lastIndexOf('.');
    return dot <= 0 ? fileName : fileName.substring(0, dot);
  }

  private static Format formatOf(JsonNode root) {
    if (root == null || root.isMissingNode()) {
      throw new IllegalArgumentException("Collection file is empty");
    }
    if (root.isObject() && root.hasNonNull("info") && root.hasNonNull("item")) {
      return Format.POSTMAN;
    }
    if (InsomniaCollectionParser.isV5Collection(root)
        || (root.isObject() && root.hasNonNull("resources"))
        || root.isArray()) {
      return Format.INSOMNIA;
    }
    throw new IllegalArgumentException(
        "Unsupported collection format: expected a Postman v2.1 collection ('info' and 'item') "
            + "or an Insomnia v4/v5 export ('resources' or a 'collection.insomnia.rest/5.x' type)");
  }

  /** Collection formats supported by the factory. */
  enum Format {
    /** Postman v2.1: an object with {@code info} and {@code item}. */
    POSTMAN,
    /**
     * Insomnia v4/v5: an object with {@code resources}, a bare resource array, or a v5 collection.
     */
    INSOMNIA
  }
}
