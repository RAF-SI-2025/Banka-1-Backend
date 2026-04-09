package app.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ configuration for the notification service.
 *
 * <p>This class declares the messaging objects used by Spring AMQP:
 * <ul>
 *     <li>A `TOPIC` exchange that receives employee events</li>
 *     <li>A durable `QUEUE` consumed by this service</li>
 *     <li>A BINDING between exchange and queue using a routing-key pattern</li>
 *     <li>A JSON message converter for request payloads</li>
 * </ul>
 *
 * <p>All names come from application.properties so values can change per environment.
 */
@Configuration
public class RabbitConfig {

    /**
     * Creates the topic exchange where employee events are published.
     *
     * @param exchangeName exchange name from configuration
     * @return durable, non-auto-delete topic exchange
     */
    @Bean
    public TopicExchange employeeEventsExchange(
            @Value("${notification.rabbit.exchange}") String exchangeName
    ) {
        return new TopicExchange(exchangeName, true, false);
    }

    /**
     * Creates the queue consumed by the notification listener.
     *
     * @param queueName queue name from configuration
     * @return durable queue
     */
    @Bean
    public Queue notificationServiceQueue(
            @Value("${notification.rabbit.queue}") String queueName
    ) {
        return new Queue(queueName, true);
    }

    /**
     * Binds the notification queue to the exchange
     * with the configured routing key pattern.
     *
     * <p>Example: {@code employee.*} matches events
     * such as {@code employee.created}.
     *
     * @param notificationServiceQueue queue bean
     * @param employeeEventsExchange exchange bean
     * @param routingKey routing-key pattern from configuration
     * @return exchange-to-queue binding
     */
    @Bean
    public Binding notificationBinding(
            Queue notificationServiceQueue,
            TopicExchange employeeEventsExchange,
            @Value("${notification.rabbit.routing-key}") String routingKey
    ) {
        return BindingBuilder.bind(notificationServiceQueue)
                .to(employeeEventsExchange)
                .with(routingKey);
    }

    /**
     * Binds the notification queue to the exchange for client events.
     *
     * @param notificationServiceQueue queue bean
     * @param employeeEventsExchange   exchange bean
     * @param clientRoutingKey         routing-key pattern from configuration
     * @return exchange-to-queue binding for client routing keys
     */
    @Bean
    public Binding clientNotificationBinding(
            Queue notificationServiceQueue,
            TopicExchange employeeEventsExchange,
            @Value("${notification.rabbit.client-routing-key}") String clientRoutingKey
    ) {
        return BindingBuilder.bind(notificationServiceQueue)
                .to(employeeEventsExchange)
                .with(clientRoutingKey);
    }

    /**
     * Binds the notification queue to the exchange for card events.
     *
     * @param notificationServiceQueue queue bean
     * @param employeeEventsExchange exchange bean
     * @param cardRoutingKey routing-key pattern from configuration
     * @return exchange-to-queue binding for card routing keys
     */
    @Bean
    public Binding cardNotificationBinding(
            Queue notificationServiceQueue,
            TopicExchange employeeEventsExchange,
            @Value("${notification.rabbit.card-routing-key}") String cardRoutingKey
    ) {
        return BindingBuilder.bind(notificationServiceQueue)
                .to(employeeEventsExchange)
                .with(cardRoutingKey);
    }

    /**
     * Binds the notification queue to the exchange for credit events.
     *
     * @param notificationServiceQueue queue bean
     * @param employeeEventsExchange exchange bean
     * @param creditRoutingKey routing-key pattern from configuration
     * @return exchange-to-queue binding for credit routing keys
     */
    @Bean
    public Binding creditNotificationBinding(
            Queue notificationServiceQueue,
            TopicExchange employeeEventsExchange,
            @Value("${notification.rabbit.credit-routing-key}") String creditRoutingKey
    ) {
        return BindingBuilder.bind(notificationServiceQueue)
                .to(employeeEventsExchange)
                .with(creditRoutingKey);
    }

    /**
     * Binds the notification queue to the exchange for order events.
     *
     * @param notificationServiceQueue queue bean
     * @param employeeEventsExchange exchange bean
     * @param orderRoutingKey routing-key pattern from configuration
     * @return exchange-to-queue binding for order routing keys
     */
    @Bean
    public Binding orderNotificationBinding(
            Queue notificationServiceQueue,
            TopicExchange employeeEventsExchange,
            @Value("${notification.rabbit.order-routing-key}") String orderRoutingKey
    ) {
        return BindingBuilder.bind(notificationServiceQueue)
                .to(employeeEventsExchange)
                .with(orderRoutingKey);
    }

    /**
     * Binds the notification queue to the exchange for tax events.
     *
     * @param notificationServiceQueue queue bean
     * @param employeeEventsExchange exchange bean
     * @param taxRoutingKey routing-key pattern from configuration
     * @return exchange-to-queue binding for tax routing keys
     */
    @Bean
    public Binding taxNotificationBinding(
            Queue notificationServiceQueue,
            TopicExchange employeeEventsExchange,
            @Value("${notification.rabbit.tax-routing-key}") String taxRoutingKey
    ) {
        return BindingBuilder.bind(notificationServiceQueue)
                .to(employeeEventsExchange)
                .with(taxRoutingKey);
    }

    /**
     * Converts RabbitMQ JSON payloads ==> to/from ==> Java objects.
     *
     * @return Jackson-based message converter
     */
    @Bean
    public MessageConverter messageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
