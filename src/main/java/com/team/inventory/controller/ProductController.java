package com.team.inventory.controller;

import com.team.inventory.dto.ProductDto;
import com.team.inventory.dto.ProductRequest;
import com.team.inventory.service.ProductService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Products (stories 2, 3), search (9), category filter (8), low-stock alerts (6).
 * Who can call what is in SecurityConfig: everyone logged in can read, only Admin can change,
 * and only Admin sees low-stock alerts.
 *
 * GET    /api/products?q=&categoryId=  -> list, optionally searched by name/SKU and filtered by category
 * GET    /api/products/low-stock       -> products at or below their low-stock threshold
 * GET    /api/products/{id}
 * POST   /api/products                 -> 201 + the new product
 * PUT    /api/products/{id}
 * DELETE /api/products/{id}            -> 204 (409 if the product has sales)
 */
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService service;

    public ProductController(ProductService service) {
        this.service = service;
    }

    @GetMapping
    public List<ProductDto> list(@RequestParam(required = false) String q,
                                 @RequestParam(required = false) Long categoryId) {
        return service.search(q, categoryId);
    }

    @GetMapping("/low-stock")
    public List<ProductDto> lowStock() {
        return service.lowStock();
    }

    @GetMapping("/{id}")
    public ProductDto get(@PathVariable long id) {
        return service.get(id);
    }

    @PostMapping
    public ResponseEntity<ProductDto> create(@Valid @RequestBody ProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{id}")
    public ProductDto update(@PathVariable long id, @Valid @RequestBody ProductRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
