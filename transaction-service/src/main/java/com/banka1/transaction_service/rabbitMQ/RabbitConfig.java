package com.banka1.transaction_service.rabbitMQ;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
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
 * Configuration class for RabbitMQ.
 * Defines beans for RabbitMQ queues, exchanges, and bindings.
 */
@Configuration
public class RabbitConfig {

    /** Name of the RabbitMQ queue for email messages. */
    @Value("${rabbitmq.queue}")
    private String queueName;

    /** Name of the direct exchange for publishing messages. */
    @Value("${rabbitmq.exchange}")
    private String exchangeName;

    /** Routing key binding the exchange to the queue. */
    @Value("${rabbitmq.routing-key}")
    private String routingKey;

    /** Hostname of the RabbitMQ server. */
    @Value("${spring.rabbitmq.host}")
    private String rabbitHost;

    /** Port of the RabbitMQ server. */
    @Value("${spring.rabbitmq.port}")
    private int rabbitPort;

    /** Username for authenticating with the RabbitMQ server. */
    @Value("${spring.rabbitmq.username}")
    private String rabbitUsername;

    /** Password for authenticating with the RabbitMQ server. */
    @Value("${spring.rabbitmq.password}")
    private String rabbitPassword;

    /**
     * Creates a RabbitMQ connection factory using configuration values.
     *
     * @return the connection factory for communicating with RabbitMQ
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
     * Creates a RabbitTemplate and sets the JSON message converter.
     *
     * @param connectionFactory the factory for opening RabbitMQ connections
     * @param jacksonMessageConverter the converter for objects to JSON messages
     * @return the configured RabbitTemplate
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter jacksonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jacksonMessageConverter);
        return template;
    }

    /**
     * Registers a Jackson converter for serializing RabbitMQ messages to JSON format.
     *
     * @return the JSON message converter
     */
    @Bean
    public MessageConverter jacksonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    /**
     * Creates a RabbitMQ queue.
     *
     * @return the configured queue
     */
    @Bean
    public Queue queue() {
        return new Queue(queueName, true);
    }

    /**
     * Creates a RabbitMQ direct exchange.
     *
     * @return the configured direct exchange
     */
    @Bean
    public TopicExchange topicExchange() {
        return new TopicExchange(exchangeName);
    }

    /**
     * Binds a queue to an exchange with a routing key.
     *
     * @param queue queue that receives messages
     * @param topicExchange exchange through which messages are routed
     * @return declared binding
     */
    @Bean
    public Binding binding(Queue queue, TopicExchange topicExchange) {
        return BindingBuilder.bind(queue).to(topicExchange).with(routingKey);
    }
}
