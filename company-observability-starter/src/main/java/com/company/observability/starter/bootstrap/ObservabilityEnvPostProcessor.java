package com.company.observability.starter.bootstrap;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;
import java.util.Map;

public class ObservabilityEnvPostProcessor implements EnvironmentPostProcessor, Ordered {
    private static final String PROPERTY_SOURCE_NAME = "ObservabilityPropertySource";
    private static final String LOGGING_CONFIG_KEY = "logging.config";
    private static final String DEFAULT_LOGGING_CONFIG = "classpath:company-observability-starter/logback-spring.xml";
    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String existringLoggingConfig = environment.getProperty(LOGGING_CONFIG_KEY);
        if(existringLoggingConfig != null && !existringLoggingConfig.isBlank()) {
            // Ako aplikacija već ima definisan logging.config, ne menjamo ga
            return;
        }
        Map<String, Object> properties = new HashMap<>();
        properties.put(LOGGING_CONFIG_KEY, DEFAULT_LOGGING_CONFIG);
        environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, properties));
    }

    @Override
    public int getOrder(){
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
