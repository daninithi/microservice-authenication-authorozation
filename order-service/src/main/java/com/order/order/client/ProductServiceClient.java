package com.order.order.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

// Fixed URL from settings: product-service.url (PRODUCT_SERVICE_URL)
@FeignClient(
        name = "product-service",
        url = "${product-service.url}"
)
public interface ProductServiceClient {

    @GetMapping("/api/v1/products/{productId}")
    ProductResponse getProduct(
            @PathVariable("productId") Long productId
    );

    // Internal endpoint: only reachable from inside the system, not through the gateway
    @PatchMapping("/internal/products/{productId}/stock")
    ProductResponse updateStock(
            @PathVariable("productId") Long productId,
            @RequestBody UpdateStockRequest request
    );
}
