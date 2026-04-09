package com.banka1.order.controller;

import com.banka1.order.dto.OrderOverviewResponse;
import com.banka1.order.dto.OrderResponse;
import com.banka1.order.dto.PartialCancelOrderRequest;
import com.banka1.order.entity.enums.OrderOverviewStatusFilter;
import com.banka1.order.service.OrderCreationService;
import jakarta.validation.Valid;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderControllerTest {

    @Mock
    private OrderCreationService orderCreationService;

    private OrderController controller;

    @BeforeEach
    void setUp() {
        controller = new OrderController(orderCreationService);
    }

    @Test
    void getOrders_methodExistsAndDelegatesToService() {
        OrderOverviewResponse row = new OrderOverviewResponse();
        row.setOrderId(1L);
        when(orderCreationService.getOrders(eq(OrderOverviewStatusFilter.ALL), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(row)));

        ResponseEntity<Page<OrderOverviewResponse>> response = controller.getOrders(OrderOverviewStatusFilter.ALL, 0, 10);

        assertThat(response.getBody().getContent()).hasSize(1);
        assertThat(response.getBody().getContent().getFirst().getOrderId()).isEqualTo(1L);
        verify(orderCreationService).getOrders(eq(OrderOverviewStatusFilter.ALL), any(Pageable.class));
    }

    @Test
    void supervisorPortalEndpointsUseExpectedMappingsAndSecurity() throws Exception {
        assertGetMapping("getOrders");
        assertPreAuthorize("getOrders", "hasRole('SUPERVISOR')");

        assertPutMapping("approveOrder", "/{id}/approve");
        assertPreAuthorize("approveOrder", "hasRole('SUPERVISOR')");

        assertPutMapping("declineOrder", "/{id}/decline");
        assertPreAuthorize("declineOrder", "hasRole('SUPERVISOR')");

        assertPutMapping("cancelOrder", "/{id}/cancel", Long.class, PartialCancelOrderRequest.class);
        assertPreAuthorize("cancelOrder", "hasRole('SUPERVISOR')", Long.class, PartialCancelOrderRequest.class);
    }

    @Test
    void legacyClientCancelEndpointIsStillPresentForExistingFlows() throws Exception {
        Method method = OrderController.class.getDeclaredMethod("cancelOrder", Jwt.class, Long.class);
        PostMapping mapping = method.getAnnotation(PostMapping.class);
        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);

        assertThat(mapping).isNotNull();
        assertThat(mapping.value()).containsExactly("/{id}/cancel");
        assertThat(preAuthorize.value()).isEqualTo("hasAnyRole('CLIENT_TRADING','AGENT','SUPERVISOR')");
    }

    @Test
    void buyAndSellRequestsUseBeanValidation() throws Exception {
        Method createBuyOrder = OrderController.class.getDeclaredMethod("createBuyOrder", Jwt.class, com.banka1.order.dto.CreateBuyOrderRequest.class);
        assertThat(createBuyOrder.getParameters()[1].getAnnotation(Valid.class)).isNotNull();
        assertThat(createBuyOrder.getParameters()[1].getAnnotation(RequestBody.class)).isNotNull();
        assertThat(createBuyOrder.getAnnotation(PreAuthorize.class).value()).isEqualTo("hasAnyRole('CLIENT_TRADING','AGENT','SUPERVISOR')");

        Method createSellOrder = OrderController.class.getDeclaredMethod("createSellOrder", Jwt.class, com.banka1.order.dto.CreateSellOrderRequest.class);
        assertThat(createSellOrder.getParameters()[1].getAnnotation(Valid.class)).isNotNull();
        assertThat(createSellOrder.getParameters()[1].getAnnotation(RequestBody.class)).isNotNull();
        assertThat(createSellOrder.getAnnotation(PreAuthorize.class).value()).isEqualTo("hasAnyRole('CLIENT_TRADING','AGENT','SUPERVISOR')");

        Method confirmOrder = OrderController.class.getDeclaredMethod("confirmOrder", Jwt.class, Long.class);
        assertThat(confirmOrder.getAnnotation(PreAuthorize.class).value()).isEqualTo("hasAnyRole('CLIENT_TRADING','AGENT','SUPERVISOR')");
    }

    private void assertGetMapping(String methodName) throws Exception {
        Method method = OrderController.class.getDeclaredMethod(methodName, OrderOverviewStatusFilter.class, int.class, int.class);
        GetMapping mapping = method.getAnnotation(GetMapping.class);
        assertThat(mapping).isNotNull();
    }

    private void assertPutMapping(String methodName, String path, Class<?>... parameterTypes) throws Exception {
        Method method = OrderController.class.getDeclaredMethod(methodName, parameterTypes.length == 0 ? new Class<?>[]{Jwt.class, Long.class} : parameterTypes);
        PutMapping mapping = method.getAnnotation(PutMapping.class);
        assertThat(mapping).isNotNull();
        assertThat(mapping.value()).containsExactly(path);
    }

    private void assertPreAuthorize(String methodName, String expected, Class<?>... parameterTypes) throws Exception {
        Class<?>[] resolvedParameterTypes = parameterTypes.length == 0
                ? ("getOrders".equals(methodName) ? new Class<?>[]{OrderOverviewStatusFilter.class, int.class, int.class} : new Class<?>[]{Jwt.class, Long.class})
                : parameterTypes;
        Method method = OrderController.class.getDeclaredMethod(methodName, resolvedParameterTypes);
        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);
        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value()).isEqualTo(expected);
    }
}
