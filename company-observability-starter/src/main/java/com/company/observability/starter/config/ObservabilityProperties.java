package com.company.observability.starter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

// Config svojstva za observability biblioteku
@ConfigurationProperties(prefix = "company.observability.starter")
public class ObservabilityProperties {
    private boolean enabled = true;
    private String correlationHeaderName = "X-Correlation-Id";
    private boolean maskingEnabled = true;
    private boolean userIdMdcEnabled = true;
    private String loggingConfigLocation = "classpath:company-observability/logback-spring.xml";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getCorrelationHeaderName() {
        return correlationHeaderName;
    }

    public void setCorrelationHeaderName(String correlationHeaderName) {
        this.correlationHeaderName = correlationHeaderName;
    }

    public boolean isMaskingEnabled() {
        return maskingEnabled;
    }

    public void setMaskingEnabled(boolean maskingEnabled) {
        this.maskingEnabled = maskingEnabled;
    }

    public boolean isUserIdMdcEnabled() {
        return userIdMdcEnabled;
    }

    public void setUserIdMdcEnabled(boolean userIdMdcEnabled) {
        this.userIdMdcEnabled = userIdMdcEnabled;
    }

    public String getLoggingConfigLocation() {
        return loggingConfigLocation;
    }

    public void setLoggingConfigLocation(String loggingConfigLocation) {
        this.loggingConfigLocation = loggingConfigLocation;
    }
}
