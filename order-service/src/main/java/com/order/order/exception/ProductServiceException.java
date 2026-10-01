package com.order.order.exception;

public class ProductServiceException
        extends RuntimeException {

    private final int status;

    public ProductServiceException(
            int status,
            String message
    ) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}