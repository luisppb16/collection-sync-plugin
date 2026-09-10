/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */
package com.luisppb16.collectionsync.infrastructure.insomnia;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Imports an Insomnia v4 export (JSON or YAML) into the shared collection model. The export is a
 * resource graph keyed by {@code _id}/{@code parentId}: workspaces, request groups, requests and
 * environments. Unknown resource fields are kept for faithful re-export.
 */
public final class InsomniaImporter implements CollectionImporter {

  private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
  private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

  @Override
  public CollectionModel importFile(Path file) throws IOException {
    Objects.requireNonNull(file, "file");
    String fileName = file.getFileName().toString().toLowerCase();
    ObjectMapper mapper = fileName.endsWith(".yaml") || fileName.endsWith(".yml") ? YAML_MAPPER : JSON_MAPPER;
    try {
      return importTree(mapper.readTree(Files.readString(file)));
    } catch (IOException parseFailure) {
      // Content may not match the extension: try the other parser before failing.
      ObjectMapper fallback = mapper == JSON_MAPPER ? YAML_MAPPER : JSON_MAPPER;
      return importTree(fallback.readTree(Files.readString(file)));
    }
  }

  /** @see #importFile(Path) */
  public CollectionModel importTree(JsonNode root) {
    Objects.requireNonNull(root, "root");
    if (!"export".equals(root.path("_type").asText()) || root.path("__export_format").asInt(-1) != 4) {
      throw new UnsupportedCollectionFormatException("Not an Insomnia v4 export: missing _type=export / __export_format=4");
    }
    JsonNode workspace = findWorkspace(root.path("resources"));

    String name = workspace == null ? "Insomnia workspace" : workspace.path("name").asText("Insomnia workspace");
    String id = workspace == null ? "wrk_" + UUID.randomUUID().toString().replace("-", "") : workspace.path("_id").asText();

    Map<String, CollectionFolder> foldersById = new HashMap<>();
    Map<String, String> variables = readEnvironments(root.path("resources"));

    CollectionFolder rootFolder = new CollectionFolder(name);
    readRequestGroups(rootFolder, foldersById, root.path("resources"));
    readRequests(rootFolder, foldersById, root.path("resources"));

    return new CollectionModel(id, name, rootFolder, variables, RequestSource.INSOMNIA.name(), workspace == null ? null : workspace.deepCopy());
  }

  private static JsonNode findWorkspace(JsonNode resources) {
    for (JsonNode resource : resources) {
      if ("workspace".equals(resource.path("_type").asText())) {
        return resource;
      }
    }
    return null;
  }

  private static Map<String, String> readEnvironments(JsonNode resources) {
    Map<String, String> variables = new LinkedHashMap<>();
    for (JsonNode resource : resources) {
      if ("environment".equals(resource.path("_type").asText())) {
        JsonNode data = resource.path("data");
        data.properties().forEach(property -> variables.put(property.getKey(), property.getValue().asText("")));
      }
    }
    return variables;
  }

  private static void readRequestGroups(CollectionFolder rootFolder, Map<String, CollectionFolder> foldersById, JsonNode resources) {
    for (JsonNode resource : resources) {
      if (!"request_group".equals(resource.path("_type").asText())) {
        continue;
      }
      String id = resource.path("_id").asText();
      String parentId = resource.path("parentId").asText();
      CollectionFolder parent = parentFolderFor(parentId, rootFolder, foldersById);
      CollectionFolder folder = parent.findOrCreateChild(resource.path("name").asText(""));
      folder.setRawExtra(resource.deepCopy());
      foldersById.put(id, folder);
    }
  }

  private static void readRequests(CollectionFolder rootFolder, Map<String, CollectionFolder> foldersById, JsonNode resources) {
    for (JsonNode resource : resources) {
      if (!"request".equals(resource.path("_type").asText())) {
        continue;
      }
      CollectionFolder parent = parentFolderFor(resource.path("parentId").asText(), rootFolder, foldersById);
      RequestDescriptor request = readRequest(resource, parent.getName());
      if (request != null) {
        parent.addRequest(request);
      }
    }
  }

  private static CollectionFolder parentFolderFor(String parentId, CollectionFolder rootFolder, Map<String, CollectionFolder> foldersById) {
    if (parentId == null || parentId.isBlank()) {
      return rootFolder;
    }
    CollectionFolder folder = foldersById.get(parentId);
    return folder != null ? folder : rootFolder;
  }

  private static RequestDescriptor readRequest(JsonNode resource, String parentFolderName) {
    HttpMethod method = readMethod(resource.path("method").asText("GET"));
    if (method == null) {
      return null;
    }
    String rawUrl = resource.path("url").asText("");
    String normalizedTemplate = PathTemplateNormalizer.canonicalForm(PathTemplateNormalizer.normalizeRequestUrl(rawUrl));

    return new RequestDescriptor(
        resource.path("name").asText(""),
        method,
        rawUrl,
        normalizedTemplate,
        readParams(resource.path("parameters"), ParamLocation.QUERY),
        readParams(resource.path("headers"), ParamLocation.HEADER),
        readBody(resource.path("body")),
        parentFolderName,
        RequestSource.INSOMNIA,
        resource.deepCopy());
  }

  private static HttpMethod readMethod(String methodText) {
    try {
      return HttpMethod.valueOf(methodText.trim().toUpperCase());
    } catch (IllegalArgumentException unknownMethod) {
      return null;
    }
  }

  private static List<ParamDescriptor> readParams(JsonNode paramsNode, ParamLocation location) {
    List<ParamDescriptor> params = new ArrayList<>();
    if (paramsNode.isArray()) {
      for (JsonNode param : paramsNode) {
        String name = param.path("name").asText(null);
        if (name != null && !name.isBlank()) {
          params.add(new ParamDescriptor(name, location, "String", false));
        }
      }
    }
    return params;
  }

  private static JsonNode readBody(JsonNode bodyNode) {
    String text = bodyNode.path("text").asText("");
    if (text.isEmpty()) {
      return null;
    }
    String mimeType = bodyNode.path("mimeType").asText("");
    if (mimeType.contains("json")) {
      try {
        return new ObjectMapper().readTree(text);
      } catch (IOException notJson) {
        return com.fasterxml.jackson.databind.node.TextNode.valueOf(text);
      }
    }
    return com.fasterxml.jackson.databind.node.TextNode.valueOf(text);
  }
}