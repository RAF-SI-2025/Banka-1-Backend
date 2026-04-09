package com.banka1.order.controller;

import com.banka1.order.dto.AuthenticatedUser;
import com.banka1.order.dto.CreateBuyOrderRequest;
import com.banka1.order.dto.CreateSellOrderRequest;
import com.banka1.order.dto.OrderOverviewResponse;
import com.banka1.order.dto.OrderResponse;
import com.banka1.order.dto.PartialCancelOrderRequest;
import com.banka1.order.entity.enums.OrderOverviewStatusFilter;
import com.banka1.order.service.OrderCreationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * REST controller for managing brokerage orders.
 */
@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderCreationService orderCreationService;

    /**
     * Creates a new buy order.
     *
     * @param jwt the authenticated user
     * @param request the buy order details
     * @return the created order response
     */
    @PostMapping("/buy")
    @PreAuthorize("hasAnyRole('CLIENT_TRADING','AGENT','SUPERVISOR')")
    public ResponseEntity<OrderResponse> createBuyOrder(@AuthenticationPrincipal Jwt jwt,
                                                        @Valid @RequestBody CreateBuyOrderRequest request) {
        OrderResponse response = orderCreationService.createBuyOrder(toAuthenticatedUser(jwt), request);
        return ResponseEntity.ok(response);
    }

    /**
     * Creates a new sell order.
     *
     * @param jwt the authenticated user
     * @param request the sell order details
     * @return the created order response
     */
    @PostMapping("/sell")
    @PreAuthorize("hasAnyRole('CLIENT_TRADING','AGENT','SUPERVISOR')")
    public ResponseEntity<OrderResponse> createSellOrder(@AuthenticationPrincipal Jwt jwt,
                                                         @Valid @RequestBody CreateSellOrderRequest request) {
        OrderResponse response = orderCreationService.createSellOrder(toAuthenticatedUser(jwt), request);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @PreAuthorize("hasRole('SUPERVISOR')")
    public ResponseEntity<List<OrderOverviewResponse>> getOrders(
            @RequestParam(defaultValue = "ALL") OrderOverviewStatusFilter status
    ) {
        return ResponseEntity.ok(orderCreationService.getOrders(status));
    }

    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasAnyRole('CLIENT_TRADING','AGENT','SUPERVISOR')")
    public ResponseEntity<OrderResponse> confirmOrder(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return ResponseEntity.ok(orderCreationService.confirmOrder(toAuthenticatedUser(jwt), id));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('CLIENT_TRADING','AGENT','SUPERVISOR')")
    public ResponseEntity<OrderResponse> cancelOrder(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return ResponseEntity.ok(orderCreationService.cancelOrder(toAuthenticatedUser(jwt), id));
    }

    @PutMapping("/{id}/cancel")
    @PreAuthorize("hasRole('SUPERVISOR')")
    public ResponseEntity<OrderResponse> cancelOrder(
            @PathVariable Long id,
            @RequestBody(required = false) PartialCancelOrderRequest request
    ) {
        return ResponseEntity.ok(orderCreationService.cancelOrder(id, request == null ? null : request.getQuantity()));
    }

    @PutMapping("/{id}/approve")
    @PreAuthorize("hasRole('SUPERVISOR')")
    public ResponseEntity<OrderResponse> approveOrder(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return ResponseEntity.ok(orderCreationService.approveOrder(Long.valueOf(jwt.getSubject()), id));
    }

    @PutMapping("/{id}/decline")
    @PreAuthorize("hasRole('SUPERVISOR')")
    public ResponseEntity<OrderResponse> declineOrder(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return ResponseEntity.ok(orderCreationService.declineOrder(Long.valueOf(jwt.getSubject()), id));
    }

    private AuthenticatedUser toAuthenticatedUser(Jwt jwt) {
        return new AuthenticatedUser(
                Long.valueOf(jwt.getSubject()),
                extractStrings(jwt.getClaim("roles")),
                extractStrings(jwt.getClaim("permissions"))
        );
    }

    @SuppressWarnings("unchecked")
    private Set<String> extractStrings(Object claim) {
        if (claim == null) {
            return Set.of();
        }
        if (claim instanceof String value) {
            return Set.of(value);
        }
        if (claim instanceof Collection<?> values) {
            Set<String> result = new LinkedHashSet<>();
            for (Object value : values) {
                if (value != null) {
                    result.add(String.valueOf(value));
                }
            }
            return result;
        }
        return Set.of(String.valueOf(claim));
    }
}
