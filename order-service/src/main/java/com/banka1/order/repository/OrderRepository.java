package com.banka1.order.repository;

import com.banka1.order.entity.Order;
import com.banka1.order.entity.enums.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for {@link Order} entities.
 */
@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    /**
     * Returns all orders placed by a specific user.
     *
     * @param userId the user's identifier
     * @return list of orders for that user
     */
    List<Order> findByUserId(Long userId);

    /**
     * Returns all orders with a given status.
     *
     * @param status the order status to filter by
     * @return list of matching orders
     */
    List<Order> findByStatus(OrderStatus status);

    /**
     * Returns all orders for a specific user with a given status.
     *
     * @param userId the user's identifier
     * @param status the order status to filter by
     * @return list of matching orders
     */
    List<Order> findByUserIdAndStatus(Long userId, OrderStatus status);
}
