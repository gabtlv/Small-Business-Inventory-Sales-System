package com.team.inventory.controller;

import com.team.inventory.dto.CheckoutProduct;
import com.team.inventory.dto.SaleRequest;
import com.team.inventory.dto.SaleResponse;
import com.team.inventory.service.SaleException;
import com.team.inventory.service.SaleService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Checkout (story 5). Admin and Staff can both use it (see SecurityConfig).
 *
 * GET  /api/sales/products -> products with price and stock, for the checkout page
 * POST /api/sales          -> record a sale and take the items out of stock
 *                             (optional Idempotency-Key header makes retries safe)
 *
 * Errors come back as {"error": "message to show the cashier"}.
 */
@RestController
@RequestMapping("/api/sales")
public class SaleController {

    private final SaleService saleService;

    public SaleController(SaleService saleService) {
        this.saleService = saleService;
    }

    @GetMapping("/products")
    public List<CheckoutProduct> products() {
        return saleService.checkoutProducts();
    }

    /**
     * 201 = new sale recorded. 200 = this Idempotency-Key was already used for a saved sale,
     * so that sale is returned and nothing new is recorded (safe retry after a lost response).
     */
    @PostMapping
    public ResponseEntity<SaleResponse> recordSale(@Valid @RequestBody SaleRequest request,
                                                   @RequestHeader(name = "Idempotency-Key", required = false) String requestKey,
                                                   Authentication auth) {
        // The cashier is whoever is logged in; any user id in the request body is ignored.
        SaleResponse sale = saleService.recordSale(auth.getName(), request, requestKey);
        return ResponseEntity.status(sale.alreadyRecorded() ? HttpStatus.OK : HttpStatus.CREATED).body(sale);
    }

    @ExceptionHandler(SaleException.class)
    public ResponseEntity<Map<String, String>> saleFailed(SaleException e) {
        Map<String, String> body = e.getCode() == null
            ? Map.of("error", e.getMessage())
            : Map.of("error", e.getMessage(), "code", e.getCode());
        return ResponseEntity.status(e.getStatus()).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> invalidRequest(MethodArgumentNotValidException e) {
        String message = e.getAllErrors().stream()
            .map(err -> err.getDefaultMessage())
            .findFirst()
            .orElse("Invalid sale.");
        return ResponseEntity.badRequest().body(Map.of("error", message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> unreadableRequest() {
        return ResponseEntity.badRequest()
            .body(Map.of("error", "Invalid sale. Quantities must be whole numbers of at least 1."));
    }
}
