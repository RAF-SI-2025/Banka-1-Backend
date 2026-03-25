package com.banka1.account_service.rabbitMQ;

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Klijent za slanje poruka na RabbitMQ.
 * Enkapsulira {@link RabbitTemplate} i konfigurisane vrednosti exchange-a i routing kljuca.
 */
@Component
@RequiredArgsConstructor
public class RabbitClient {

    /** Spring AMQP template koji obavlja stvarno slanje poruka. */
    private final RabbitTemplate rabbitTemplate;

    /** Naziv RabbitMQ exchange-a na koji se poruke salju. */
    @Value("${rabbitmq.exchange}")
    private String exchange;

    /**
     * Salje email notifikaciju na RabbitMQ exchange koristeci routing key iz tipa poruke.
     *
     * @param dto payload poruke koja se prosledjuje email servisu
     */
    //TODO FIX
    public void sendEmailNotification(EmailDto dto) {
        rabbitTemplate.convertAndSend(exchange, dto.getEmailType().getRoutingKey(), dto);
    }
}
