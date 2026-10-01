package com.product.product.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.product.product.dto.request.UpdateStockRequest;
import com.product.product.dto.response.ProductResponse;
import com.product.product.service.ProductService;

import jakarta.validation.Valid;

// Only for other services (order-service), never for users.
// The API Gateway has no route for /internal/**, so outside clients can't call it.
@RestController
@RequestMapping("/internal/products")
public class InternalProductController {

    private final ProductService productService;

    public InternalProductController(
            ProductService productService
    ) {
        this.productService = productService;
    }

    // Order placed: quantity is negative. Order cancelled: quantity is positive.
    @PatchMapping("/{productId}/stock")
    public ResponseEntity<ProductResponse> updateStock(
            @PathVariable Long productId,
            @Valid @RequestBody UpdateStockRequest request
    ) {

        ProductResponse response =
                productService.updateStock(
                        productId,
                        request
                );

        return ResponseEntity.ok(response);
    }
}
