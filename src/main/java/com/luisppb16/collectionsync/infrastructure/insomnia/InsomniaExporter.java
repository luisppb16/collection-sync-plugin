/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */
package com.luisppb16.collectionsync.infrastructure.insomnia;

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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Exports a collection model as an Insomnia v4 export (YAML by default, JSON as an option).
 * Imported resources keep their original {@code _id}/{@code parentId} nodes; generated resources
 * get stable deterministic ids derived from their names, so repeated exports are identical.
 */
public final class InsomniaExporter implements CollectionExporter {

  private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new com.fasterxml.jackson.dataformat.yaml.YAMLFactory());
  private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

  private final Instant exportDate;

  public InsomniaExporter() {
    this(Instant.now());
  }

  /** Constructor for tests: a fixed date makes repeated exports byte-identical. */
  public InsomniaExporter(Instant exportDate) {
    this.exportDate = Objects.requireNonNull(exportDate, "exportDate");
  }

  @Override
  public void exportFile(CollectionModel collection, Path target) throws IOException {
    Objects.requireNonNull(collection, "collection");
    Objects.requireNonNull(target, "target");
    ObjectMapper mapper = isYamlTarget(target) ? YAML_MAPPER : JSON_MAPPER;
    Files.writeString(target, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(toJson(collection)), StandardCharsets.UTF_8);
  }

  /** @return the Insomnia v4 document of the collection. */
  public JsonNode toJson(CollectionModel collection) {
    ObjectNode document = JSON_MAPPER.createObjectNode();
    document.put("_type", "export");
    document.put("__export_format", 4);
    document.put("__export_date", exportDate.toString());
    document.put("__export_source", "collectionsync");

    ArrayNode resources = document.putArray("resources");
    Map<CollectionFolder, String> folderIds = new HashMap<>();
    exportWorkspace(collection, resources);
    exportFolders(collection.root(), resources, folderIds, collection.id());
    exportEnvironments(collection, resources);
    return document;
  }

  private static void exportWorkspace(CollectionModel collection, ArrayNode resources) {
    if (collection.rawExtra() != null && collection.rawExtra().isObject()) {
      resources.add(collection.rawExtra().deepCopy());
      return;
    }
    ObjectNode workspace = JSON_MAPPER.createObjectNode();
    workspace.put("_id", stableId("wrk_", collection.name()));
    workspace.put("_type", "workspace");
    workspace.putNull("parentId");
    workspace.put("name", collection.name());
    resources.add(workspace);
  }

  /** Exports the folder tree depth-first, registering each folder id for its requests. */
  private void exportFolders(CollectionFolder folder, ArrayNode resources, Map<CollectionFolder, String> folderIds, String parentId) {
    for (CollectionFolder child : folder.getChildren()) {
      String folderId = exportRequestGroup(child, resources, parentId);
      folderIds.put(child, folderId);
      exportFolders(child, resources, folderIds, folderId);
    }
    for (RequestDescriptor request : folder.getRequests()) {
      exportRequest(request, resources, parentId);
    }
  }

  private String exportRequestGroup(CollectionFolder folder, ArrayNode resources, String parentId) {
    String folderId;
    if (folder.getRawExtra() != null && folder.getRawExtra().hasNonNull("_id")) {
      folderId = folder.getRawExtra().path("_id").asText();
    } else {
      folderId = stableId("fld_", folder.getName());
    }
    if (folder.getRawExtra() != null) {
      ObjectNode group = (ObjectNode) folder.getRawExtra().deepCopy();
      group.put("parentId", parentId);
      resources.add(group);
    } else {
      ObjectNode group = JSON_MAPPER.createObjectNode();
      group.put("_id", folderId);
      group.put("_type", "request_group");
      group.put("parentId", parentId);
      group.put("name", folder.getName());
      resources.add(group);
    }
    return folderId;
  }

  private void exportRequest(RequestDescriptor request, ArrayNode resources, String parentId) {
    if (request.source() == RequestSource.INSOMNIA && request.rawExtra() != null) {
      resources.add(request.rawExtra().deepCopy());
      return;
    }
    ObjectNode resource = JSON_MAPPER.createObjectNode();
    resource.put("_id", stableId("req_", request.name()));
    resource.put("_type", "request");
    resource.put("parentId", parentId == null ? "" : parentId);
    resource.put("name", request.name());
    resource.put("method", request.method().name());
    resource.put("url", request.rawUrl());
    ArrayNode headers = resource.putArray("headers");
    for (ParamDescriptor header : request.headers()) {
      ObjectNode headerEntry = headers.addObject();
      headerEntry.put("name", header.name());
      headerEntry.put("value", "{{ _." + header.name() + " }}");
      headerEntry.put("disabled", false);
    }
    ArrayNode parameters = resource.putArray("parameters");
    for (ParamDescriptor queryParam : request.query()) {
      ObjectNode parameter = parameters.addObject();
      parameter.put("name", queryParam.name());
      parameter.put("value", "{{ _." + queryParam.name() + " }}");
      parameter.put("disabled", !queryParam.required());
    }
    if (request.body() != null) {
      ObjectNode body = resource.putObject("body");
      body.put("mimeType", "application/json");
      body.put("text", request.body().toString());
    }
    resources.add(resource);
  }

  private static void exportEnvironments(CollectionModel collection, ArrayNode resources) {
    if (collection.variables().isEmpty()) {
      return;
    }
    ObjectNode environment = JSON_MAPPER.createObjectNode();
    environment.put("_id", stableId("env_", collection.name()));
    environment.put("_type", "environment");
    environment.put("parentId", collection.id());
    environment.put("name", "Base Environment");
    ObjectNode data = environment.putObject("data");
    collection.variables().forEach(data::put);
    resources.add(environment);
  }

  /** @return a stable id: the prefix plus the MD5 hex of the name (12 chars), so exports repeat. */
  private static String stableId(String prefix, String name) {
    try {
      MessageDigest digest = MessageDigest.getInstance("MD5");
      byte[] hash = digest.digest(name.getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder();
      for (int index = 0; index < 12; index++) {
        hex.append(String.format("%02x", hash[index]));
      }
      return prefix + hex;
    } catch (NoSuchAlgorithmException missingAlgorithm) {
      throw new IllegalStateException("MD5 digest is always available on the JVM", missingAlgorithm);
    }
  }

  private static boolean isYamlTarget(Path target) {
    String fileName = target.getFileName().toString().toLowerCase();
    return fileName.endsWith(".yaml") || fileName.endsWith(".yml");
  }
}