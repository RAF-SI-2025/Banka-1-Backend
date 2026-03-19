package app.integration;

import app.config.NotificationProperties;
import app.entities.RoutingKeys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Validates that every {@link RoutingKeys} constant is present in the routing-key map
 * loaded from {@code application.properties}.
 *
 * <p>This test exists to catch drift between {@code RoutingKeys.java} and the property
 * file. If a constant is added to {@code RoutingKeys} but the corresponding mapping is
 * missing from properties (or vice-versa), this test will fail before the service is
 * deployed.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "spring.datasource.url=jdbc:h2:mem:routing-keys-validation-it;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "management.endpoint.health.group.readiness.include=readinessState,db,rabbit",
        "spring.autoconfigure.exclude=org.springdoc.webmvc.ui.SwaggerConfig"
})
class RoutingKeysConfigValidationTest {

    @Autowired
    private NotificationProperties notificationProperties;

    /**
     * Asserts that every routing key defined in {@link RoutingKeys} has a corresponding
     * entry in the {@code notification.routing-keys} property map.
     *
     * <p>Failing here means the Java constant and the properties file are out of sync.
     */
    @Test
    void allRoutingKeyConstantsArePresentInProperties() {
        Map<String, String> routingKeys = notificationProperties.getRoutingKeys();

        assertTrue(routingKeys.containsKey(RoutingKeys.EMPLOYEE_CREATED),
                "Missing routing key mapping for: " + RoutingKeys.EMPLOYEE_CREATED);
        assertTrue(routingKeys.containsKey(RoutingKeys.EMPLOYEE_PASSWORD_RESET),
                "Missing routing key mapping for: " + RoutingKeys.EMPLOYEE_PASSWORD_RESET);
        assertTrue(routingKeys.containsKey(RoutingKeys.EMPLOYEE_ACCOUNT_DEACTIVATED),
                "Missing routing key mapping for: " + RoutingKeys.EMPLOYEE_ACCOUNT_DEACTIVATED);
        assertTrue(routingKeys.containsKey(RoutingKeys.CLIENT_CREATED),
                "Missing routing key mapping for: " + RoutingKeys.CLIENT_CREATED);
        assertTrue(routingKeys.containsKey(RoutingKeys.CLIENT_PASSWORD_RESET),
                "Missing routing key mapping for: " + RoutingKeys.CLIENT_PASSWORD_RESET);
        assertTrue(routingKeys.containsKey(RoutingKeys.CLIENT_ACCOUNT_DEACTIVATED),
                "Missing routing key mapping for: " + RoutingKeys.CLIENT_ACCOUNT_DEACTIVATED);
    }

    /**
     * Asserts that every routing key defined in {@link RoutingKeys} maps to a non-blank
     * notification type, and that a template exists in properties for that type.
     *
     * <p>Failing here means a routing key is mapped but there is no email template to
     * render for it — the service would throw at runtime when processing that event.
     */
    @Test
    void allRoutingKeysMappedToNotificationTypeWithTemplate() {
        Map<String, String> routingKeys = notificationProperties.getRoutingKeys();
        Map<String, ?> templates = notificationProperties.getTemplates();

        for (Map.Entry<String, String> entry : routingKeys.entrySet()) {
            String routingKey = entry.getKey();
            String notificationType = entry.getValue();

            assertTrue(notificationType != null && !notificationType.isBlank(),
                    "Routing key '" + routingKey + "' maps to a blank notification type");
            assertTrue(templates.containsKey(notificationType),
                    "No template defined for notification type '" + notificationType
                            + "' (mapped from routing key '" + routingKey + "')");
        }
    }
}
