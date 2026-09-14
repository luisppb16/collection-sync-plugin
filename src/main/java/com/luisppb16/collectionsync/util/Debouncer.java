/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */
package com.luisppb16.collectionsync.util;

import com.intellij.openapi.Disposable;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Coalesces bursts of invocations into a single trailing run after the quiet period elapses; used
 * to batch rapid PSI modification events into one invalidation. The executor is a daemon thread
 * pool that shuts down when this debouncer is disposed.
 */
public final class Debouncer implements Disposable {

  private final ScheduledExecutorService executor;
  private final long delayMillis;
  private volatile ScheduledFuture<?> pending;

  public Debouncer(long delayMillis) {
    this.delayMillis = delayMillis;
    this.executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
      Thread thread = new Thread(runnable, "CollectionSync-Debouncer");
      thread.setDaemon(true);
      return thread;
    });
  }

  /** Cancels the pending task (if any) and schedules {@code action} after the quiet period. */
  public void debounce(Runnable action) {
    Objects.requireNonNull(action, "action");
    ScheduledFuture<?> previous = pending;
    if (previous != null) {
      previous.cancel(false);
    }
    pending = executor.schedule(action, delayMillis, TimeUnit.MILLISECONDS);
  }

  @Override
  public void dispose() {
    executor.shutdownNow();
  }
}