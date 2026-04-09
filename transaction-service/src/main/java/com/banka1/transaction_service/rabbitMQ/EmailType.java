package com.banka1.transaction_service.rabbitMQ;

/**
 * Enum defining types of email notifications sent by the employee-service via RabbitMQ.
 * Each type carries the corresponding RabbitMQ routing key.
 */
public enum EmailType {

    TRANSACTION_COMPLETED("transaction.completed"),

    TRANSACTION_DENIED("transaction.denied");


    private final String routingKey;

    EmailType(String routingKey) {
        this.routingKey = routingKey;
    }

    public String getRoutingKey() {
        return routingKey;
    }
}
