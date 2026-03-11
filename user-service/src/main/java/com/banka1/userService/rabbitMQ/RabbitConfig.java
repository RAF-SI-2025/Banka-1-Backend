package com.banka1.userService.rabbitMQ;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    @Value("${rabbitmq.queue}")
    private String queueName;

    @Value("${rabbitmq.exchange}")
    private String exchangeName;

    @Value("${rabbitmq.routing-key}")
    private String routingKey;

    @Value("${spring.rabbitmq.host}")
    private String rabbitHost;

    @Value("${spring.rabbitmq.port}")
    private int rabbitPort;

    @Value("${spring.rabbitmq.username}")
    private String rabbitUsername;

    @Value("${spring.rabbitmq.password}")
    private String rabbitPassword;

    /**
     * Kreira RabbitMQ connection factory na osnovu vrednosti iz konfiguracije.
     *
     * @return konekcioni factory za komunikaciju sa RabbitMQ serverom
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
     * Kreira RabbitTemplate i povezuje JSON konverter poruka.
     *
     * @param connectionFactory factory za otvaranje RabbitMQ konekcija
     * @param jacksonMessageConverter konverter objekata u JSON poruke
     * @return konfigurisan RabbitTemplate
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,MessageConverter jacksonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jacksonMessageConverter);
        return template;
    }
    /**
     * Registruje Jackson konverter za serijalizaciju RabbitMQ poruka.
     *
     * @return JSON message converter
     */
    @Bean
    public MessageConverter jacksonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    /**
     * Kreira trajni RabbitMQ queue.
     *
     * @return deklarisani queue
     */
    @Bean
    public Queue queue() {
        return new Queue(queueName, true);
    }

    /**
     * Kreira direct exchange za rutiranje email poruka.
     *
     * @return deklarisani direct exchange
     */
    @Bean
    public DirectExchange directExchange() {
        return new DirectExchange(exchangeName);
    }

    /**
     * Povezuje queue i exchange preko konfigurisanog routing kljuca.
     *
     * @param queue queue koji prima poruke
     * @param directExchange exchange preko kog se poruke rutiraju
     * @return deklarisani binding
     */
    @Bean
    public Binding binding(Queue queue, DirectExchange directExchange) {
        return BindingBuilder.bind(queue).to(directExchange).with(routingKey);
    }
}
