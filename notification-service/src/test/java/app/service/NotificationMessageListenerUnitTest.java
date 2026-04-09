package app.service;

import app.dto.NotificationRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link NotificationMessageListener} listener delegation.
 *
 * <p>This class verifies that the RabbitMQ listener does not add business logic of its own and
 * simply forwards the consumed payload and routing key to {@link NotificationDeliveryService}.
 */
@ExtendWith(MockitoExtension.class)
class NotificationMessageListenerUnitTest {

    @Mock
    private NotificationDeliveryService notificationDeliveryService;

    @InjectMocks
    private NotificationMessageListener notificationMessageListener;

    /**
     * Verifies that a normal incoming RabbitMQ payload is delegated together with its routing key.
     *
     * <p>This protects the listener entry point from accidentally mutating or dropping routing
     * metadata before it reaches the delivery orchestration layer.
     */
    @Test
    void receiveMessageDelegatesPayloadAndRoutingKeyToDeliveryService() {
        NotificationRequest request = new NotificationRequest(
                "Dimitrije",
                "dimitrije.tomic99@gmail.com",
                Map.of("name", "Dimitrije", "activationLink", "https://example.com/activate")
        );

        notificationMessageListener.receiveMessage(request, "employee.created");

        verify(notificationDeliveryService).handleIncomingMessage(request, "employee.created");
    }

    /**
     * Verifies that a null payload is still passed through to the delivery service.
     *
     * <p>The delivery layer is responsible for validation and error persistence, so the listener
     * must not swallow invalid messages before they reach that logic.
     */
    @Test
    void receiveMessageDelegatesNullPayloadToDeliveryService() {
        notificationMessageListener.receiveMessage(null, "employee.created");

        verify(notificationDeliveryService).handleIncomingMessage(null, "employee.created");
    }

    /**
     * Verifies that newly introduced credit/order/tax routing keys are forwarded unchanged.
     *
     * <p>This protects the listener from adding any special-case filtering for the new
     * ISSUE #39 event families.
     */
    @Test
    void receiveMessageDelegatesNewRoutingKeysToDeliveryService() {
        NotificationRequest request = new NotificationRequest(
                "Dimitrije",
                "dimitrije.tomic99@gmail.com",
                Map.of("name", "Dimitrije", "creditId", "42")
        );

        notificationMessageListener.receiveMessage(request, "credit.installment_failed");
        notificationMessageListener.receiveMessage(request, "order.approved");
        notificationMessageListener.receiveMessage(request, "tax.collected");

        verify(notificationDeliveryService).handleIncomingMessage(request, "credit.installment_failed");
        verify(notificationDeliveryService).handleIncomingMessage(request, "order.approved");
        verify(notificationDeliveryService).handleIncomingMessage(request, "tax.collected");
    }
}
