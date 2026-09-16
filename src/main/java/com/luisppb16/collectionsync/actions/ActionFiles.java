/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */

package com.luisppb16.collectionsync.actions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * File helpers shared by the Endpoint Coverage context menu actions: which files can act as a
 * collection or as an OpenAPI document, and how to read an OpenAPI tree.
 */
public final class ActionFiles {

  private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("json", "yaml", "yml");
  private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
  private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

  private ActionFiles() {}

  /**
   * Reports whether the given file can be used as a collection or OpenAPI source, i.e. it has a
   * {@code .json}, {@code .yaml} or {@code .yml} extension.
   *
   * @param file selected file; may be null (e.g. no file under the popup)
   * @return true when the file name has a supported extension
   */
  public static boolean isSupportedFile(@Nullable VirtualFile file) {
    return file != null && isSupportedFile(file.getName());
  }

  /**
   * Pure predicate behind {@link #isSupportedFile(VirtualFile)}; the extension is matched
   * case-insensitively on the file name.
   *
   * @param fileName file name; may be null
   * @return true when the name ends (last extension) in {@code .json}, {@code .yaml} or {@code
   *     .yml}, in any case
   */
  public static boolean isSupportedFile(@Nullable String fileName) {
    if (fileName == null) {
      return false;
    }
    int dot = fileName.lastIndexOf('.');
    return dot >= 0
        && SUPPORTED_EXTENSIONS.contains(fileName.substring(dot + 1).toLowerCase(Locale.ROOT));
  }

  /**
   * Reports whether a parsed document root is an OpenAPI 3.x document, i.e. a JSON/YAML object with
   * a {@code paths} member.
   *
   * @param root parsed document root; must not be null
   * @return true when the root is an object declaring {@code paths}
   */
  public static boolean isOpenApiDocument(@NotNull JsonNode root) {
    Objects.requireNonNull(root, "root must not be null");
    return root.isObject() && root.hasNonNull("paths");
  }

  /**
   * Reads an OpenAPI document as a Jackson tree, using the same format criterion as {@link
   * com.luisppb16.collectionsync.source.OpenApiEndpointSource}: {@code .yaml}/{@code .yml} files
   * are parsed as YAML, and any other file is first parsed as JSON and, when that fails, retried as
   * YAML.
   *
   * @param openApiFile document to read; must not be null
   * @return the parsed root node; never null
   * @throws IOException if the file cannot be read or parses neither as JSON nor as YAML
   */
  public static @NotNull JsonNode readOpenApiTree(@NotNull File openApiFile) throws IOException {
    Objects.requireNonNull(openApiFile, "openApiFile must not be null");
    if (isYaml(openApiFile.getName())) {
      return YAML_MAPPER.readTree(openApiFile);
    }
    try {
      return JSON_MAPPER.readTree(openApiFile);
    } catch (IOException jsonFailure) {
      return YAML_MAPPER.readTree(openApiFile);
    }
  }

  private static boolean isYaml(@NotNull String fileName) {
    String lowerCaseName = fileName.toLowerCase(Locale.ROOT);
    return lowerCaseName.endsWith(".yaml") || lowerCaseName.endsWith(".yml");
  }
}
