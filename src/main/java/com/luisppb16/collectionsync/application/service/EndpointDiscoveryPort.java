/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */
package com.luisppb16.collectionsync.application.service;

import com.luisppb16.collectionsync.domain.model.EndpointDescriptor;
import java.util.List;

/** Port for endpoint discovery; implemented by the PSI layer so use cases stay framework-free. */
public interface EndpointDiscoveryPort {

  /**
   * @return every endpoint discovered in the project sources; empty while indexing or before the
   *         first scan
   */
  List<EndpointDescriptor> endpoints();
}