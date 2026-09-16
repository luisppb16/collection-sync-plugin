/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */

package com.luisppb16.collectionsync.collection;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.jetbrains.annotations.NotNull;

import com.luisppb16.collectionsync.domain.model.ApiRequest;

/**
 * Parses a collection file (Postman v2.1 or Insomnia v4/v5) into requests.
 *
 * <p>Implementations must tolerate nested folder structures and must skip entries without a
 * usable method or URL. The file format (JSON or YAML) is detected by the implementation.
 */
public interface CollectionParser {

    /**
     * Parses the given collection file.
     *
     * @param file collection file to read; must not be null
     * @return every request found in the file, possibly empty; never null
     * @throws IOException              if the file cannot be read or parsed
     * @throws IllegalArgumentException if the file does not follow the expected format
     */
    @NotNull
    List<ApiRequest> parse(@NotNull File file) throws IOException;
}