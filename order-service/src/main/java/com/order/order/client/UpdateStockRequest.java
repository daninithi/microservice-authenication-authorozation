package com.order.order.client;

public class UpdateStockRequest {

    private Integer quantity;

    public UpdateStockRequest() {
    }

    public UpdateStockRequest(Integer quantity) {
        this.quantity = quantity;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }
}