package com.product.product.service;

import java.time.LocalDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.product.product.dto.request.CreateProductRequest;
import com.product.product.dto.request.UpdateProductRequest;
import com.product.product.dto.request.UpdateStockRequest;
import com.product.product.dto.response.ProductResponse;
import com.product.product.exception.InsufficientStockException;
import com.product.product.exception.ProductNotFoundException;
import com.product.product.model.Product;
import com.product.product.repository.ProductRepository;

@Service
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Transactional
    public ProductResponse createProduct(
            CreateProductRequest request
    ) {

        Product product = new Product(
                null,
                request.getName(),
                request.getDescription(),
                request.getPrice(),
                request.getStockQuantity(),
                LocalDateTime.now()
        );

        Product savedProduct =
                productRepository.save(product);

        return ProductResponse.from(savedProduct);
    }

    @Transactional(readOnly = true)
    public Page<ProductResponse> getAllProducts(
            Pageable pageable
    ) {

        return productRepository
                .findAll(pageable)
                .map(ProductResponse::from);
    }

    @Transactional(readOnly = true)
    public ProductResponse getProductById(
            Long productId
    ) {

        Product product = findProductById(productId);

        return ProductResponse.from(product);
    }

    @Transactional
    public ProductResponse updateProduct(
            Long productId,
            UpdateProductRequest request
    ) {

        Product product = findProductById(productId);

        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setStockQuantity(request.getStockQuantity());

        Product updatedProduct =
                productRepository.save(product);

        return ProductResponse.from(updatedProduct);
    }

    @Transactional
    public void deleteProduct(Long productId) {

        Product product = findProductById(productId);

        productRepository.delete(product);
    }

    @Transactional
    public ProductResponse updateStock(
            Long productId,
            UpdateStockRequest request
    ) {

        Product product = findProductById(productId);

        int currentStock =
                product.getStockQuantity();

        int quantityChange =
                request.getQuantity();

        int newStock =
                currentStock + quantityChange;

        if (newStock < 0) {
            throw new InsufficientStockException(
                    "Insufficient stock for product ID: "
                            + productId
            );
        }

        product.setStockQuantity(newStock);

        Product updatedProduct =
                productRepository.save(product);

        return ProductResponse.from(updatedProduct);
    }

    private Product findProductById(Long productId) {

        return productRepository
                .findById(productId)
                .orElseThrow(() ->
                        new ProductNotFoundException(
                                "Product not found with ID: "
                                        + productId
                        )
                );
    }
}