/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */

package com.luisppb16.collectionsync.actions;

import com.fasterxml.jackson.databind.JsonNode;
import com.intellij.openapi.vfs.VirtualFile;
import com.luisppb16.collectionsync.io.JsonYaml;
import java.io.File;
import java.io.IOException;
import java.util.Objects;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * File helpers shared by the Endpoint Coverage context menu actions: which files can act as a
 * collection or as an OpenAPI document, and how to read an OpenAPI tree.
 */
public final class ActionFiles {

  /**
   * Platform file type names under which a collection or OpenAPI document may be edited, matched
   * against {@link VirtualFile#getFileType()} so the accepted files follow what the IDE shows for
   * them instead of a hard-coded extension list: {@code JSON}, {@code YAML} and any text file the
   * IDE has no dedicated type for (e.g. a collection kept in {@code .http} style files or without
   * an extension).
   */
  private static final Set<String> SUPPORTED_FILE_TYPE_NAMES = Set.of("JSON", "YAML", "PLAIN_TEXT");

  private ActionFiles() {}

  /**
   * Reports whether the given file can be used as a collection or OpenAPI source, i.e. the IDE
   * recognizes it as JSON, YAML or plain text.
   *
   * @param file selected file; may be null (e.g. no file under the popup)
   * @return true when the file type is JSON, YAML or plain text
   */
  public static boolean isSupportedFile(@Nullable VirtualFile file) {
    return file != null && SUPPORTED_FILE_TYPE_NAMES.contains(file.getFileType().getName());
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
   * Reads an OpenAPI document as a Jackson tree with the same content-based criterion as {@link
   * com.luisppb16.collectionsync.collection.CollectionParsers}: JSON first, YAML fallback, whatever
   * the file extension says.
   *
   * @param openApiFile document to read; must not be null
   * @return the parsed root node; never null
   * @throws IOException if the file cannot be read or parses neither as JSON nor as YAML
   */
  public static @NotNull JsonNode readOpenApiTree(@NotNull File openApiFile) throws IOException {
    Objects.requireNonNull(openApiFile, "openApiFile must not be null");
    return JsonYaml.readTree(openApiFile);
  }
}
