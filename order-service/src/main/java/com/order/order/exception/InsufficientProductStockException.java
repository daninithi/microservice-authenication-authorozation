package com.order.order.exception;

public class InsufficientProductStockException
        extends RuntimeException {

    public InsufficientProductStockException(
            String message
    ) {
        super(message);
    }
}