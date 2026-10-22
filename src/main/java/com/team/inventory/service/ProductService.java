package com.team.inventory.service;

import com.team.inventory.dto.CategoryDto;
import com.team.inventory.dto.ProductDto;
import com.team.inventory.dto.ProductRequest;
import com.team.inventory.repository.CategoryRepository;
import com.team.inventory.repository.ProductRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Rules for products (stories 2, 3, 6, 9) and categories (story 8). */
@Service
public class ProductService {

    private final ProductRepository products;
    private final CategoryRepository categories;

    public ProductService(ProductRepository products, CategoryRepository categories) {
        this.products = products;
        this.categories = categories;
    }

    // ---- Products ----

    public List<ProductDto> search(String query, Long categoryId) {
        return products.search(query, categoryId);
    }

    public List<ProductDto> lowStock() {
        return products.findLowStock();
    }

    public ProductDto get(long id) {
        return products.findById(id).orElseThrow(() -> ApiException.notFound("Product not found."));
    }

    @Transactional
    public ProductDto create(ProductRequest request) {
        check(request, null);
        return get(products.insert(request));
    }

    @Transactional
    public ProductDto update(long id, ProductRequest request) {
        get(id);
        check(request, id);
        products.update(id, request);
        return get(id);
    }

    @Transactional
    public void delete(long id) {
        get(id);
        if (products.hasSales(id)) {
            throw ApiException.conflict(
                "This product has sales recorded, so it can't be deleted (the sales history needs it). Set its quantity to 0 instead.");
        }
        products.delete(id);
    }

    private void check(ProductRequest request, Long id) {
        if (products.skuTaken(request.cleanSku(), id)) {
            throw ApiException.conflict("Another product already uses SKU " + request.cleanSku() + ".");
        }
        if (request.categoryId() != null && !categories.exists(request.categoryId())) {
            throw ApiException.badRequest("That category doesn't exist (it may have been deleted). Choose another.");
        }
    }

    // ---- Categories ----

    public List<CategoryDto> categories() {
        return categories.findAll();
    }

    @Transactional
    public CategoryDto createCategory(String rawName) {
        String name = rawName.trim();
        if (categories.nameTaken(name)) {
            throw ApiException.conflict("A category called " + name + " already exists.");
        }
        long id = categories.insert(name);
        return categories.findById(id).orElseThrow();
    }

    /** Deletes the category; its products stay, just without a category. */
    @Transactional
    public void deleteCategory(long id) {
        if (!categories.exists(id)) {
            throw ApiException.notFound("Category not found.");
        }
        categories.unassignProducts(id);
        categories.delete(id);
    }
}
