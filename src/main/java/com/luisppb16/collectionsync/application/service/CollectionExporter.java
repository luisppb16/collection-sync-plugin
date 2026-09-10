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

/** Writes a collection model to a file of a concrete collection format. */
public interface CollectionExporter {

  /**
   * @param collection the collection to write
   * @param target the destination file
   * @throws IOException when the file cannot be written
   */
  void exportFile(CollectionModel collection, Path target) throws IOException;
}