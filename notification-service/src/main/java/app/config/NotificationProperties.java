package app.config;

import app.dto.EmailTemplate;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * Configuration properties for notification templates and routing keys.
 *
 * Ucitava vrednosti iz {@code application.properties} i mapira ih u ovaj java objekat.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "notification")
public class NotificationProperties {

    private Map<String, EmailTemplate> templates;
    private Map<String, String> routingKeys;
}
