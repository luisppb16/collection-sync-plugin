/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */

package com.luisppb16.collectionsync.coverage;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.jetbrains.annotations.Nullable;

/**
 * Resolves which project module a collection file belongs to. An explicit binding always wins; when
 * there is none, a module whose normalized name equals a path segment is inferred, preferring the
 * longest match and falling back to global when the match is ambiguous.
 */
public final class CollectionModuleResolver {

  private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]");

  private CollectionModuleResolver() {}

  public static String resolve(
      @Nullable String collectionPath, @Nullable String explicitModule, List<String> moduleNames) {
    if (explicitModule != null
        && !explicitModule.isBlank()
        && moduleNames.contains(explicitModule)) {
      return explicitModule;
    }
    return infer(collectionPath, moduleNames);
  }

  static String infer(@Nullable String collectionPath, List<String> moduleNames) {
    if (collectionPath == null || collectionPath.isBlank()) {
      return "";
    }
    String[] segments = collectionPath.split("[/\\\\]");
    String bestModule = "";
    int bestLength = 0;
    boolean ambiguous = false;
    for (String moduleName : moduleNames) {
      String normalizedModule = normalize(moduleName);
      if (normalizedModule.isEmpty() || !matchesSegment(segments, normalizedModule)) {
        continue;
      }
      if (normalizedModule.length() > bestLength) {
        bestModule = moduleName;
        bestLength = normalizedModule.length();
        ambiguous = false;
      } else if (normalizedModule.length() == bestLength) {
        ambiguous = true;
      }
    }
    return ambiguous ? "" : bestModule;
  }

  private static boolean matchesSegment(String[] segments, String normalizedModule) {
    for (String segment : segments) {
      if (normalize(segment).equals(normalizedModule)) {
        return true;
      }
    }
    return false;
  }

  private static String normalize(String value) {
    return NON_ALPHANUMERIC.matcher(value.toLowerCase(Locale.ROOT)).replaceAll("");
  }
}
