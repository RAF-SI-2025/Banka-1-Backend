package com.banka1.order.rabbitmq;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ infrastructure configuration.
 * Sets up the connection factory, message template, JSON converter,
 * and the topic exchange used for order and tax notifications.
 */
@Configuration
public class RabbitConfig {

    @Value("${rabbitmq.exchange}")
    private String exchangeName;

    @Value("${spring.rabbitmq.host}")
    private String rabbitHost;

    @Value("${spring.rabbitmq.port}")
    private int rabbitPort;

    @Value("${spring.rabbitmq.username}")
    private String rabbitUsername;

    @Value("${spring.rabbitmq.password}")
    private String rabbitPassword;

    /**
     * Creates a connection factory using the RabbitMQ host and credentials from properties.
     *
     * @return configured {@link CachingConnectionFactory}
     */
    @Bean
    public ConnectionFactory connectionFactory() {
        CachingConnectionFactory connectionFactory = new CachingConnectionFactory(rabbitHost);
        connectionFactory.setPort(rabbitPort);
        connectionFactory.setUsername(rabbitUsername);
        connectionFactory.setPassword(rabbitPassword);
        return connectionFactory;
    }

    /**
     * Creates a {@link RabbitTemplate} with JSON message conversion enabled.
     *
     * @param connectionFactory the connection factory
     * @param jacksonMessageConverter the JSON converter
     * @return configured RabbitTemplate
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter jacksonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jacksonMessageConverter);
        return template;
    }

    /**
     * Jackson-based message converter that serializes DTOs to JSON automatically.
     *
     * @return {@link JacksonJsonMessageConverter}
     */
    @Bean
    public MessageConverter jacksonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    /**
     * Declares the topic exchange used for order and tax event routing.
     *
     * @return the configured {@link TopicExchange}
     */
    @Bean
    public TopicExchange orderExchange() {
        return new TopicExchange(exchangeName);
    }
}
