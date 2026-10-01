
package com.order.order.repository;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.order.order.model.Order;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Page<Order> findByCustomerId(UUID customerId, Pageable pageable);
}