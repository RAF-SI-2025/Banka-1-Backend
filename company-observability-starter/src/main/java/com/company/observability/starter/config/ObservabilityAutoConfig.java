package com.company.observability.starter.config;

import com.company.observability.starter.domain.UserIdExtractor;
import com.company.observability.starter.domain.UuidCorrelationIdGenerator;
import com.company.observability.starter.service.*;
import com.company.observability.starter.web.GlobalExceptionHandler;
import com.company.observability.starter.web.filter.CorrelationIdFilter;
import com.company.observability.starter.web.filter.HttpRequestLoggingFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

import java.util.Optional;

@AutoConfiguration
@EnableConfigurationProperties(ObservabilityProperties.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(OncePerRequestFilter.class)
@ConditionalOnProperty(prefix = "company.observability.starter", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ObservabilityAutoConfig {

    @Bean
    @ConditionalOnMissingBean
    public UuidCorrelationIdGenerator uuidCorrelationIdGenerator() {
        return new UuidCorrelationIdGenerator();
    }

    @Bean
    @ConditionalOnMissingBean
    public CorrelationIdService correlationIdService(UuidCorrelationIdGenerator uuidCorrelationIdGenerator) {
        return new CorrelationIdService(uuidCorrelationIdGenerator);
    }

    @Bean
    @ConditionalOnMissingBean(UserIdExtractor.class)
    public UserIdExtractor defaultUserIdExtractor() {
        return Optional::empty;
    }

    @Bean
    @ConditionalOnMissingBean
    public UserIdMdcService userIdMdcService(
            UserIdExtractor userIdExtractor,
            ObservabilityProperties observabilityProperties
    ) {
        return new UserIdMdcService(userIdExtractor, observabilityProperties);
    }


    @Bean
    @ConditionalOnMissingBean
    public CorrelationIdFilter correlationIdFilter(
            CorrelationIdService correlationIdService,
            ObservabilityProperties observabilityProperties,
            UserIdMdcService userIdMdcService
    ) {
        return new CorrelationIdFilter(correlationIdService, observabilityProperties, userIdMdcService);
    }

    @Bean
    public FilterRegistrationBean<CorrelationIdFilter> correlationIdFilterRegistration(
            CorrelationIdFilter correlationIdFilter
    ) {
        FilterRegistrationBean<CorrelationIdFilter> registrationBean =
                new FilterRegistrationBean<>(correlationIdFilter);

        registrationBean.setName("correlationIdFilter");
        registrationBean.addUrlPatterns("/*");
        registrationBean.setOrder(Ordered.HIGHEST_PRECEDENCE);

        return registrationBean;
    }

    @Bean
    @ConditionalOnMissingBean
    public RequestLoggingService requestLoggingService() {
        return new RequestLoggingService();
    }

    @Bean
    @ConditionalOnMissingBean
    public SensitiveDataMaskingService sensitiveDataMaskingService() {
        return new SensitiveDataMaskingService();
    }

    @Bean
    @ConditionalOnMissingBean
    public HttpRequestLoggingFilter httpRequestLoggingFilter(
            RequestLoggingService requestLoggingService, SensitiveDataMaskingService maskingService
    ) {
        return new HttpRequestLoggingFilter(requestLoggingService, maskingService);
    }

    @Bean
    public FilterRegistrationBean<HttpRequestLoggingFilter> httpRequestLoggingFilterRegistration(
            HttpRequestLoggingFilter httpRequestLoggingFilter
    ) {
        FilterRegistrationBean<HttpRequestLoggingFilter> registrationBean =
                new FilterRegistrationBean<>(httpRequestLoggingFilter);

        registrationBean.setName("httpRequestLoggingFilter");
        registrationBean.addUrlPatterns("/*");
        registrationBean.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);

        return registrationBean;
    }

    @Bean
    @ConditionalOnMissingBean
    public ExceptionLoggingService exceptionLoggingService(SensitiveDataMaskingService maskingService) {
        return new ExceptionLoggingService(maskingService);
    }

    @Bean
    @ConditionalOnMissingBean
    public GlobalExceptionHandler globalExceptionHandler(
            ExceptionLoggingService exceptionLoggingService
    ) {
        return new GlobalExceptionHandler(exceptionLoggingService);
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.security.core.Authentication")
    static class SecurityConfig {

        @Bean
        @ConditionalOnMissingBean(UserIdExtractor.class)
        public UserIdExtractor jwtAuthenticationUserIdExtractor() {
            return new JwtAuthenticationUserIdExtract();
        }
    }
}

