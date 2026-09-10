/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */
package com.luisppb16.collectionsync.application.service;

import com.luisppb16.collectionsync.domain.model.CollectionModel;
import java.io.IOException;
import java.nio.file.Path;

/** Reads a collection file of a concrete format into the shared collection model. */
public interface CollectionImporter {

  /**
   * @param file the collection file to read
   * @return the parsed collection
   * @throws IOException when the file cannot be read
   * @throws com.luisppb16.collectionsync.infrastructure.io.UnsupportedCollectionFormatException when
   *         the content is not a valid collection of this format
   */
  CollectionModel importFile(Path file) throws IOException;
}