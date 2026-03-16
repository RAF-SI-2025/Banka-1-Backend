package app.service;

import app.dto.EmailTemplate;
import app.dto.NotificationRequest;
import app.dto.ResolvedEmail;
import app.entities.NotificationType;
import app.template.NotificationTemplateFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link NotificationContentResolver}.
 *
 * <p>NotificationContentResolver is package-private; this test lives in the same package.
 */
@ExtendWith(MockitoExtension.class)
class NotificationContentResolverUnitTest {

    @Mock
    private NotificationTemplateFactory templateFactory;

    @BeforeEach
    void setUp() {
        lenient().when(templateFactory.resolve(any())).thenReturn(
                new EmailTemplate("Subject", "Hello {{name}}")
        );
    }

    // --- Validation: request and email ---

    @Test
    void resolveThrowsWhenRequestIsNull() {
        assertThrows(IllegalArgumentException.class,
                () -> NotificationContentResolver.resolve(null, NotificationType.EMPLOYEE_CREATED, templateFactory));
    }

    @Test
    void resolveThrowsWhenEmailIsNull() {
        NotificationRequest request = new NotificationRequest("Alice", null, Map.of());
        assertThrows(IllegalArgumentException.class,
                () -> NotificationContentResolver.resolve(request, NotificationType.EMPLOYEE_CREATED, templateFactory));
    }

    @Test
    void resolveThrowsWhenEmailIsBlank() {
        NotificationRequest request = new NotificationRequest("Alice", "   ", Map.of());
        assertThrows(IllegalArgumentException.class,
                () -> NotificationContentResolver.resolve(request, NotificationType.EMPLOYEE_CREATED, templateFactory));
    }



    @Test
    void resolveThrowsWhenNotificationTypeIsNull() {
        NotificationRequest request = new NotificationRequest("Alice", "alice@example.com", Map.of());
        assertThrows(IllegalArgumentException.class,
                () -> NotificationContentResolver.resolve(request, null, templateFactory));
    }

    // --- Happy path rendering ---

    @Test
    void resolveRendersTemplateVariablesIntoBody() {
        NotificationRequest request = new NotificationRequest(
                "Alice", "alice@example.com", Map.of("name", "Alice")
        );
        ResolvedEmail resolved = NotificationContentResolver.resolve(
                request, NotificationType.EMPLOYEE_CREATED, templateFactory
        );

        assertEquals("alice@example.com", resolved.recipientEmail());
        assertEquals("Subject", resolved.subject());
        assertEquals("Hello Alice", resolved.body());
    }

    @Test
    void resolveUsesUsernameAsNameFallbackWhenNameNotInTemplateVariables() {
        NotificationRequest request = new NotificationRequest("Bob", "bob@example.com", new HashMap<>());
        ResolvedEmail resolved = NotificationContentResolver.resolve(
                request, NotificationType.EMPLOYEE_CREATED, templateFactory
        );

        assertEquals("Hello Bob", resolved.body());
    }

    @Test
    void resolveDoesNotOverrideExplicitNameVariableWithUsername() {
        NotificationRequest request = new NotificationRequest(
                "Bob", "bob@example.com", Map.of("name", "Robert")
        );
        ResolvedEmail resolved = NotificationContentResolver.resolve(
                request, NotificationType.EMPLOYEE_CREATED, templateFactory
        );

        assertEquals("Hello Robert", resolved.body());
    }

    @Test
    void resolveHandlesNullTemplateVariablesInRequest() {
        NotificationRequest request = new NotificationRequest("Alice", "alice@example.com", null);
        ResolvedEmail resolved = NotificationContentResolver.resolve(
                request, NotificationType.EMPLOYEE_CREATED, templateFactory
        );

        assertEquals("Hello Alice", resolved.body());
    }

    @Test
    void resolveWithWhitespaceOnlyUsernameAndNoNameVariableLeavesPlaceholderUnreplaced() {
        NotificationRequest request = new NotificationRequest("   ", "alice@example.com", new HashMap<>());
        ResolvedEmail resolved = NotificationContentResolver.resolve(
                request, NotificationType.EMPLOYEE_CREATED, templateFactory
        );

        assertEquals("Hello {{name}}", resolved.body());
    }

    // --- HTML escaping ---

    @Test
    void resolveEscapesScriptTagInTemplateVariable() {
        NotificationRequest request = new NotificationRequest(
                "Alice",
                "alice@example.com",
                Map.of("name", "<script>alert('xss')</script>")
        );
        ResolvedEmail resolved = NotificationContentResolver.resolve(
                request, NotificationType.EMPLOYEE_CREATED, templateFactory
        );

        assertFalse(resolved.body().contains("<script>"));
        assertTrue(resolved.body().contains("&lt;script&gt;"));
    }

    @Test
    void resolveEscapesAmpersandInTemplateVariable() {
        NotificationRequest request = new NotificationRequest(
                "AT&T", "user@example.com", new HashMap<>()
        );
        ResolvedEmail resolved = NotificationContentResolver.resolve(
                request, NotificationType.EMPLOYEE_CREATED, templateFactory
        );

        assertTrue(resolved.body().contains("AT&amp;T"));
        assertFalse(resolved.body().contains("AT&T"));
    }

    @Test
    void resolveEscapesDoubleQuotesInTemplateVariable() {
        NotificationRequest request = new NotificationRequest(
                "Alice", "alice@example.com", Map.of("name", "\"Alice\"")
        );
        ResolvedEmail resolved = NotificationContentResolver.resolve(
                request, NotificationType.EMPLOYEE_CREATED, templateFactory
        );

        assertTrue(resolved.body().contains("&quot;Alice&quot;"));
        assertFalse(resolved.body().contains("\"Alice\""));
    }

    @Test
    void resolveEscapesSingleQuoteInTemplateVariable() {
        NotificationRequest request = new NotificationRequest(
                "Alice", "alice@example.com", Map.of("name", "O'Brien")
        );
        ResolvedEmail resolved = NotificationContentResolver.resolve(
                request, NotificationType.EMPLOYEE_CREATED, templateFactory
        );

        assertTrue(resolved.body().contains("O&#39;Brien"));
        assertFalse(resolved.body().contains("O'Brien"));
    }

    @Test
    void resolveEscapesGreaterThanInTemplateVariable() {
        NotificationRequest request = new NotificationRequest(
                "Alice", "alice@example.com", Map.of("name", "a>b")
        );
        ResolvedEmail resolved = NotificationContentResolver.resolve(
                request, NotificationType.EMPLOYEE_CREATED, templateFactory
        );

        assertTrue(resolved.body().contains("a&gt;b"));
    }
}
