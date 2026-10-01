
package com.order.order.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

import com.order.order.model.Order;
import com.order.order.model.OrderStatus;

public class OrderResponse {

    private final Long orderId;
    private final UUID customerId;
    private final Long productId;
    private final Integer quantity;
    private final OrderStatus status;
    private final LocalDateTime createdAt;

    public OrderResponse(
            Long orderId,
            UUID customerId,
            Long productId,
            Integer quantity,
            OrderStatus status,
            LocalDateTime createdAt) {

        this.orderId = orderId;
        this.customerId = customerId;
        this.productId = productId;
        this.quantity = quantity;
        this.status = status;
        this.createdAt = createdAt;
    }

    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getOrderId(),
                order.getCustomerId(),
                order.getProductId(),
                order.getQuantity(),
                order.getStatus(),
                order.getCreatedAt()
        );
    }

    public Long getOrderId() {
        return orderId;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public Long getProductId() {
        return productId;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}