package app.integration;

import app.config.RabbitConfig;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(
        classes = RabbitConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@TestPropertySource(properties = {
        "notification.rabbit.exchange=employee.events",
        "notification.rabbit.queue=notification-service-queue",
        "notification.rabbit.routing-key=employee.#",
        "notification.rabbit.client-routing-key=client.#",
        "notification.rabbit.card-routing-key=card.#",
        "notification.rabbit.credit-routing-key=credit.#",
        "notification.rabbit.order-routing-key=order.#",
        "notification.rabbit.tax-routing-key=tax.#"
})
class RabbitBindingsConfigIntegrationTest {

    @Autowired
    @Qualifier("creditNotificationBinding")
    private Binding creditNotificationBinding;

    @Autowired
    @Qualifier("orderNotificationBinding")
    private Binding orderNotificationBinding;

    @Autowired
    @Qualifier("taxNotificationBinding")
    private Binding taxNotificationBinding;

    @Test
    void orderBindingUsesOrderWildcardRoutingKey() {
        assertEquals("order.#", orderNotificationBinding.getRoutingKey());
    }

    @Test
    void creditBindingUsesCreditWildcardRoutingKey() {
        assertEquals("credit.#", creditNotificationBinding.getRoutingKey());
    }

    @Test
    void taxBindingUsesTaxWildcardRoutingKey() {
        assertEquals("tax.#", taxNotificationBinding.getRoutingKey());
    }
}
