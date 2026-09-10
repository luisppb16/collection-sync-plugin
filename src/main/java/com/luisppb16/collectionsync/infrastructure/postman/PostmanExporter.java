/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */
package com.luisppb16.collectionsync.infrastructure.postman;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.luisppb16.collectionsync.application.service.CollectionExporter;
import com.luisppb16.collectionsync.domain.model.CollectionFolder;
import com.luisppb16.collectionsync.domain.model.CollectionModel;
import com.luisppb16.collectionsync.domain.model.ParamDescriptor;
import com.luisppb16.collectionsync.domain.model.RequestDescriptor;
import com.luisppb16.collectionsync.domain.model.RequestSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Exports a collection model as Postman v2.1 JSON. Requests imported from Postman are re-emitted
 * untouched (their original item keeps scripts, responses and protocolProfileBehavior); generated
 * requests are built from scratch with the plugin-managed fields.
 */
public final class PostmanExporter implements CollectionExporter {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Override
  public void exportFile(CollectionModel collection, Path target) throws IOException {
    Objects.requireNonNull(collection, "collection");
    Objects.requireNonNull(target, "target");
    Files.writeString(target, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(toJson(collection)));
  }

  /** @return the Postman v2.1 JSON of the collection. */
  public JsonNode toJson(CollectionModel collection) {
    ObjectNode root = MAPPER.createObjectNode();
    ObjectNode info = root.putObject("info");
    info.put("_postman_id", collection.id());
    info.put("name", collection.name());
    info.put("schema", PostmanImporter.POSTMAN_V21_SCHEMA);

    ArrayNode items = root.putArray("item");
    exportFolderContents(collection.root(), items);

    if (!collection.variables().isEmpty()) {
      ArrayNode variables = root.putArray("variable");
      collection.variables().forEach((key, value) -> {
        ObjectNode variable = variables.addObject();
        variable.put("key", key);
        variable.put("value", value);
      });
    }
    return root;
  }

  private static void exportFolderContents(CollectionFolder folder, ArrayNode items) {
    for (CollectionFolder child : folder.getChildren()) {
      exportFolder(child, items);
    }
    for (RequestDescriptor request : folder.getRequests()) {
      items.add(exportRequest(request));
    }
  }

  private static void exportFolder(CollectionFolder folder, ArrayNode items) {
    if (!folder.getRequests().isEmpty() || !folder.getChildren().isEmpty()) {
      ObjectNode item = items.addObject();
      item.put("name", folder.getName());
      ArrayNode children = item.putArray("item");
      for (CollectionFolder child : folder.getChildren()) {
        exportFolder(child, children);
      }
      for (RequestDescriptor request : folder.getRequests()) {
        children.add(exportRequest(request));
      }
    }
  }

  private static JsonNode exportRequest(RequestDescriptor request) {
    if (request.source() == RequestSource.POSTMAN && request.rawExtra() != null) {
      return request.rawExtra().deepCopy();
    }
    return buildFreshItem(request);
  }

  private static JsonNode buildFreshItem(RequestDescriptor request) {
    ObjectNode item = MAPPER.createObjectNode();
    item.put("name", request.name());
    ObjectNode requestNode = item.putObject("request");
    requestNode.put("method", request.method().name());
    requestNode.set("url", buildUrl(request));
    requestNode.set("header", buildHeaders(request.headers()));
    if (request.body() != null) {
      ObjectNode body = requestNode.putObject("body");
      body.put("mode", "raw");
      body.put("raw", request.body().toString());
      ObjectNode options = body.putObject("options");
      options.putObject("raw").put("language", "json");
    }
    return item;
  }

  private static JsonNode buildUrl(RequestDescriptor request) {
    ObjectNode url = MAPPER.createObjectNode();
    url.put("raw", request.rawUrl());
    int firstSlash = request.rawUrl().indexOf('/');
    if (firstSlash > 0) {
      ArrayNode host = url.putArray("host");
      host.add(request.rawUrl().substring(0, firstSlash));
      ArrayNode path = url.putArray("path");
      String pathPart = request.rawUrl().substring(firstSlash + 1);
      if (!pathPart.isBlank()) {
        for (String segment : pathPart.split("/")) {
          path.add(segment);
        }
      }
    }
    ArrayNode query = url.putArray("query");
    for (ParamDescriptor queryParam : request.query()) {
      ObjectNode queryEntry = query.addObject();
      queryEntry.put("key", queryParam.name());
      queryEntry.put("value", "{{" + queryParam.name() + "}}");
      queryEntry.put("disabled", !queryParam.required());
    }
    return url;
  }

  private static JsonNode buildHeaders(List<ParamDescriptor> headers) {
    ArrayNode headerArray = MAPPER.createArrayNode();
    for (ParamDescriptor header : headers) {
      ObjectNode headerEntry = headerArray.addObject();
      headerEntry.put("key", header.name());
      headerEntry.put("value", "{{" + header.name() + "}}");
    }
    return headerArray;
  }
}