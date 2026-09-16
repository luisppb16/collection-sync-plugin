/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */

package com.luisppb16.collectionsync.source;

import com.intellij.openapi.project.Project;
import com.luisppb16.collectionsync.domain.model.ApiEndpoint;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * Provider of the real endpoints of the project.
 *
 * <p>Implementations: {@code OpenApiEndpointSource} (reads a local OpenAPI 3.x file) and {@code
 * ControllerEndpointSource} (PSI scan of Spring/JAX-RS controllers). Implementations must be thread
 * safe and must be called from a background thread.
 */
public interface EndpointSource {

  /**
   * Collects every endpoint visible to this source.
   *
   * @param project current project; must not be null
   * @return the endpoints, possibly empty; never null
   */
  @NotNull
  List<ApiEndpoint> collect(@NotNull Project project);
}
