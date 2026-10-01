package com.order.order.exception;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import feign.RetryableException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // Handle DTO validation errors
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationException(
            MethodArgumentNotValidException exception
    ) {

        List<String> messages = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error ->
                        error.getField()
                                + ": "
                                + error.getDefaultMessage()
                )
                .toList();

        ApiErrorResponse response = new ApiErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "Validation failed",
                messages
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(response);
    }

    // Handle missing orders
    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleOrderNotFoundException(
            OrderNotFoundException exception
    ) {

        ApiErrorResponse response = new ApiErrorResponse(
                HttpStatus.NOT_FOUND.value(),
                "Order not found",
                List.of(exception.getMessage())
        );

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(response);
    }

    // Handle invalid status transitions
    @ExceptionHandler(InvalidOrderStatusTransitionException.class)
    public ResponseEntity<ApiErrorResponse>
    handleInvalidOrderStatusTransitionException(
            InvalidOrderStatusTransitionException exception
    ) {

        ApiErrorResponse response = new ApiErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "Invalid order status transition",
                List.of(exception.getMessage())
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(response);
    }

    @ExceptionHandler(ProductServiceException.class)
     public ResponseEntity<ApiErrorResponse>
        handleProductServiceException(
                ProductServiceException exception
        ) {

        ApiErrorResponse response =
                new ApiErrorResponse(
                        exception.getStatus(),
                        "Product Service error",
                        List.of(exception.getMessage())
                );

        return ResponseEntity
                .status(exception.getStatus())
                .body(response);
     }

     @ExceptionHandler(InsufficientProductStockException.class)
        public ResponseEntity<ApiErrorResponse>
        handleInsufficientProductStockException(
                InsufficientProductStockException exception
        ) {

        ApiErrorResponse response =
                new ApiErrorResponse(
                        400,
                        "Insufficient product stock",
                        List.of(exception.getMessage())
                );

        return ResponseEntity
                .status(400)
                .body(response);
        }

    // A logged-in user tried to touch someone else's order
    @ExceptionHandler(OrderAccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleOrderAccessDeniedException(
            OrderAccessDeniedException exception
    ) {

        ApiErrorResponse response = new ApiErrorResponse(
                HttpStatus.FORBIDDEN.value(),
                "Forbidden",
                List.of(exception.getMessage())
        );

        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(response);
    }

    // Product Service is not running or not reachable
    @ExceptionHandler(RetryableException.class)
    public ResponseEntity<ApiErrorResponse> handleProductServiceUnavailable(
            RetryableException exception
    ) {

        ApiErrorResponse response = new ApiErrorResponse(
                HttpStatus.SERVICE_UNAVAILABLE.value(),
                "Product Service unavailable",
                List.of("Product Service is not reachable. Please try again later")
        );

        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(response);
    }
}
