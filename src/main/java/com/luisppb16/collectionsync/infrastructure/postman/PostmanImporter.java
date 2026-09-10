/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */
package com.luisppb16.collectionsync.infrastructure.postman;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luisppb16.collectionsync.application.service.CollectionImporter;
import com.luisppb16.collectionsync.domain.model.CollectionFolder;
import com.luisppb16.collectionsync.domain.model.CollectionModel;
import com.luisppb16.collectionsync.domain.model.HttpMethod;
import com.luisppb16.collectionsync.domain.model.ParamDescriptor;
import com.luisppb16.collectionsync.domain.model.ParamLocation;
import com.luisppb16.collectionsync.domain.model.RequestDescriptor;
import com.luisppb16.collectionsync.domain.model.RequestSource;
import com.luisppb16.collectionsync.domain.service.PathTemplateNormalizer;
import com.luisppb16.collectionsync.infrastructure.io.UnsupportedCollectionFormatException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Imports a Postman v2.1 collection (https://schema.getpostman.com/json/collection/v2.1.0)
 * into the shared collection model. Unknown item fields (scripts, responses, auth) are kept in
 * {@link RequestDescriptor#rawExtra()} for faithful re-export.
 */
public final class PostmanImporter implements CollectionImporter {

  public static final String POSTMAN_V21_SCHEMA = "https://schema.getpostman.com/json/collection/v2.1.0/collection.json";

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Override
  public CollectionModel importFile(Path file) throws IOException {
    Objects.requireNonNull(file, "file");
    return importTree(MAPPER.readTree(file.toFile()));
  }

  /** @see #importFile(Path) */
  public CollectionModel importTree(JsonNode root) {
    Objects.requireNonNull(root, "root");
    JsonNode info = root.path("info");
    if (info.isMissingNode() || info.path("name").isMissingNode()) {
      throw new UnsupportedCollectionFormatException("Not a Postman v2.1 collection: missing info.name");
    }
    String name = info.path("name").asText();
    String id = info.path("_postman_id").asText(UUID.randomUUID().toString());
    Map<String, String> variables = readVariables(root.path("variable"));

    CollectionFolder rootFolder = new CollectionFolder(name);
    readItems(root.path("item"), rootFolder);

    return new CollectionModel(id, name, rootFolder, variables, RequestSource.POSTMAN.name(), root.deepCopy());
  }

  private static Map<String, String> readVariables(JsonNode variablesNode) {
    Map<String, String> variables = new LinkedHashMap<>();
    if (variablesNode.isArray()) {
      for (JsonNode variable : variablesNode) {
        String key = variable.path("key").asText(null);
        if (key != null && !key.isBlank()) {
          variables.put(key, variable.path("value").asText(""));
        }
      }
    }
    return variables;
  }

  private static void readItems(JsonNode itemsNode, CollectionFolder parentFolder) {
    if (!itemsNode.isArray()) {
      return;
    }
    for (JsonNode item : itemsNode) {
      String name = item.path("name").asText("");
      if (item.hasNonNull("item")) {
        CollectionFolder folder = parentFolder.findOrCreateChild(name);
        folder.setRawExtra(item.deepCopy());
        readItems(item.path("item"), folder);
      } else if (item.hasNonNull("request")) {
        RequestDescriptor request = readRequest(name, item, parentFolder.getName());
        if (request != null) {
          parentFolder.addRequest(request);
        }
      }
    }
  }

  private static RequestDescriptor readRequest(String name, JsonNode item, String folderName) {
    JsonNode requestNode = item.path("request");
    String methodText = requestNode.path("method").asText("GET");
    HttpMethod method = readMethod(methodText);
    if (method == null) {
      return null;
    }
    String rawUrl = readRawUrl(requestNode.path("url"));
    String normalizedTemplate = PathTemplateNormalizer.canonicalForm(PathTemplateNormalizer.normalizeRequestUrl(rawUrl));
    JsonNode body = readBody(requestNode.path("body"));

    return new RequestDescriptor(
        name,
        method,
        rawUrl,
        normalizedTemplate,
        readQueryParams(requestNode.path("url").path("query")),
        readHeaders(requestNode.path("header")),
        body,
        folderName,
        RequestSource.POSTMAN,
        item.deepCopy());
  }

  private static HttpMethod readMethod(String methodText) {
    try {
      return HttpMethod.valueOf(methodText.trim().toUpperCase());
    } catch (IllegalArgumentException unknownMethod) {
      return null;
    }
  }

  private static String readRawUrl(JsonNode urlNode) {
    if (urlNode.isTextual()) {
      return urlNode.asText();
    }
    if (urlNode.hasNonNull("raw")) {
      return urlNode.path("raw").asText();
    }
    StringBuilder url = new StringBuilder();
    for (JsonNode pathSegment : urlNode.path("path")) {
      url.append('/').append(pathSegment.asText());
    }
    return url.isEmpty() ? "/" : url.toString();
  }

  private static JsonNode readBody(JsonNode bodyNode) {
    if (!bodyNode.hasNonNull("raw")) {
      return null;
    }
    String raw = bodyNode.path("raw").asText();
    try {
      return MAPPER.readTree(raw);
    } catch (IOException notJson) {
      return MAPPER.getNodeFactory().textNode(raw);
    }
  }

  private static java.util.List<ParamDescriptor> readQueryParams(JsonNode queryNode) {
    java.util.List<ParamDescriptor> query = new java.util.ArrayList<>();
    if (queryNode.isArray()) {
      for (JsonNode queryParam : queryNode) {
        String key = queryParam.path("key").asText(null);
        if (key != null && !key.isBlank()) {
          query.add(new ParamDescriptor(key, ParamLocation.QUERY, "String", false));
        }
      }
    }
    return query;
  }

  private static java.util.List<ParamDescriptor> readHeaders(JsonNode headersNode) {
    java.util.List<ParamDescriptor> headers = new java.util.ArrayList<>();
    if (headersNode.isArray()) {
      for (JsonNode header : headersNode) {
        String key = header.path("key").asText(null);
        if (key != null && !key.isBlank()) {
          headers.add(new ParamDescriptor(key, ParamLocation.HEADER, "String", false));
        }
      }
    }
    return headers;
  }
}