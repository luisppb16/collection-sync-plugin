/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */

package com.luisppb16.collectionsync.coverage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.luisppb16.collectionsync.test.PsiTestCase;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

@DisplayName("CoverageService.scanAsync")
class CoverageServiceScanAsyncTest extends PsiTestCase {

  @Test
  @DisplayName(
      "Given a smart project, when a scan is requested asynchronously, then the result is published and the callback runs")
  void publishesResultAndRunsCallback() throws InterruptedException {
    CoverageService service = new CoverageService(getFixture().getProject());
    CountDownLatch callback = new CountDownLatch(1);
    AtomicReference<CoverageService.ScanOutput> published = new AtomicReference<>();

    ApplicationManager.getApplication()
        .invokeAndWait(() ->
            service.scanAsync(() -> {
              published.set(service.lastScan());
              callback.countDown();
            }));

    assertThat(callback.await(30, TimeUnit.SECONDS))
        .as("the asynchronous scan must invoke its callback")
        .isTrue();
    assertThat(published.get()).isNotNull();
    assertThat(service.lastScan()).isNotNull();
  }

  @Test
  @DisplayName("Given an asynchronous scan, when it finishes, then the callback runs exactly once")
  void invokesTheCallbackExactlyOnce() throws InterruptedException {
    CoverageService service = new CoverageService(getFixture().getProject());
    CountDownLatch callback = new CountDownLatch(1);
    AtomicBoolean alreadyRan = new AtomicBoolean(false);
    AtomicBoolean ranTwice = new AtomicBoolean(false);

    ApplicationManager.getApplication()
        .invokeAndWait(() ->
            service.scanAsync(() -> {
              if (!alreadyRan.compareAndSet(false, true)) {
                ranTwice.set(true);
              }
              callback.countDown();
            }));

    assertThat(callback.await(30, TimeUnit.SECONDS)).isTrue();
    assertThat(ranTwice).isFalse();
  }

  @Test
  @DisplayName(
      "Given a dumb project, when scans are requested, then only one deferred scan is registered and the user is notified once")
  void defersScansWhileIndexesAreBuilding() {
    Project project = getFixture().getProject();
    CoverageService service = new CoverageService(project);
    AtomicBoolean callbackRan = new AtomicBoolean(false);
    Runnable callback = () -> callbackRan.set(true);

    ApplicationManager.getApplication()
        .invokeAndWait(() -> {
          DumbService dumbService = mock(DumbService.class);
          when(dumbService.isDumb()).thenReturn(true);
          NotificationGroup group = mock(NotificationGroup.class);
          when(group.createNotification(anyString(), any(NotificationType.class)))
              .thenReturn(mock(Notification.class));
          NotificationGroupManager notificationManager = mock(NotificationGroupManager.class);
          when(notificationManager.getNotificationGroup(anyString())).thenReturn(group);

          try (MockedStatic<DumbService> dumb = mockStatic(DumbService.class);
              MockedStatic<NotificationGroupManager> notifications =
                  mockStatic(NotificationGroupManager.class)) {
            dumb.when(() -> DumbService.getInstance(project)).thenReturn(dumbService);
            notifications
                .when(NotificationGroupManager::getInstance)
                .thenReturn(notificationManager);

            service.scanAsync(callback);
            service.scanAsync(callback);

            ArgumentCaptor<Runnable> deferred = ArgumentCaptor.forClass(Runnable.class);
            verify(dumbService).runWhenSmart(deferred.capture());
            verify(group).createNotification(anyString(), any(NotificationType.class));

            deferred.getValue().run();

            verify(dumbService, times(2)).runWhenSmart(any());
          }
        });

    assertThat(callbackRan).isFalse();
    assertThat(service.lastScan()).isNull();
  }

  @Test
  @DisplayName(
      "Given a scan request outside the EDT, when it is made, then the EDT contract fails fast")
  void rejectsCallsOutsideTheEdt() {
    CoverageService service = new CoverageService(getFixture().getProject());

    assertThatExceptionOfType(AssertionError.class).isThrownBy(() -> service.scanAsync(() -> {}));
  }

  @Test
  @DisplayName("Given a null callback, when a scan is requested, then it fails fast")
  void rejectsNullCallback() {
    CoverageService service = new CoverageService(getFixture().getProject());

    assertThatIllegalArgumentException().isThrownBy(() -> service.scanAsync(null));
  }
}
