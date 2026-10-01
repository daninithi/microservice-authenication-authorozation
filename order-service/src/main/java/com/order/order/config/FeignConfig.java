package com.order.order.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.order.order.exception.ProductServiceException;

import feign.codec.ErrorDecoder;

@Configuration
public class FeignConfig {

    // Turns error replies from product-service into a ProductServiceException
    // (handled in GlobalExceptionHandler) instead of a generic 500
    @Bean
    public ErrorDecoder productServiceErrorDecoder() {
        return (methodKey, response) -> switch (response.status()) {
            case 404 -> new ProductServiceException(404, "Product not found");
            case 400 -> new ProductServiceException(400, "Not enough stock for this product");
            case 503 -> new ProductServiceException(503,
                    "Product Service is not available. Please try again later");
            default -> new ProductServiceException(502,
                    "Product Service returned status " + response.status());
        };
    }
}
