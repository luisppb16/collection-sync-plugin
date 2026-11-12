/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */

package com.luisppb16.collectionsync.scan;

import com.luisppb16.collectionsync.domain.model.HttpMethod;
import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/**
 * {@link EndpointAnnotationResolver} for JAX-RS resources. It is instantiated once per namespace
 * ({@code jakarta.ws.rs} and {@code javax.ws.rs}) so both generations are detected.
 */
public final class JaxRsAnnotationResolver extends FixedVerbResolver {

  private static final String DEFAULT_NAMESPACE = "jakarta.ws.rs";

  public JaxRsAnnotationResolver() {
    this(DEFAULT_NAMESPACE);
  }

  /**
   * @param namespace annotation package, e.g. {@code jakarta.ws.rs} or {@code javax.ws.rs}; must
   *     not be null or blank
   */
  public JaxRsAnnotationResolver(@NotNull String namespace) {
    super(
        List.of(pathAnnotation(namespace)),
        List.of(pathAnnotation(namespace)),
        fixedMethods(namespace));
  }

  private static String pathAnnotation(String namespace) {
    return normalized(namespace) + ".Path";
  }

  private static List<FixedVerb> fixedMethods(String namespace) {
    String prefix = normalized(namespace);
    return List.of(
        new FixedVerb(prefix + ".GET", HttpMethod.GET),
        new FixedVerb(prefix + ".POST", HttpMethod.POST),
        new FixedVerb(prefix + ".PUT", HttpMethod.PUT),
        new FixedVerb(prefix + ".DELETE", HttpMethod.DELETE),
        new FixedVerb(prefix + ".PATCH", HttpMethod.PATCH),
        new FixedVerb(prefix + ".HEAD", HttpMethod.HEAD),
        new FixedVerb(prefix + ".OPTIONS", HttpMethod.OPTIONS));
  }

  private static String normalized(String namespace) {
    String resolved = Objects.requireNonNull(namespace, "namespace must not be null").strip();
    if (resolved.isEmpty()) {
      throw new IllegalArgumentException("namespace must not be blank");
    }
    return resolved;
  }
}
