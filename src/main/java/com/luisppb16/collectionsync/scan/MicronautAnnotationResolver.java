/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */

package com.luisppb16.collectionsync.scan;

import com.luisppb16.collectionsync.domain.model.HttpMethod;
import java.util.List;

/** {@link EndpointAnnotationResolver} for Micronaut controllers. */
public final class MicronautAnnotationResolver extends FixedVerbResolver {

  private static final String CONTROLLER = "io.micronaut.http.annotation.Controller";

  private static final List<FixedVerb> FIXED_METHODS =
      List.of(
          new FixedVerb("io.micronaut.http.annotation.Get", HttpMethod.GET),
          new FixedVerb("io.micronaut.http.annotation.Post", HttpMethod.POST),
          new FixedVerb("io.micronaut.http.annotation.Put", HttpMethod.PUT),
          new FixedVerb("io.micronaut.http.annotation.Delete", HttpMethod.DELETE),
          new FixedVerb("io.micronaut.http.annotation.Patch", HttpMethod.PATCH),
          new FixedVerb("io.micronaut.http.annotation.Head", HttpMethod.HEAD),
          new FixedVerb("io.micronaut.http.annotation.Options", HttpMethod.OPTIONS),
          new FixedVerb("io.micronaut.http.annotation.Trace", HttpMethod.TRACE));

  private static final List<String> VERB_NAMES =
      FIXED_METHODS.stream().map(FixedVerb::qualifiedName).toList();

  public MicronautAnnotationResolver() {
    super(List.of(CONTROLLER), VERB_NAMES, FIXED_METHODS);
  }
}
