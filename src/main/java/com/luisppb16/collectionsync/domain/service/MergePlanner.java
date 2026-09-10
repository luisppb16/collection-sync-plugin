/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */
package com.luisppb16.collectionsync.domain.service;

import com.luisppb16.collectionsync.domain.model.CollectionFolder;
import com.luisppb16.collectionsync.domain.model.CollectionModel;
import com.luisppb16.collectionsync.domain.model.RequestDescriptor;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Plans how generated requests are merged into an existing collection: adds only requests whose
 * (method + normalized template) pair is not already present, and places each one in the folder
 * matching its controller, creating the folder when missing.
 */
public final class MergePlanner {

  /** What the merge preview should do with a candidate request. */
  public enum MergeAction {
    ADD,
    SKIP_DUPLICATE
  }

  /** One candidate request of a merge plan. */
  public record MergeEntry(MergeAction action, RequestDescriptor request, String targetFolderPath, boolean newFolder) {
    public MergeEntry {
      request = Objects.requireNonNull(request, "request");
      targetFolderPath = Objects.requireNonNull(targetFolderPath, "targetFolderPath");
    }
  }

  /** The whole plan for the merge preview dialog. */
  public record MergePlan(List<MergeEntry> entries) {
    public MergePlan {
      entries = List.copyOf(entries);
    }
  }

  private MergePlanner() {}

  /**
   * Plans the merge of generated requests into the collection (the collection is not modified;
   * the caller applies the selected entries).
   *
   * @param collection the imported collection
   * @param generated requests generated for missing endpoints; their {@code folderPath} must
   *        contain the owning controller name
   * @return the plan to show in the merge preview
   */
  public static MergePlan plan(CollectionModel collection, List<RequestDescriptor> generated) {
    Objects.requireNonNull(collection, "collection");
    Objects.requireNonNull(generated, "generated");
    List<MergeEntry> entries = new ArrayList<>();
    for (RequestDescriptor request : generated) {
      String folderName = folderName(request);
      boolean existsAnywhere = containsRequest(collection.root(), request);
      boolean folderExists = collection.root().findChild(folderName) != null;
      MergeAction action = existsAnywhere ? MergeAction.SKIP_DUPLICATE : MergeAction.ADD;
      entries.add(new MergeEntry(action, request, folderName, !folderExists));
    }
    return new MergePlan(entries);
  }

  /**
   * Applies the ADD entries of the plan to the collection tree.
   *
   * @param collection the collection to mutate
   * @param plan the plan whose ADD entries are applied
   */
  public static void apply(CollectionModel collection, MergePlan plan) {
    Objects.requireNonNull(collection, "collection");
    Objects.requireNonNull(plan, "plan");
    for (MergeEntry entry : plan.entries()) {
      if (entry.action() == MergeAction.ADD) {
        collection.root().findOrCreateChild(entry.targetFolderPath()).addRequest(entry.request());
      }
    }
  }

  private static boolean containsRequest(CollectionFolder folder, RequestDescriptor request) {
    if (folder.containsRequest(request.method(), request.normalizedTemplate())) {
      return true;
    }
    for (CollectionFolder child : folder.getChildren()) {
      if (containsRequest(child, request)) {
        return true;
      }
    }
    return false;
  }

  private static String folderName(RequestDescriptor request) {
    String folderPath = request.folderPath();
    return folderPath == null || folderPath.isBlank() ? "Generated" : folderPath.trim();
  }
}