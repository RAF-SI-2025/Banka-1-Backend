package com.banka1.userService.rabbitMQ;

import com.banka1.userService.dto.rabbitmq.EmailDto;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RabbitClient {
    private final RabbitTemplate rabbitTemplate;
    @Value("${rabbitmq.exchange}")
    private String exchange;
    @Value("${rabbitmq.routing-key}")
    private String routingKey;

    /**
     * Salje email notifikaciju na RabbitMQ exchange sa konfigurisanom routing putanjom.
     *
     * @param dto payload poruke koja se prosledjuje email servisu
     */
    public void sendEmailNotification(EmailDto dto) {
        rabbitTemplate.convertAndSend(exchange,routingKey,dto);
    }
}
