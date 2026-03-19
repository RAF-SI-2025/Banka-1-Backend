package app.integration;

import app.dto.NotificationRequest;
import app.entities.NotificationDelivery;
import app.entities.NotificationDeliveryStatus;

import app.entities.RoutingKeys;
import app.repository.NotificationDeliveryRepository;
import app.service.NotificationDeliveryService;
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
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for the successful end-to-end employee notification flow.
 *
 * <p>This class verifies the behavior of notification-service after a supported employee
 * event reaches the service. The tests cover routing-key resolution, template rendering,
 * persistence of delivery metadata, and the final email content that would be sent to the
 * recipient.
 *
 * <p>The main reason for these tests is to protect the integration contract with upstream
 * publishers. If routing keys, templates, or delivery state transitions drift, these tests
 * should fail before that change reaches production.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "spring.datasource.url=jdbc:h2:mem:notification-flow-it;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "management.endpoint.health.group.readiness.include=readinessState,db,rabbit",
        "spring.autoconfigure.exclude=org.springdoc.webmvc.ui.SwaggerConfig"
})
class NotificationDeliveryFlowIntegrationTest {

    private static final String TEST_EMAIL = "andrija.milikic2203@gmail.com";

    @Autowired
    private NotificationDeliveryService notificationDeliveryService;

    @Autowired
    private NotificationDeliveryRepository notificationDeliveryRepository;

    @Autowired
    private RecordingMailSender recordingMailSender;

    @BeforeEach
    void setUp() {
        notificationDeliveryRepository.deleteAll();
        recordingMailSender.reset();
    }

    /**
     * Tests the complete happy-path flow for the employee activation notification.
     *
     * <p>The test verifies that an {@code employee.created} event is mapped to
     * {@code EMPLOYEE_CREATED}, persisted as a successful delivery, and
     * rendered into the exact activation email subject/body expected by the recipient.
     *
     * <p>This is important because account activation is the primary onboarding path and must
     * continue working even when routing-key or template configuration changes.
     */
    @Test
    void employeeCreatedEventPersistsSucceededDeliveryAndSendsActivationEmail() {
        NotificationRequest request = new NotificationRequest(
                "Andrija",
                TEST_EMAIL,
                Map.of(
                        "name", "Andrija",
                        "activationLink", "https://example.com/activate/123"
                )
        );

        notificationDeliveryService.handleIncomingMessage(request, RoutingKeys.EMPLOYEE_CREATED);

        NotificationDelivery delivery = waitForSucceededDelivery();
        SimpleMailMessage sentMessage = recordingMailSender.singleSentMessage();

        assertEquals("EMPLOYEE_CREATED", delivery.getNotificationType());
        assertEquals(TEST_EMAIL, delivery.getRecipientEmail());
        assertEquals("Activation Email", delivery.getSubject());
        assertEquals(
                "Zdravo Andrija, vas nalog je kreiran. Aktivirajte nalog klikom na link:\nhttps://example.com/activate/123",
                delivery.getBody()
        );
        assertEquals(NotificationDeliveryStatus.SUCCEEDED, delivery.getStatus());
        assertEquals(1, delivery.getAttemptCount());
        assertNotNull(delivery.getSentAt());
        assertNull(delivery.getNextAttemptAt());
        assertNull(delivery.getLastError());

        assertArrayEquals(new String[]{TEST_EMAIL}, sentMessage.getTo());
        assertEquals("Activation Email", sentMessage.getSubject());
        assertEquals(
                "Zdravo Andrija, vas nalog je kreiran. Aktivirajte nalog klikom na link:\nhttps://example.com/activate/123",
                sentMessage.getText()
        );
    }

    /**
     * Tests the complete happy-path flow for the employee password-reset notification.
     *
     * <p>The test verifies that the routing key used by {@code user-service},
     * {@code employee.password_reset}, resolves to
     * {@code EMPLOYEE_PASSWORD_RESET}, produces a successful delivery record,
     * and sends the final rendered reset email with the expected link.
     *
     * <p>This test exists because password reset was one of the exact notification types that
     * broke when the routing-key contract diverged between services.
     */
    @Test
    void employeePasswordResetEventPersistsSucceededDeliveryAndSendsResetEmail() {
        NotificationRequest request = new NotificationRequest(
                "Andrija",
                TEST_EMAIL,
                Map.of(
                        "name", "Andrija",
                        "resetLink", "https://example.com/reset/123"
                )
        );

        notificationDeliveryService.handleIncomingMessage(request, RoutingKeys.EMPLOYEE_PASSWORD_RESET);

        NotificationDelivery delivery = waitForSucceededDelivery();
        SimpleMailMessage sentMessage = recordingMailSender.singleSentMessage();

        assertEquals("EMPLOYEE_PASSWORD_RESET", delivery.getNotificationType());
        assertEquals(TEST_EMAIL, delivery.getRecipientEmail());
        assertEquals("Password Reset Email", delivery.getSubject());
        assertEquals(
                "Zdravo Andrija, resetujte lozinku klikom na link:\nhttps://example.com/reset/123",
                delivery.getBody()
        );
        assertEquals(NotificationDeliveryStatus.SUCCEEDED, delivery.getStatus());
        assertEquals(1, delivery.getAttemptCount());
        assertNotNull(delivery.getSentAt());
        assertNull(delivery.getNextAttemptAt());
        assertNull(delivery.getLastError());

        assertArrayEquals(new String[]{TEST_EMAIL}, sentMessage.getTo());
        assertEquals("Password Reset Email", sentMessage.getSubject());
        assertEquals(
                "Zdravo Andrija, resetujte lozinku klikom na link:\nhttps://example.com/reset/123",
                sentMessage.getText()
        );
    }

    /**
     * Tests the complete happy-path flow for the employee account-deactivation notification.
     *
     * <p>The test verifies that an {@code employee.account_deactivated} event resolves to
     * {@code EMPLOYEE_ACCOUNT_DEACTIVATED}, is stored as a successful
     * notification delivery, and generates the expected deactivation email text.
     *
     * <p>The purpose of this test is to ensure that all three supported employee notification
     * types are covered, not just activation and reset flows.
     */
    @Test
    void employeeAccountDeactivatedEventPersistsSucceededDeliveryAndSendsDeactivationEmail() {
        NotificationRequest request = new NotificationRequest(
                "Andrija",
                TEST_EMAIL,
                Map.of("name", "Andrija")
        );

        notificationDeliveryService.handleIncomingMessage(request, RoutingKeys.EMPLOYEE_ACCOUNT_DEACTIVATED);

        NotificationDelivery delivery = waitForSucceededDelivery();
        SimpleMailMessage sentMessage = recordingMailSender.singleSentMessage();

        assertEquals("EMPLOYEE_ACCOUNT_DEACTIVATED", delivery.getNotificationType());
        assertEquals(TEST_EMAIL, delivery.getRecipientEmail());
        assertEquals("Account Deactivation Email", delivery.getSubject());
        assertEquals("Zdravo Andrija, vas nalog je deaktiviran.", delivery.getBody());
        assertEquals(NotificationDeliveryStatus.SUCCEEDED, delivery.getStatus());
        assertEquals(1, delivery.getAttemptCount());
        assertNotNull(delivery.getSentAt());
        assertNull(delivery.getNextAttemptAt());
        assertNull(delivery.getLastError());

        assertArrayEquals(new String[]{TEST_EMAIL}, sentMessage.getTo());
        assertEquals("Account Deactivation Email", sentMessage.getSubject());
        assertEquals("Zdravo Andrija, vas nalog je deaktiviran.", sentMessage.getText());
    }

    /**
     * Tests the complete happy-path flow for the client account-creation notification.
     *
     * <p>Verifies that a {@code client.created} event resolves to {@code CLIENT_CREATED},
     * persists as a succeeded delivery, and renders the expected activation email body.
     */
    @Test
    void clientCreatedEventPersistsSucceededDeliveryAndSendsActivationEmail() {
        NotificationRequest request = new NotificationRequest(
                "Marko",
                TEST_EMAIL,
                Map.of(
                        "name", "Marko",
                        "activationLink", "https://example.com/activate/456"
                )
        );

        notificationDeliveryService.handleIncomingMessage(request, RoutingKeys.CLIENT_CREATED);

        NotificationDelivery delivery = waitForSucceededDelivery();
        SimpleMailMessage sentMessage = recordingMailSender.singleSentMessage();

        assertEquals("CLIENT_CREATED", delivery.getNotificationType());
        assertEquals(TEST_EMAIL, delivery.getRecipientEmail());
        assertEquals("Client Account Activation Email", delivery.getSubject());
        assertEquals(
                "Zdravo Marko, vas klijentski nalog je kreiran. Aktivirajte nalog klikom na link:\nhttps://example.com/activate/456",
                delivery.getBody()
        );
        assertEquals(NotificationDeliveryStatus.SUCCEEDED, delivery.getStatus());
        assertEquals(1, delivery.getAttemptCount());
        assertNotNull(delivery.getSentAt());
        assertNull(delivery.getNextAttemptAt());
        assertNull(delivery.getLastError());

        assertArrayEquals(new String[]{TEST_EMAIL}, sentMessage.getTo());
        assertEquals("Client Account Activation Email", sentMessage.getSubject());
    }

    /**
     * Tests the complete happy-path flow for the client password-reset notification.
     *
     * <p>Verifies that a {@code client.password_reset} event resolves to
     * {@code CLIENT_PASSWORD_RESET} and sends the correct reset email.
     */
    @Test
    void clientPasswordResetEventPersistsSucceededDeliveryAndSendsResetEmail() {
        NotificationRequest request = new NotificationRequest(
                "Marko",
                TEST_EMAIL,
                Map.of(
                        "name", "Marko",
                        "resetLink", "https://example.com/reset/456"
                )
        );

        notificationDeliveryService.handleIncomingMessage(request, RoutingKeys.CLIENT_PASSWORD_RESET);

        NotificationDelivery delivery = waitForSucceededDelivery();
        SimpleMailMessage sentMessage = recordingMailSender.singleSentMessage();

        assertEquals("CLIENT_PASSWORD_RESET", delivery.getNotificationType());
        assertEquals(TEST_EMAIL, delivery.getRecipientEmail());
        assertEquals("Client Password Reset Email", delivery.getSubject());
        assertEquals(
                "Zdravo Marko, resetujte lozinku klikom na link:\nhttps://example.com/reset/456",
                delivery.getBody()
        );
        assertEquals(NotificationDeliveryStatus.SUCCEEDED, delivery.getStatus());
        assertEquals(1, delivery.getAttemptCount());
        assertNotNull(delivery.getSentAt());
        assertNull(delivery.getNextAttemptAt());
        assertNull(delivery.getLastError());

        assertArrayEquals(new String[]{TEST_EMAIL}, sentMessage.getTo());
        assertEquals("Client Password Reset Email", sentMessage.getSubject());
    }

    /**
     * Tests the complete happy-path flow for the client account-deactivation notification.
     *
     * <p>Verifies that a {@code client.account_deactivated} event resolves to
     * {@code CLIENT_ACCOUNT_DEACTIVATED} and sends the correct deactivation email.
     */
    @Test
    void clientAccountDeactivatedEventPersistsSucceededDeliveryAndSendsDeactivationEmail() {
        NotificationRequest request = new NotificationRequest(
                "Marko",
                TEST_EMAIL,
                Map.of("name", "Marko")
        );

        notificationDeliveryService.handleIncomingMessage(request, RoutingKeys.CLIENT_ACCOUNT_DEACTIVATED);

        NotificationDelivery delivery = waitForSucceededDelivery();
        SimpleMailMessage sentMessage = recordingMailSender.singleSentMessage();

        assertEquals("CLIENT_ACCOUNT_DEACTIVATED", delivery.getNotificationType());
        assertEquals(TEST_EMAIL, delivery.getRecipientEmail());
        assertEquals("Client Account Deactivation Email", delivery.getSubject());
        assertEquals("Zdravo Marko, vas klijentski nalog je deaktiviran.", delivery.getBody());
        assertEquals(NotificationDeliveryStatus.SUCCEEDED, delivery.getStatus());
        assertEquals(1, delivery.getAttemptCount());
        assertNotNull(delivery.getSentAt());
        assertNull(delivery.getNextAttemptAt());
        assertNull(delivery.getLastError());

        assertArrayEquals(new String[]{TEST_EMAIL}, sentMessage.getTo());
        assertEquals("Client Account Deactivation Email", sentMessage.getSubject());
    }

    /**
     * Waits until post-commit processing finishes and returns the resulting delivery record.
     *
     * <p>The notification flow sends email only after transaction commit, so assertions cannot
     * be made immediately after calling {@code handleIncomingMessage}. This helper waits until
     * the database record reaches {@code SUCCEEDED} and one email has been captured.
     *
     * @return the single successful delivery persisted by the service
     */
    private NotificationDelivery waitForSucceededDelivery() {
        waitForCondition(Duration.ofSeconds(5), () -> {
            List<NotificationDelivery> deliveries = notificationDeliveryRepository.findAll();
            return deliveries.size() == 1
                    && deliveries.getFirst().getStatus() == NotificationDeliveryStatus.SUCCEEDED
                    && recordingMailSender.sentCount() == 1;
        });

        List<NotificationDelivery> deliveries = notificationDeliveryRepository.findAll();
        assertEquals(1, deliveries.size());
        return deliveries.getFirst();
    }

    /**
     * Polls until a condition becomes true or the timeout expires.
     *
     * <p>This helper keeps the integration tests deterministic while still allowing the
     * service to complete asynchronous post-commit work naturally.
     *
     * @param timeout maximum time to wait
     * @param condition condition that indicates the expected behavior completed
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
                throw new AssertionError("Interrupted while waiting for notification delivery", ex);
            }
        }
        throw new AssertionError("Condition not met within timeout: " + timeout);
    }

    /**
     * Test configuration that replaces the real mail sender with an in-memory recorder.
     *
     * <p>This allows the tests to stay integration-oriented inside the service boundary while
     * avoiding external SMTP dependencies.
     */
    @TestConfiguration
    static class MailTestConfiguration {
        @Bean
        @Primary
        RecordingMailSender recordingMailSender() {
            return new RecordingMailSender();
        }
    }

    /**
     * In-memory {@link JavaMailSender} implementation used to capture sent messages.
     *
     * <p>The service still executes its normal delivery flow, but instead of contacting a real
     * SMTP server, sent messages are stored in memory so the tests can assert recipient,
     * subject, and body exactly.
     */
    static class RecordingMailSender implements JavaMailSender {
        private final CopyOnWriteArrayList<SimpleMailMessage> sentMessages = new CopyOnWriteArrayList<>();

        void reset() {
            sentMessages.clear();
        }

        int sentCount() {
            return sentMessages.size();
        }

        SimpleMailMessage singleSentMessage() {
            assertEquals(1, sentMessages.size());
            return sentMessages.getFirst();
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
