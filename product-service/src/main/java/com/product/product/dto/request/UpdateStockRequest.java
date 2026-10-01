package com.product.product.dto.request;

import jakarta.validation.constraints.NotNull;

public class UpdateStockRequest {

    @NotNull(message = "Quantity change is required")
    private Integer quantity;

    public UpdateStockRequest() {
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }
}