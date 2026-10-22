package com.team.inventory.controller;

import com.team.inventory.dto.CategoryDto;
import com.team.inventory.dto.CategoryRequest;
import com.team.inventory.service.ProductService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Categories (story 8). Everyone logged in can list them; only Admin can add/delete (SecurityConfig).
 *
 * GET    /api/categories       -> [{id, name, productCount}]
 * POST   /api/categories       -> 201 + the new category
 * DELETE /api/categories/{id}  -> 204; its products become uncategorized
 */
@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final ProductService service;

    public CategoryController(ProductService service) {
        this.service = service;
    }

    @GetMapping
    public List<CategoryDto> list() {
        return service.categories();
    }

    @PostMapping
    public ResponseEntity<CategoryDto> create(@Valid @RequestBody CategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createCategory(request.name()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        service.deleteCategory(id);
        return ResponseEntity.noContent().build();
    }
}
