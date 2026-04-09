package com.banka1.transaction_service.rabbitMQ;

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * RabbitMQ client for sending email notifications to the message broker.
 * Encapsulates {@link RabbitTemplate} and configured values for exchange and routing key.
 */
@Component
@RequiredArgsConstructor
public class RabbitClient {

    /** Spring AMQP template that performs the actual sending of messages to RabbitMQ */
    private final RabbitTemplate rabbitTemplate;

    /** Name of the RabbitMQ exchange to which messages are sent */
    @Value("${rabbitmq.exchange}")
    private String exchange;

    /**
     * Sends an email notification to the RabbitMQ exchange using the routing key from the message type.
     * <p>
     * The email service will receive the message and process it according to the type (TRANSACTION_COMPLETED or TRANSACTION_DENIED).
     *
     * @param dto the payload of the message with notification details to be forwarded to the email service
     */
    //TODO FIX
    public void sendEmailNotification(EmailDto dto) {
        rabbitTemplate.convertAndSend(exchange, dto.getEmailType().getRoutingKey(), dto);
    }

}
