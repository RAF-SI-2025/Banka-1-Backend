package app.service;

import app.dto.NotificationRequest;
import app.dto.ResolvedEmail;
import app.dto.EmailTemplate;

import app.exception.BusinessException;
import app.exception.ErrorCode;
import app.template.NotificationTemplateFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Map;

import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link NotificationService}.
 *
 * <p>These tests use a mocked {@link JavaMailSender}; no real email is sent.
 * They verify that the service builds outgoing mail correctly and that it delegates content
 * resolution through the configured template factory for supported notification types.
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceUnitTest {

    /**
     * Test email used in assertions and sample payloads.
     */
    private static final String TEST_EMAIL = "dimitrije.tomic99@gmail.com";

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private NotificationTemplateFactory templateFactory;

    @InjectMocks
    private NotificationService notificationService;

    /**
     * Verifies that {@link NotificationService#sendEmail(String, String, String)} builds the
     * expected {@link SimpleMailMessage} contents.
     *
     * <p>This protects the final email shape seen by the recipient, including recipient
     * address, subject, and rendered body text.
     */
    @Test
    void sendEmailBuildsExpectedEmailMessage() {
        notificationService.sendEmail(
                TEST_EMAIL,
                "Activation Email",
                "Zdravo Dimitrije, vas nalog je kreiran. Aktivirajte nalog klikom na link:\nhttps://example.com/activate/123"
        );

        ArgumentCaptor<SimpleMailMessage> messageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(messageCaptor.capture());

        SimpleMailMessage sent = messageCaptor.getValue();
        assertArrayEquals(new String[]{TEST_EMAIL}, sent.getTo());
        assertEquals("Activation Email", sent.getSubject());
        assertEquals("Zdravo Dimitrije, vas nalog je kreiran. Aktivirajte nalog klikom na link:\nhttps://example.com/activate/123", sent.getText());
    }

    /**
     * Verifies that template variables are rendered into the email body for the requested
     * notification type.
     *
     * <p>This ensures the service returns the fully resolved content that downstream delivery
     * logic will persist and send.
     */
    @Test
    void resolveEmailContentRendersTemplateForProvidedNotificationType() {
        NotificationRequest request = new NotificationRequest(
                "Dimitrije",
                TEST_EMAIL,
                Map.of("name", "Dimitrije", "resetLink", "https://example.com/reset/123")
        );
        when(templateFactory.resolve("EMPLOYEE_PASSWORD_RESET")).thenReturn(
                new EmailTemplate(
                        "Password Reset Email",
                        "Zdravo {{name}}, resetujte lozinku klikom na link:\n{{resetLink}}"
                )
        );

        ResolvedEmail resolved = notificationService.resolveEmailContent(
                request,
                "EMPLOYEE_PASSWORD_RESET"
        );

        assertEquals(TEST_EMAIL, resolved.recipientEmail());
        assertEquals("Password Reset Email", resolved.subject());
        assertEquals("Zdravo Dimitrije, resetujte lozinku klikom na link:\nhttps://example.com/reset/123", resolved.body());
    }

    /**
     * Verifies that the new credit-approved template renders the provided placeholders.
     *
     * <p>This protects ISSUE #39 credit decision notifications from missing interpolation.
     */
    @Test
    void resolveEmailContentRendersCreditApprovedTemplateVariables() {
        NotificationRequest request = new NotificationRequest(
                "Milica",
                "milica@example.com",
                Map.of("name", "Milica", "creditId", "77", "approvedAmount", "250000.00")
        );
        when(templateFactory.resolve("CREDIT_APPROVED")).thenReturn(
                new EmailTemplate(
                        "Credit Request Approved",
                        "Zdravo {{name}}, vas kreditni zahtev sa identifikatorom {{creditId}} je odobren. Odobren iznos: {{approvedAmount}}."
                )
        );

        ResolvedEmail resolved = notificationService.resolveEmailContent(request, "CREDIT_APPROVED");

        assertEquals("milica@example.com", resolved.recipientEmail());
        assertEquals("Credit Request Approved", resolved.subject());
        assertEquals("Zdravo Milica, vas kreditni zahtev sa identifikatorom 77 je odobren. Odobren iznos: 250000.00.", resolved.body());
    }

    /**
     * Verifies that the tax-collected template renders the tax-specific placeholders.
     *
     * <p>This protects the latest tax notification rendering path from config-only drift.
     */
    @Test
    void resolveEmailContentRendersTaxCollectedTemplateVariables() {
        NotificationRequest request = new NotificationRequest(
                "Milan",
                "milan@example.com",
                Map.of("name", "Milan", "listingId", "123", "transactionId", "456", "tax", "37.50", "taxRsd", "2925.00")
        );
        when(templateFactory.resolve("TAX_COLLECTED")).thenReturn(
                new EmailTemplate(
                        "Tax Collected",
                        "Zdravo {{name}}, naplacen je porez za listing {{listingId}}. Transakcija: {{transactionId}}, porez: {{tax}}, porez u RSD: {{taxRsd}}."
                )
        );

        ResolvedEmail resolved = notificationService.resolveEmailContent(request, "TAX_COLLECTED");

        assertEquals("milan@example.com", resolved.recipientEmail());
        assertEquals("Tax Collected", resolved.subject());
        assertEquals("Zdravo Milan, naplacen je porez za listing 123. Transakcija: 456, porez: 37.50, porez u RSD: 2925.00.", resolved.body());
    }

    /**
     * Verifies that resolving email content fails when the recipient email is blank.
     *
     * <p>This protects the service from generating deliverable content for an invalid recipient.
     */
    @Test
    void resolveEmailContentFailsWhenUserEmailMissing() {
        NotificationRequest request = new NotificationRequest("Dimitrije", "", Map.of("name", "Dimitrije"));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> notificationService.resolveEmailContent(request, "EMPLOYEE_CREATED")
        );
        assertEquals(ErrorCode.RECIPIENT_EMAIL_REQUIRED, exception.getErrorCode());
    }

    /**
     * Verifies that resolving email content fails when the recipient email is null.
     *
     * <p>This complements the blank-email validation case and ensures missing recipient data is
     * rejected consistently.
     */
    @Test
    void resolveEmailContentFailsWhenUserEmailIsNull() {
        NotificationRequest request = new NotificationRequest("Dimitrije", null, Map.of("name", "Dimitrije"));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> notificationService.resolveEmailContent(request, "EMPLOYEE_CREATED")
        );
        assertEquals(ErrorCode.RECIPIENT_EMAIL_REQUIRED, exception.getErrorCode());
    }

    /**
     * Verifies that a configured sender address is copied into the outgoing mail header.
     *
     * <p>This matters because SMTP configuration often requires a stable {@code From} address,
     * and the service should honor it when present.
     */
    @Test
    void sendEmailWithConfiguredFromAddressSetsFromHeader() {
        ReflectionTestUtils.setField(notificationService, "fromAddress", "sender@example.com");

        notificationService.sendEmail(TEST_EMAIL, "Subject", "Body");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        assertEquals("sender@example.com", captor.getValue().getFrom());
    }

    /**
     * Verifies that no {@code From} header is set when the configured sender address is blank.
     *
     * <p>This prevents the service from writing meaningless empty header values into outgoing
     * messages.
     */
    @Test
    void sendEmailWithBlankFromAddressDoesNotSetFromHeader() {
        ReflectionTestUtils.setField(notificationService, "fromAddress", "");

        notificationService.sendEmail(TEST_EMAIL, "Subject", "Body");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        assertNull(captor.getValue().getFrom());
    }
}
