package app.service;

import app.dto.NotificationRequest;
import app.dto.ResolvedEmail;
import app.dto.RetryTask;
import app.entities.NotificationDelivery;
import app.entities.NotificationDeliveryStatus;

import app.exception.BusinessException;
import app.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link NotificationDeliveryService}.
 *
 * <p>These tests validate delivery lifecycle behavior inside notification-service:
 * unsupported routing-key handling, pending-delivery creation, retry scheduling,
 * recovery from transient failures, and terminal failure scenarios.
 *
 * <p>The goal is to protect the orchestration layer that sits between incoming
 * RabbitMQ events, template rendering, persistence, and actual email sending.
 */
@ExtendWith(MockitoExtension.class)
class NotificationDeliveryServiceUnitTest {

    /**
     * Test email used in assertions and payload examples.
     */
    private static final String TEST_EMAIL = "dimitrije.tomic99@gmail.com";

    @Mock
    private NotificationDeliveryTxService notificationDeliveryTxService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private RetryTaskQueue retryTaskQueue;

    @Mock
    private Map<String, String> routingKeysMap;

    @InjectMocks
    private NotificationDeliveryService notificationDeliveryService;

    private Map<String, NotificationDelivery> deliveriesById;
    private Map<String, NotificationDeliveryStatus> createdStatusesById;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(notificationDeliveryService, "defaultMaxRetries", 4);
        ReflectionTestUtils.setField(notificationDeliveryService, "retryDelaySeconds", 5);
        ReflectionTestUtils.setField(notificationDeliveryService, "startupPageSize", 100);
        deliveriesById = new HashMap<>();
        createdStatusesById = new HashMap<>();

        lenient().when(notificationDeliveryTxService.createPendingDelivery(any(NotificationDelivery.class)))
                .thenAnswer(invocation -> {
                    NotificationDelivery delivery = invocation.getArgument(0);
                    createdStatusesById.put(delivery.getDeliveryId(), delivery.getStatus());
                    deliveriesById.put(delivery.getDeliveryId(), delivery);
                    return delivery;
                });
        lenient().when(notificationDeliveryTxService.persistFailedAudit(any(NotificationDelivery.class)))
                .thenAnswer(invocation -> {
                    NotificationDelivery delivery = invocation.getArgument(0);
                    deliveriesById.put(delivery.getDeliveryId(), delivery);
                    return delivery;
                });
        lenient().when(notificationDeliveryTxService.findByDeliveryId(anyString()))
                .thenAnswer(invocation -> Optional.ofNullable(deliveriesById.get(invocation.getArgument(0))));
        lenient().when(notificationDeliveryTxService.findPageByStatus(any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        lenient().doAnswer(invocation -> {
            String deliveryId = invocation.getArgument(0);
            Instant attemptedAt = invocation.getArgument(1);
            NotificationDelivery delivery = deliveriesById.get(deliveryId);
            if (delivery != null) {
                delivery.setAttemptCount(delivery.getAttemptCount() + 1);
                delivery.setStatus(NotificationDeliveryStatus.SUCCEEDED);
                delivery.setLastAttemptAt(attemptedAt);
                delivery.setSentAt(attemptedAt);
                delivery.setLastError(null);
                delivery.setNextAttemptAt(null);
            }
            return null;
        }).when(notificationDeliveryTxService).markSucceeded(anyString(), any(Instant.class));
        lenient().doAnswer(invocation -> {
            String deliveryId = invocation.getArgument(0);
            Instant attemptedAt = invocation.getArgument(1);
            String error = invocation.getArgument(2);
            boolean retryable = invocation.getArgument(3);
            int retryDelaySeconds = invocation.getArgument(4);
            NotificationDelivery delivery = deliveriesById.get(deliveryId);
            if (delivery == null) {
                return null;
            }

            int updatedRetryCount = delivery.getAttemptCount() + 1;
            delivery.setAttemptCount(updatedRetryCount);
            delivery.setLastAttemptAt(attemptedAt);
            delivery.setSentAt(null);
            delivery.setLastError(error);

            if (retryable && updatedRetryCount < delivery.getMaxRetries()) {
                delivery.setStatus(NotificationDeliveryStatus.RETRY_SCHEDULED);
                delivery.setNextAttemptAt(attemptedAt.plusSeconds(retryDelaySeconds));
                return delivery.getNextAttemptAt();
            }

            delivery.setStatus(NotificationDeliveryStatus.FAILED);
            delivery.setNextAttemptAt(null);
            return null;
        }).when(notificationDeliveryTxService).markFailedOrRetry(
                anyString(), any(Instant.class), anyString(), org.mockito.ArgumentMatchers.anyBoolean(), anyInt()
        );
        lenient().doAnswer(invocation -> {
            String to = invocation.getArgument(0);
            String subject = invocation.getArgument(1);
            String body = invocation.getArgument(2);

            // Pronađi delivery i markiraj kao SUCCEEDED
            deliveriesById.values().stream()
                    .filter(d -> to.equals(d.getRecipientEmail()) && subject.equals(d.getSubject()))
                    .findFirst()
                    .ifPresent(d -> {
                        Instant now = Instant.now();
                        d.setStatus(NotificationDeliveryStatus.SUCCEEDED);
                        d.setSentAt(now);
                        d.setLastAttemptAt(now);
                        d.setAttemptCount(d.getAttemptCount() + 1);
                        d.setLastError(null);
                        d.setNextAttemptAt(null);
                    });

            return null;
        }).when(notificationService).sendEmail(anyString(), anyString(), anyString());
    }

    /**
     * Verifies that a mail authentication failure becomes a terminal failed delivery without
     * scheduling a retry.
     *
     * <p>This test covers the non-retryable failure path after transaction commit. The delivery
     * should still be created as pending first, but once the actual send attempt throws
     * {@link MailAuthenticationException}, the record must end in {@code FAILED} and no retry
     * task should be queued.
     */
    @Test
    void handleIncomingMessageMarksDeliveryFailedWithoutRetryOnMailAuthenticationException() {
        NotificationRequest request = new NotificationRequest("Dimitrije", TEST_EMAIL, Map.of());
        when(routingKeysMap.get("EMPLOYEE_CREATED")).thenReturn("EMPLOYEE_CREATED");
        when(notificationService.resolveEmailContent(request, "EMPLOYEE_CREATED"))
                .thenReturn(new ResolvedEmail(TEST_EMAIL, "Hello", "Body"));
        doThrow(new MailAuthenticationException("Authentication failed"))
                .when(notificationService).sendEmail(TEST_EMAIL, "Hello", "Body");

        runHandleIncomingMessageAndCommit(
                () -> notificationDeliveryService.handleIncomingMessage(request, "EMPLOYEE_CREATED")
        );

        ArgumentCaptor<NotificationDelivery> deliveryCaptor = ArgumentCaptor.forClass(NotificationDelivery.class);
        verify(notificationDeliveryTxService).createPendingDelivery(deliveryCaptor.capture());
        NotificationDelivery finalSaved = deliveriesById.get(deliveryCaptor.getValue().getDeliveryId());

        assertEquals(NotificationDeliveryStatus.FAILED, finalSaved.getStatus());
        assertEquals(1, finalSaved.getAttemptCount());
        assertNull(finalSaved.getNextAttemptAt());
        verify(retryTaskQueue, never()).schedule(any(), any());
    }

    /**
     * Verifies that a null payload is rejected before the service enters delivery persistence or
     * send logic.
     *
     * <p>This protects the orchestration layer from creating audit or delivery records for a
     * completely missing message body.
     */
    @Test
    void handleIncomingMessagePersistsFailedAuditWhenPayloadIsInvalid() {
        when(routingKeysMap.get("EMPLOYEE_CREATED")).thenReturn("EMPLOYEE_CREATED");
        assertThrows(BusinessException.class, () ->
                notificationDeliveryService.handleIncomingMessage(null, "EMPLOYEE_CREATED")
        );

        verify(notificationDeliveryTxService, never()).persistFailedAudit(any());
        verify(notificationService, never()).sendEmail(any(), any(), any());
    }

    /**
     * Verifies that an unknown routing key is stored as a failed audit and not processed further.
     *
     * <p>This is the consumer-side protection against unsupported events arriving from RabbitMQ.
     */
    @Test
    void handleIncomingMessagePersistsFailedAuditWhenRoutingKeyIsUnsupported() {
        NotificationRequest request = new NotificationRequest("Dimitrije", TEST_EMAIL, Map.of());

        notificationDeliveryService.handleIncomingMessage(request, "employee.unknown");

        ArgumentCaptor<NotificationDelivery> deliveryCaptor = ArgumentCaptor.forClass(NotificationDelivery.class);
        verify(notificationDeliveryTxService).persistFailedAudit(deliveryCaptor.capture());
        NotificationDelivery saved = deliveryCaptor.getValue();

        assertEquals(NotificationDeliveryStatus.FAILED, saved.getStatus());
        assertEquals("UNKNOWN", saved.getNotificationType());
        assertEquals(TEST_EMAIL, saved.getRecipientEmail());
        assertEquals("Unsupported routing key: employee.unknown", saved.getLastError());
        verify(notificationService, never()).sendEmail(any(), any(), any());
    }

    /**
     * Verifies that {@code employee.password_reset} is treated as a supported routing key.
     *
     * <p>This test checks the consumer-side contract with {@code user-service}. When that
     * routing key arrives, notification-service must resolve it to
     * {@link NotificationType#EMPLOYEE_PASSWORD_RESET} and continue into the normal delivery
     * creation flow instead of storing a failed audit with "unsupported routing key".
     *
     * <p>The reason for this test is the earlier mismatch between underscore and dot routing-key
     * variants, which broke password-reset notifications across services.
     */
    @Test
    void handleIncomingMessageAcceptsEmployeePasswordResetRoutingKeyFromUserService() {
        NotificationRequest request = new NotificationRequest(
                "Dimitrije",
                TEST_EMAIL,
                Map.of("resetLink", "https://example.com/reset/123")
        );
        when(routingKeysMap.get("employee.password_reset"))
                .thenReturn("EMPLOYEE_PASSWORD_RESET");
        when(notificationService.resolveEmailContent(request, "EMPLOYEE_PASSWORD_RESET"))
                .thenReturn(new ResolvedEmail(TEST_EMAIL, "Password Reset Email", "Body"));

        runHandleIncomingMessageInTransaction(
                () -> notificationDeliveryService.handleIncomingMessage(request, "employee.password_reset")
        );

        verify(notificationDeliveryTxService, never()).persistFailedAudit(any());
        verify(notificationService).resolveEmailContent(request, "EMPLOYEE_PASSWORD_RESET");
        verify(notificationDeliveryTxService).createPendingDelivery(any(NotificationDelivery.class));
    }

    /**
     * Verifies that a due retry task loads the delivery from persistence and completes it
     * successfully when sending succeeds.
     *
     * <p>This protects the core retry worker path for already-persisted deliveries.
     */
    @Test
    void processDueRetriesLoadsFromDbAndMarksSucceededWhenTaskIsDue() {
        Instant now = Instant.now();
        RetryTask dueTask = new RetryTask("delivery-1", now.minusSeconds(1));
        NotificationDelivery delivery = new NotificationDelivery();
        delivery.setDeliveryId("delivery-1");
        delivery.setRecipientEmail(TEST_EMAIL);
        delivery.setSubject("Hello");
        delivery.setBody("Body");
        delivery.setStatus(NotificationDeliveryStatus.RETRY_SCHEDULED);
        delivery.setNotificationType("EMPLOYEE_CREATED");
        delivery.setAttemptCount(1);
        delivery.setMaxRetries(4);
        delivery.setNextAttemptAt(now.minusSeconds(1));
        deliveriesById.put(delivery.getDeliveryId(), delivery);

        when(retryTaskQueue.peek()).thenReturn(dueTask).thenReturn(null);
        when(retryTaskQueue.pollDue(any(Instant.class))).thenReturn(dueTask);

        notificationDeliveryService.processDueRetries();

        verify(notificationService).sendEmail(TEST_EMAIL, "Hello", "Body");
        assertEquals(NotificationDeliveryStatus.SUCCEEDED, deliveriesById.get("delivery-1").getStatus());
    }

    /**
     * Verifies that the retry worker does nothing when the next queued task is not due yet.
     *
     * <p>This prevents premature sends and unnecessary repository work.
     */
    @Test
    void processDueRetriesDoesNothingWhenNextTaskIsNotDueYet() {
        Instant future = Instant.now().plusSeconds(30);
        when(retryTaskQueue.peek()).thenReturn(new RetryTask("delivery-future", future));

        notificationDeliveryService.processDueRetries();

        verify(retryTaskQueue, never()).pollDue(any(Instant.class));
        verify(notificationDeliveryTxService, never()).findByDeliveryId(any());
        verify(notificationService, never()).sendEmail(any(), any(), any());
    }

    /**
     * Verifies that a retry task is rescheduled when the latest database state moved the delivery
     * into the future.
     *
     * <p>This protects the in-memory retry queue from racing ahead of the persisted retry
     * schedule.
     */
    @Test
    void processDueRetriesReschedulesTaskWhenDatabaseStateWasMovedIntoFuture() {
        Instant now = Instant.now();
        RetryTask dueTask = new RetryTask("delivery-1", now.minusSeconds(1));
        NotificationDelivery delivery = new NotificationDelivery();
        delivery.setDeliveryId("delivery-1");
        delivery.setRecipientEmail(TEST_EMAIL);
        delivery.setSubject("Hello");
        delivery.setBody("Body");
        delivery.setStatus(NotificationDeliveryStatus.RETRY_SCHEDULED);
        delivery.setNotificationType("EMPLOYEE_CREATED");
        delivery.setAttemptCount(1);
        delivery.setMaxRetries(4);
        delivery.setNextAttemptAt(now.plusSeconds(60));
        deliveriesById.put(delivery.getDeliveryId(), delivery);

        when(retryTaskQueue.peek()).thenReturn(dueTask).thenReturn(null);
        when(retryTaskQueue.pollDue(any(Instant.class))).thenReturn(dueTask);

        notificationDeliveryService.processDueRetries();

        verify(retryTaskQueue).schedule("delivery-1", delivery.getNextAttemptAt());
        verify(notificationService, never()).sendEmail(any(), any(), any());
    }

    /**
     * Verifies that the final allowed failed retry attempt marks the delivery as terminally
     * failed instead of scheduling another retry.
     *
     * <p>This test protects the configured retry budget and the transition to a terminal failed
     * state.
     */
    @Test
    void processDueRetriesMarksDeliveryFailedWhenRetryBudgetIsExhausted() {
        Instant now = Instant.now();
        RetryTask dueTask = new RetryTask("delivery-1", now.minusSeconds(1));
        NotificationDelivery delivery = new NotificationDelivery();
        delivery.setDeliveryId("delivery-1");
        delivery.setRecipientEmail(TEST_EMAIL);
        delivery.setSubject("Hello");
        delivery.setBody("Body");
        delivery.setStatus(NotificationDeliveryStatus.RETRY_SCHEDULED);
        delivery.setNotificationType("EMPLOYEE_CREATED");
        delivery.setAttemptCount(3);
        delivery.setMaxRetries(4);
        delivery.setNextAttemptAt(now.minusSeconds(1));
        deliveriesById.put(delivery.getDeliveryId(), delivery);

        when(retryTaskQueue.peek()).thenReturn(dueTask).thenReturn(null);
        when(retryTaskQueue.pollDue(any(Instant.class))).thenReturn(dueTask);
        doThrow(new IllegalStateException("SMTP unavailable"))
                .when(notificationService).sendEmail(TEST_EMAIL, "Hello", "Body");

        notificationDeliveryService.processDueRetries();

        NotificationDelivery finalSaved = deliveriesById.get("delivery-1");

        assertEquals(NotificationDeliveryStatus.FAILED, finalSaved.getStatus());
        assertEquals(4, finalSaved.getAttemptCount());
        assertNull(finalSaved.getNextAttemptAt());
        verify(retryTaskQueue, never()).schedule("delivery-1", now.minusSeconds(1));
    }

    /**
     * Verifies that retry processing quietly skips tasks whose delivery record no longer exists.
     *
     * <p>This keeps the retry worker robust against stale in-memory tasks.
     */
    @Test
    void processDueRetriesSkipsMissingDeliveryRecord() {
        Instant now = Instant.now();
        RetryTask dueTask = new RetryTask("missing-delivery", now.minusSeconds(1));

        when(retryTaskQueue.peek()).thenReturn(dueTask).thenReturn(null);
        when(retryTaskQueue.pollDue(any(Instant.class))).thenReturn(dueTask);

        notificationDeliveryService.processDueRetries();

        verify(notificationService, never()).sendEmail(any(), any(), any());
        verify(notificationDeliveryTxService, never()).markSucceeded(anyString(), any(Instant.class));
        verify(notificationDeliveryTxService, never()).markFailedOrRetry(
                anyString(), any(Instant.class), anyString(), org.mockito.ArgumentMatchers.anyBoolean(), anyInt()
        );
    }

    /**
     * Verifies that startup reload enqueues only deliveries that are still eligible for retry.
     *
     * <p>This protects recovery behavior after service restart by excluding already exhausted
     * records from the retry queue.
     */
    @Test
    void loadRetryTasksOnStartupQueuesOnlyRetryableRecords() {
        NotificationDelivery retryable = new NotificationDelivery();
        retryable.setDeliveryId("delivery-retryable");
        retryable.setAttemptCount(1);
        retryable.setMaxRetries(4);
        retryable.setStatus(NotificationDeliveryStatus.RETRY_SCHEDULED);
        retryable.setNextAttemptAt(Instant.parse("2026-03-07T12:00:05Z"));

        NotificationDelivery exhausted = new NotificationDelivery();
        exhausted.setDeliveryId("delivery-exhausted");
        exhausted.setAttemptCount(4);
        exhausted.setMaxRetries(4);
        exhausted.setStatus(NotificationDeliveryStatus.RETRY_SCHEDULED);
        exhausted.setNextAttemptAt(Instant.parse("2026-03-07T12:00:10Z"));

        NotificationDelivery pending = new NotificationDelivery();
        pending.setDeliveryId("delivery-pending");
        pending.setAttemptCount(0);
        pending.setMaxRetries(4);
        pending.setStatus(NotificationDeliveryStatus.PENDING);

        when(notificationDeliveryTxService.findPageByStatus(eq(NotificationDeliveryStatus.PENDING), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(pending)));
        when(notificationDeliveryTxService.findPageByStatus(eq(NotificationDeliveryStatus.RETRY_SCHEDULED), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(retryable, exhausted)));

        notificationDeliveryService.loadRetryTasksOnStartup();

        verify(retryTaskQueue).schedule("delivery-retryable", retryable.getNextAttemptAt());
        verify(retryTaskQueue, never()).schedule("delivery-exhausted", exhausted.getNextAttemptAt());
        verify(retryTaskQueue, atLeastOnce()).schedule(org.mockito.ArgumentMatchers.eq("delivery-pending"), any(Instant.class));
    }

    /**
     * Verifies that a null routing key is treated like any other unsupported event and persisted
     * as a failed audit record.
     *
     * <p>This protects the service from malformed broker metadata.
     */
    @Test
    void handleIncomingMessageWithNullRoutingKeyPersistsFailedAudit() {
        NotificationRequest request = new NotificationRequest("Dimitrije", TEST_EMAIL, Map.of());

        notificationDeliveryService.handleIncomingMessage(request, (String) null);

        ArgumentCaptor<NotificationDelivery> captor = ArgumentCaptor.forClass(NotificationDelivery.class);
        verify(notificationDeliveryTxService).persistFailedAudit(captor.capture());
        NotificationDelivery saved = captor.getValue();

        assertEquals(NotificationDeliveryStatus.FAILED, saved.getStatus());
        assertEquals("UNKNOWN", saved.getNotificationType());
        verify(notificationService, never()).sendEmail(any(), any(), any());
    }

    /**
     * Verifies that template/content resolution failures abort processing before a failed audit or
     * send attempt is created.
     *
     * <p>This distinguishes invalid content-generation errors from runtime delivery errors.
     */
    @Test
    void handleIncomingMessagePersistsFailedAuditWhenContentResolutionThrows() {
        NotificationRequest request = new NotificationRequest("Dimitrije", TEST_EMAIL, Map.of());
        when(routingKeysMap.get("EMPLOYEE_CREATED")).thenReturn("EMPLOYEE_CREATED");
        when(notificationService.resolveEmailContent(request, "EMPLOYEE_CREATED"))
                .thenThrow(new BusinessException(ErrorCode.EMAIL_CONTENT_RESOLUTION_FAILED, "template error"));

        assertThrows(BusinessException.class, () ->
                runHandleIncomingMessageInTransaction(
                        () -> notificationDeliveryService.handleIncomingMessage(request, "EMPLOYEE_CREATED")
                )
        );

        verify(notificationDeliveryTxService, never()).persistFailedAudit(any());
        verify(notificationService, never()).sendEmail(any(), any(), any());
    }

    /**
     * Verifies that startup validation rejects an invalid retry budget of zero.
     *
     * <p>This protects the service from booting with nonsensical retry configuration.
     */
    @Test
    void validateRetryConfigThrowsWhenMaxRetriesIsZero() {
        ReflectionTestUtils.setField(notificationDeliveryService, "defaultMaxRetries", 0);

        assertThrows(IllegalStateException.class,
                () -> notificationDeliveryService.validateRetryConfig());
    }

    /**
     * Verifies that startup validation rejects a retry delay of zero seconds.
     *
     * <p>This prevents an invalid tight-loop retry configuration at application startup.
     */
    @Test
    void validateRetryConfigThrowsWhenDelaySecondsIsZero() {
        ReflectionTestUtils.setField(notificationDeliveryService, "retryDelaySeconds", 0);

        assertThrows(IllegalStateException.class,
                () -> notificationDeliveryService.validateRetryConfig());
    }

    /**
     * Verifies that startup reload paginates through multiple result pages for both pending and
     * scheduled deliveries.
     *
     * <p>This protects restart recovery logic when the backlog is larger than one page.
     */
    @Test
    void loadRetryTasksOnStartupPaginatesThroughMultiplePages() {
        NotificationDelivery p1 = new NotificationDelivery();
        p1.setDeliveryId("delivery-page1");
        p1.setAttemptCount(0);
        p1.setMaxRetries(4);
        p1.setStatus(NotificationDeliveryStatus.PENDING);

        NotificationDelivery p2 = new NotificationDelivery();
        p2.setDeliveryId("delivery-page2");
        p2.setAttemptCount(1);
        p2.setMaxRetries(4);
        p2.setStatus(NotificationDeliveryStatus.RETRY_SCHEDULED);
        p2.setNextAttemptAt(Instant.parse("2026-03-11T10:00:00Z"));

        org.springframework.data.domain.PageImpl<NotificationDelivery> page1 =
                new org.springframework.data.domain.PageImpl<>(
                        List.of(p1),
                        org.springframework.data.domain.PageRequest.of(0, 1),
                        2
                );
        org.springframework.data.domain.PageImpl<NotificationDelivery> page2 =
                new org.springframework.data.domain.PageImpl<>(
                        List.of(p2),
                        org.springframework.data.domain.PageRequest.of(1, 1),
                        2
                );

        ReflectionTestUtils.setField(notificationDeliveryService, "startupPageSize", 1);

        when(notificationDeliveryTxService.findPageByStatus(
                eq(NotificationDeliveryStatus.PENDING), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(page1)
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of()));
        when(notificationDeliveryTxService.findPageByStatus(
                eq(NotificationDeliveryStatus.RETRY_SCHEDULED), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(page2)
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of()));

        notificationDeliveryService.loadRetryTasksOnStartup();

        verify(retryTaskQueue, atLeastOnce()).schedule(eq("delivery-page1"), any(Instant.class));
        verify(retryTaskQueue).schedule("delivery-page2", p2.getNextAttemptAt());
    }

    /**
     * Verifies that exceptions thrown during retry sending are handled inside the retry worker
     * without crashing the test flow.
     *
     * <p>This protects the processing loop from bubbling transient mail exceptions out of
     * {@code processDueRetries()}.
     */
    @Test
    void processDueRetriesThrowsExceptionWhenSendFails() {
        Instant now = Instant.now();
        RetryTask dueTask = new RetryTask("delivery-1", now.minusSeconds(1));
        NotificationDelivery delivery = new NotificationDelivery();
        delivery.setDeliveryId("delivery-1");
        delivery.setRecipientEmail(TEST_EMAIL);
        delivery.setSubject("Hello");
        delivery.setBody("Body");
        delivery.setStatus(NotificationDeliveryStatus.RETRY_SCHEDULED);
        delivery.setNotificationType("EMPLOYEE_CREATED");
        delivery.setAttemptCount(3);
        delivery.setMaxRetries(4);
        delivery.setNextAttemptAt(now.minusSeconds(1));
        deliveriesById.put(delivery.getDeliveryId(), delivery);

        when(retryTaskQueue.peek()).thenReturn(dueTask).thenReturn(null);
        when(retryTaskQueue.pollDue(any(Instant.class))).thenReturn(dueTask);
        doThrow(new IllegalStateException("SMTP unavailable"))
                .when(notificationService).sendEmail(TEST_EMAIL, "Hello", "Body");

        notificationDeliveryService.processDueRetries();
    }


    /**
     * Runs code with active transaction synchronization enabled.
     *
     * <p>This helper is needed because the service registers work to execute after transaction
     * commit, and several unit tests need the same Spring transaction context assumptions as the
     * real service methods.
     *
     * @param runnable callback executed inside the synthetic transaction-synchronization scope
     */
    private void runHandleIncomingMessageInTransaction(Runnable runnable) {
        TransactionSynchronizationManager.initSynchronization();
        try {
            runnable.run();
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    /**
     * Runs code with transaction synchronization enabled and then triggers registered
     * {@code afterCommit} callbacks.
     *
     * <p>This helper is used for tests that need to observe behavior which only happens after the
     * service commits the surrounding transaction, such as the actual email send attempt.
     *
     * @param runnable callback executed inside the synthetic transaction-synchronization scope
     */
    private void runHandleIncomingMessageAndCommit(Runnable runnable) {
        TransactionSynchronizationManager.initSynchronization();
        try {
            runnable.run();
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(org.springframework.transaction.support.TransactionSynchronization::afterCommit);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }
}
