/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */
package com.luisppb16.collectionsync.domain.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Normalizes the many path-template dialects (Spring {@code {id:\d+}}, JAX-RS {@code {id}},
 * {@code :id}) and collection URL syntaxes (Postman {@code {{id}}}, Insomnia {@code {{ _.id }}})
 * into one segment model: literals, variables and wildcards.
 *
 * <p>Variables match positionally regardless of their name; wildcards match anything.
 */
public final class PathTemplateNormalizer {

  /** One segment of a normalized path template. */
  public sealed interface Segment permits Literal, Variable, Wildcard {}

  /** A constant path segment, e.g. {@code users}. */
  public record Literal(String value) implements Segment {

    public Literal {
      if (value == null || value.isBlank()) {
        throw new IllegalArgumentException("Literal.value must not be blank");
      }
    }
  }

  /** A path variable, e.g. {@code {id}}, {@code {id:\d+}}, {@code :id}, {@code {{id}}}. */
  public record Variable(String name) implements Segment {

    public Variable {
      if (name == null || name.isBlank()) {
        throw new IllegalArgumentException("Variable.name must not be blank");
      }
    }
  }

  /** A wildcard segment ({@code *}, {@code **}) that matches any segment. */
  public record Wildcard() implements Segment {}

  /** A normalized path template: the ordered list of its segments. */
  public record PathTemplate(List<Segment> segments) {
    public PathTemplate {
      segments = List.copyOf(segments);
    }
  }

  private PathTemplateNormalizer() {}

  /**
   * Joins the class-level and method-level paths of an endpoint and normalizes them.
   *
   * @param basePath class-level path; may be empty
   * @param methodPath method-level path; may be empty
   * @return the normalized template (never null)
   */
  public static PathTemplate normalizeEndpointTemplate(String basePath, String methodPath) {
    Objects.requireNonNull(basePath, "basePath");
    Objects.requireNonNull(methodPath, "methodPath");
    return parse(stripQuery(join(basePath, methodPath)), false);
  }

  /**
   * Normalizes a collection request URL: drops scheme, host placeholder ({{baseUrl}} / _.base_url)
   * and query string, then parses the path segments.
   *
   * @param rawUrl the request URL as written in the collection
   * @return the normalized template (never null)
   */
  public static PathTemplate normalizeRequestUrl(String rawUrl) {
    if (rawUrl == null || rawUrl.isBlank()) {
      throw new IllegalArgumentException("rawUrl must not be blank");
    }
    return parse(stripQuery(stripHost(stripScheme(rawUrl.trim()))), true);
  }

  private static String join(String basePath, String methodPath) {
    String base = basePath.trim();
    String method = methodPath.trim();
    if (base.isEmpty()) {
      return method;
    }
    if (method.isEmpty() || method.equals("/")) {
      return base;
    }
    return (base.endsWith("/") ? base : base + "/") + (method.startsWith("/") ? method.substring(1) : method);
  }

  private static String stripScheme(String url) {
    int schemeIndex = url.indexOf("://");
    if (schemeIndex < 0) {
      return url;
    }
    int pathIndex = url.indexOf('/', schemeIndex + 3);
    return pathIndex >= 0 ? url.substring(pathIndex) : "";
  }

  private static String stripHost(String url) {
    if (!url.startsWith("{{")) {
      return url;
    }
    int close = url.indexOf("}}");
    if (close < 0) {
      return url;
    }
    String afterHost = url.substring(close + 2);
    return afterHost.startsWith("/") || afterHost.isBlank() ? afterHost : url;
  }

  private static String stripQuery(String path) {
    int queryIndex = path.indexOf('?');
    return queryIndex >= 0 ? path.substring(0, queryIndex) : path;
  }

  private static PathTemplate parse(String path, boolean collectionSyntax) {
    List<Segment> segments = new ArrayList<>();
    for (String rawSegment : path.split("/")) {
      Segment segment = parseSegment(rawSegment, collectionSyntax);
      if (segment != null) {
        segments.add(segment);
      }
    }
    return new PathTemplate(segments);
  }

  private static Segment parseSegment(String rawSegment, boolean collectionSyntax) {
    String segment = rawSegment.trim();
    if (segment.isEmpty()) {
      return null;
    }
    if (segment.equals("*") || segment.equals("**")) {
      return new Wildcard();
    }
    String variableName = extractVariableName(segment, collectionSyntax);
    return variableName != null ? new Variable(variableName) : new Literal(segment);
  }

  private static String extractVariableName(String segment, boolean collectionSyntax) {
    String name = null;
    if (segment.startsWith("{") && segment.endsWith("}") && segment.length() > 2) {
      name = segment.substring(1, segment.length() - 1);
    } else if (segment.startsWith(":") && segment.length() > 1) {
      name = segment.substring(1);
    }
    if (name == null) {
      return null;
    }
    // {{ _.id }} keeps the property name; {id:\d+} keeps only the name.
    name = name.replace("{", "").replace("}", "").trim();
    int regexIndex = name.indexOf(':');
    if (regexIndex >= 0) {
      name = name.substring(0, regexIndex);
    }
    if (name.startsWith("_.")) {
      name = name.substring(2).trim();
    }
    return name.isBlank() ? null : name;
  }
}