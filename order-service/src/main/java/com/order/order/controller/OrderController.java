package com.order.order.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.order.order.dto.request.CreateOrderRequest;
import com.order.order.dto.request.UpdateOrderStatusRequest;
import com.order.order.dto.response.OrderResponse;
import com.order.order.security.AuthUser;
import com.order.order.service.OrderService;

import jakarta.validation.Valid;

// Access rules are in SecurityConfig; ownership checks are in OrderService.
// @AuthenticationPrincipal gives the AuthUser that JwtAuthenticationFilter read from the token.
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(
        OrderService orderService
    ) {
        this.orderService = orderService;
    }

    // Create an order for the logged-in user
    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
            @Valid @RequestBody CreateOrderRequest request,
            @AuthenticationPrincipal AuthUser currentUser
    ) {

        OrderResponse response = orderService.createOrder(request, currentUser);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    // ADMIN: get all orders with pagination and sorting
    @GetMapping
    public ResponseEntity<Page<OrderResponse>> getAllOrders(
            Pageable pageable
    ) {

        Page<OrderResponse> responses =
                orderService.getAllOrders(pageable);

        return ResponseEntity.ok(responses);
    }

    // The logged-in user's own orders, with pagination and sorting
    @GetMapping("/my")
    public ResponseEntity<Page<OrderResponse>> getMyOrders(
            @AuthenticationPrincipal AuthUser currentUser,
            Pageable pageable
    ) {

        Page<OrderResponse> responses =
                orderService.getMyOrders(currentUser, pageable);

        return ResponseEntity.ok(responses);
    }

    // Get one order by ID (owner or ADMIN)
    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponse> getOrderById(
            @PathVariable Long orderId,
            @AuthenticationPrincipal AuthUser currentUser
    ) {

        OrderResponse response =
                orderService.getOrderById(orderId, currentUser);

        return ResponseEntity.ok(response);
    }

    // ADMIN: update order status
    @PatchMapping("/{orderId}/status")
    public ResponseEntity<OrderResponse> updateOrderStatus(
            @PathVariable Long orderId,
            @Valid @RequestBody UpdateOrderStatusRequest request
    ) {

        OrderResponse response =
                orderService.updateOrderStatus(orderId, request);

        return ResponseEntity.ok(response);
    }

    // Cancel an order (owner or ADMIN)
    @PatchMapping("/{orderId}/cancel")
    public ResponseEntity<OrderResponse> cancelOrder(
            @PathVariable Long orderId,
            @AuthenticationPrincipal AuthUser currentUser
    ) {

        OrderResponse response =
                orderService.cancelOrder(orderId, currentUser);

        return ResponseEntity.ok(response);
    }

}
