/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */
package com.luisppb16.collectionsync.domain.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A folder of a collection tree: a Postman folder or an Insomnia request group. Mutable on
 * purpose: the merge step inserts generated requests into the tree before export.
 */
public final class CollectionFolder {

  private String name;
  private final List<CollectionFolder> children;
  private final List<RequestDescriptor> requests;

  public CollectionFolder(String name) {
    this.name = Objects.requireNonNull(name, "name");
    this.children = new ArrayList<>();
    this.requests = new ArrayList<>();
  }

  public String getName() {
    return name;
  }

  public void rename(String newName) {
    this.name = Objects.requireNonNull(newName, "newName");
  }

  public List<CollectionFolder> getChildren() {
    return children;
  }

  public List<RequestDescriptor> getRequests() {
    return requests;
  }

  /** @return the direct child folder with the given name, or null. */
  public CollectionFolder findChild(String childName) {
    return children.stream().filter(child -> child.getName().equals(childName)).findFirst().orElse(null);
  }

  /** @return the direct child folder with the given name, creating it when absent. */
  public CollectionFolder findOrCreateChild(String childName) {
    CollectionFolder existing = findChild(childName);
    if (existing == null) {
      existing = new CollectionFolder(childName);
      children.add(existing);
    }
    return existing;
  }

  /** @return true when a request with the same method and normalized template already exists here. */
  public boolean containsRequest(HttpMethod method, String normalizedTemplate) {
    return requests.stream().anyMatch(request -> request.method() == method && request.normalizedTemplate().equals(normalizedTemplate));
  }

  public void addRequest(RequestDescriptor request) {
    requests.add(Objects.requireNonNull(request, "request"));
  }
}