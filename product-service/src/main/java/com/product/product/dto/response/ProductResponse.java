package com.product.product.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.product.product.model.Product;

public class ProductResponse {

    private final Long productId;
    private final String name;
    private final String description;
    private final BigDecimal price;
    private final Integer stockQuantity;
    private final LocalDateTime createdAt;

    public ProductResponse(
            Long productId,
            String name,
            String description,
            BigDecimal price,
            Integer stockQuantity,
            LocalDateTime createdAt
    ) {
        this.productId = productId;
        this.name = name;
        this.description = description;
        this.price = price;
        this.stockQuantity = stockQuantity;
        this.createdAt = createdAt;
    }

    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getProductId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getStockQuantity(),
                product.getCreatedAt()
        );
    }

    public Long getProductId() {
        return productId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public Integer getStockQuantity() {
        return stockQuantity;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}