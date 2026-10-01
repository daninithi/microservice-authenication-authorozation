package com.order.order.exception;

// A logged-in user tried to read or change someone else's order
public class OrderAccessDeniedException extends RuntimeException {

    public OrderAccessDeniedException(String message) {
        super(message);
    }
}
