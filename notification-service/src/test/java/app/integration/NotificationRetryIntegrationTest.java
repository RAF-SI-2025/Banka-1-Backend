package app.integration;

import app.dto.NotificationRequest;
import app.entities.NotificationDelivery;
import app.entities.NotificationDeliveryStatus;
import app.entities.RoutingKeys;
import app.repository.NotificationDeliveryRepository;
import app.service.NotificationDeliveryService;
import app.service.RetryTaskQueue;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.MailParseException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.TestPropertySource;

import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for retry behavior in {@link NotificationDeliveryService}.
 *
 * <p>This class verifies what happens after the initial notification send fails. It checks
 * persistence state transitions, retry scheduling, repeated processing, and terminal failure
 * behavior using a controlled in-memory mail sender.
 *
 * <p>The goal is to protect the delivery guarantees of notification-service when SMTP delivery
 * is temporarily or repeatedly unavailable.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "spring.datasource.url=jdbc:h2:mem:notification-retry-it;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "notification.retry.delay-seconds=7",
        "management.endpoint.health.group.readiness.include=readinessState,db,rabbit",
        "spring.autoconfigure.exclude=org.springdoc.webmvc.ui.SwaggerConfig"
})
class NotificationRetryIntegrationTest {

    @Autowired
    private NotificationDeliveryService notificationDeliveryService;

    @Autowired
    private NotificationDeliveryRepository notificationDeliveryRepository;

    @Autowired
    private RetryTaskQueue retryTaskQueue;

    @Autowired
    private ControlledMailSender controlledMailSender;

    @BeforeEach
    void setUp() {
        notificationDeliveryRepository.deleteAll();
        controlledMailSender.reset();
    }

    /**
     * Verifies that one retryable send failure is persisted, scheduled, retried, and eventually
     * marked as succeeded.
     *
     * <p>This test protects the core retry path where a transient SMTP error should not lose the
     * notification but should be retried and completed successfully later.
     */
    @Test
    void retryableFailureIsPersistedRetriedAndEventuallySucceeds() {
        controlledMailSender.failNext(new IllegalStateException("SMTP unavailable"));
        NotificationRequest request = new NotificationRequest(
                "Dimitrije",
                "Dimitrije@example.com",
                Map.of("name", "Dimitrije", "activationLink", "https://example.com/activate/123")
        );

        notificationDeliveryService.handleIncomingMessage(request, RoutingKeys.EMPLOYEE_CREATED);

        waitForCondition(Duration.ofSeconds(5), () -> {
            List<NotificationDelivery> deliveries = notificationDeliveryRepository.findAll();
            return deliveries.size() == 1
                    && deliveries.getFirst().getStatus() == NotificationDeliveryStatus.RETRY_SCHEDULED;
        });
        NotificationDelivery scheduled = singleDelivery();
        assertEquals(NotificationDeliveryStatus.RETRY_SCHEDULED, scheduled.getStatus());
        assertEquals(1, scheduled.getAttemptCount());
        assertNotNull(scheduled.getNextAttemptAt());
        assertEquals(7, Duration.between(scheduled.getLastAttemptAt(), scheduled.getNextAttemptAt()).getSeconds());
        assertEquals(1, controlledMailSender.attemptCount());
        assertEquals(0, controlledMailSender.sentCount());

        makeRetryDue(scheduled.getDeliveryId());
        notificationDeliveryService.processDueRetries();

        NotificationDelivery succeeded = deliveryById(scheduled.getDeliveryId());
        assertEquals(NotificationDeliveryStatus.SUCCEEDED, succeeded.getStatus());
        assertEquals(2, succeeded.getAttemptCount());
        assertNotNull(succeeded.getSentAt());
        assertNull(succeeded.getNextAttemptAt());
        assertNull(succeeded.getLastError());
        assertEquals(2, controlledMailSender.attemptCount());
        assertEquals(1, controlledMailSender.sentCount());
    }

    /**
     * Verifies that repeated retryable failures eventually exhaust the retry budget and end in a
     * terminal failed state.
     *
     * <p>This protects the service from retrying forever and confirms that delivery state is
     * finalized cleanly once the configured maximum number of attempts is reached.
     */
    @Test
    void retryableFailuresEventuallyExhaustRetryBudgetAndMarkDeliveryFailed() {
        controlledMailSender.failNext(new IllegalStateException("SMTP unavailable"));
        controlledMailSender.failNext(new IllegalStateException("SMTP unavailable"));
        controlledMailSender.failNext(new IllegalStateException("SMTP unavailable"));
        controlledMailSender.failNext(new IllegalStateException("SMTP unavailable"));

        NotificationRequest request = new NotificationRequest(
                "Dimitrije",
                "dimitrije@example.com",
                Map.of("name", "Dimitrije", "activationLink", "https://example.com/activate/123")
        );

        notificationDeliveryService.handleIncomingMessage(request, RoutingKeys.EMPLOYEE_CREATED);

        waitForCondition(Duration.ofSeconds(5), () -> {
            List<NotificationDelivery> deliveries = notificationDeliveryRepository.findAll();
            return deliveries.size() == 1
                    && deliveries.getFirst().getStatus() == NotificationDeliveryStatus.RETRY_SCHEDULED;
        });
        NotificationDelivery delivery = singleDelivery();
        assertEquals(NotificationDeliveryStatus.RETRY_SCHEDULED, delivery.getStatus());
        assertEquals(1, delivery.getAttemptCount());

        while (delivery.getStatus() == NotificationDeliveryStatus.RETRY_SCHEDULED) {
            makeRetryDue(delivery.getDeliveryId());
            notificationDeliveryService.processDueRetries();
            delivery = deliveryById(delivery.getDeliveryId());
        }

        assertEquals(NotificationDeliveryStatus.FAILED, delivery.getStatus());
        assertEquals(4, delivery.getAttemptCount());
        assertNull(delivery.getNextAttemptAt());
        assertNull(delivery.getSentAt());
        assertTrue(delivery.getLastError().contains("IllegalStateException"));
        assertEquals(4, controlledMailSender.attemptCount());
        assertEquals(0, controlledMailSender.sentCount());
    }

    /**
     * Forces the stored retry timestamp into the past so the retry worker can process it
     * immediately.
     *
     * <p>This helper exists because production retry timing is time-based, but the test needs a
     * deterministic way to trigger the next attempt without waiting for real time to pass.
     */
    private void makeRetryDue(String deliveryId) {
        NotificationDelivery delivery = deliveryById(deliveryId);
        Instant dueNow = Instant.now().minusSeconds(1);
        delivery.setNextAttemptAt(dueNow);
        notificationDeliveryRepository.save(delivery);
        retryTaskQueue.schedule(deliveryId, dueNow);
    }

    /**
     * Returns the only persisted delivery and asserts that exactly one record exists.
     *
     * <p>The retry tests operate on a single notification flow, so multiple records would mean
     * the scenario is no longer controlled.
     */
    private NotificationDelivery singleDelivery() {
        List<NotificationDelivery> deliveries = notificationDeliveryRepository.findAll();
        assertEquals(1, deliveries.size());
        return deliveries.get(0);
    }

    /**
     * Loads a delivery by its internal identifier.
     *
     * <p>This helper keeps the assertions focused on business behavior instead of repository
     * boilerplate.
     */
    private NotificationDelivery deliveryById(String deliveryId) {
        return notificationDeliveryRepository.findByDeliveryId(deliveryId).orElseThrow();
    }

    /**
     * Polls until the expected asynchronous retry state is visible or the timeout expires.
     *
     * <p>The retry flow includes after-commit work and scheduled processing, so a polling helper
     * keeps the integration assertions stable without relying on brittle fixed sleeps.
     *
     * @param timeout maximum time to wait
     * @param condition condition that signals the expected retry state was reached
     */
    private void waitForCondition(Duration timeout, java.util.function.BooleanSupplier condition) {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while waiting for retry processing", ex);
            }
        }
        throw new AssertionError("Condition not met within timeout: " + timeout);
    }

    /**
     * Test configuration that replaces the real SMTP sender with a controllable in-memory fake.
     *
     * <p>This allows the retry flow to be tested inside Spring without depending on an external
     * mail server.
     */
    @TestConfiguration
    static class MailTestConfiguration {
        @Bean
        @Primary
        ControlledMailSender controlledMailSender() {
            return new ControlledMailSender();
        }
    }

    /**
     * In-memory mail sender that can fail selected attempts and record successful sends.
     *
     * <p>The retry tests use this component to simulate transient mail errors in a deterministic
     * way while still exercising the real notification-service retry logic.
     */
    static class ControlledMailSender implements JavaMailSender {
        private final AtomicInteger attempts = new AtomicInteger();
        private final Deque<RuntimeException> failures = new ArrayDeque<>();
        private final CopyOnWriteArrayList<SimpleMailMessage> sentMessages = new CopyOnWriteArrayList<>();

        void failNext(RuntimeException ex) {
            failures.add(ex);
        }

        void reset() {
            attempts.set(0);
            failures.clear();
            sentMessages.clear();
        }

        int attemptCount() {
            return attempts.get();
        }

        int sentCount() {
            return sentMessages.size();
        }

        @Override
        public MimeMessage createMimeMessage() {
            return new MimeMessage(Session.getInstance(new Properties()));
        }

        @Override
        public MimeMessage createMimeMessage(InputStream contentStream) {
            try {
                return new MimeMessage(Session.getInstance(new Properties()), contentStream);
            } catch (Exception ex) {
                throw new MailParseException("Failed to parse mime message", ex);
            }
        }

        @Override
        public void send(MimeMessage mimeMessage) {
            throw new UnsupportedOperationException("MimeMessage sending is not used in these tests");
        }

        @Override
        public void send(MimeMessage... mimeMessages) {
            throw new UnsupportedOperationException("MimeMessage sending is not used in these tests");
        }

        @Override
        public void send(SimpleMailMessage simpleMessage) {
            attempts.incrementAndGet();
            RuntimeException failure = failures.pollFirst();
            if (failure != null) {
                throw failure;
            }
            sentMessages.add(new SimpleMailMessage(simpleMessage));
        }

        @Override
        public void send(SimpleMailMessage... simpleMessages) {
            for (SimpleMailMessage simpleMessage : simpleMessages) {
                send(simpleMessage);
            }
        }
    }
}
