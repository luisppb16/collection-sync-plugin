/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */

package com.luisppb16.collectionsync.collection;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Resolves Postman/Insomnia collection variables without failing on unknown expressions. */
final class CollectionVariables {

  private static final Pattern VARIABLE =
      Pattern.compile("\\{\\{\\s*(?:_\\.)?([A-Za-z_][A-Za-z0-9_]*)\\s*}}");

  private CollectionVariables() {}

  static String resolve(String value, Map<String, String> variables) {
    if (value == null || value.isEmpty() || variables.isEmpty() || value.indexOf("{{") < 0) {
      return value;
    }
    Matcher matcher = VARIABLE.matcher(value);
    StringBuilder resolved = new StringBuilder();
    while (matcher.find()) {
      String replacement = variables.get(matcher.group(1));
      matcher.appendReplacement(
          resolved, Matcher.quoteReplacement(replacement == null ? matcher.group() : replacement));
    }
    matcher.appendTail(resolved);
    return resolved.toString();
  }

  static Map<String, String> merge(Map<String, String> parent, JsonNode variables) {
    if (variables == null || !variables.isArray() || variables.isEmpty()) {
      return parent;
    }
    Map<String, String> merged = new HashMap<>(parent);
    for (JsonNode variable : variables) {
      String key = variable.path("key").asText(null);
      JsonNode value = variable.get("value");
      if (key != null
          && !key.isBlank()
          && value != null
          && value.isTextual()
          && !value.asText().isEmpty()) {
        merged.put(key.strip(), value.asText());
      }
    }
    return merged;
  }

  static List<JsonNode> environmentData(List<JsonNode> resources, JsonNode root) {
    List<JsonNode> data = new ArrayList<>();
    for (JsonNode resource : resources) {
      if ("environment".equals(resource.path("_type").asText())) {
        addData(data, resource);
      }
    }
    JsonNode environments = root.path("environments");
    if (environments.isArray()) {
      for (JsonNode environment : environments) {
        addData(data, environment);
      }
    } else if (environments.isObject()) {
      for (Map.Entry<String, JsonNode> environment : environments.properties()) {
        addData(data, environment.getValue());
      }
    }
    return data;
  }

  static Map<String, String> uniqueValues(List<JsonNode> dataNodes) {
    Map<String, Set<String>> values = new HashMap<>();
    for (JsonNode data : dataNodes) {
      if (data == null || !data.isObject()) {
        continue;
      }
      for (Map.Entry<String, JsonNode> entry : data.properties()) {
        if (entry.getValue().isTextual() && !entry.getValue().asText().isEmpty()) {
          values
              .computeIfAbsent(entry.getKey(), key -> new LinkedHashSet<>())
              .add(entry.getValue().asText());
        }
      }
    }
    Map<String, String> unique = new HashMap<>();
    values.forEach((key, candidates) -> {
      if (candidates.size() == 1) {
        unique.put(key, candidates.iterator().next());
      }
    });
    return unique;
  }

  private static void addData(List<JsonNode> data, JsonNode environment) {
    JsonNode environmentData = environment.get("data");
    data.add(environmentData != null ? environmentData : environment);
  }
}
