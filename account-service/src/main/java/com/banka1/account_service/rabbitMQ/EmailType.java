package com.banka1.account_service.rabbitMQ;

/**
 * Enum koji definise tipove email notifikacija koje employee-service salje putem RabbitMQ-a.
 * Svaki tip nosi odgovarajuci RabbitMQ routing key.
 */
public enum EmailType {

    ACCOUNT_CREATED("account.created"),

    ACCOUNT_DEACTIVATED("account.deactivated");


    private final String routingKey;

    EmailType(String routingKey) {
        this.routingKey = routingKey;
    }

    public String getRoutingKey() {
        return routingKey;
    }
}
