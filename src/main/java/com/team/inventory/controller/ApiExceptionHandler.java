package com.team.inventory.controller;

import com.team.inventory.service.ApiException;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Turns errors from the product, category and report APIs into {"error": "readable message"},
 * the same shape checkout uses, so app.js's api() helper can show them.
 * (SaleController keeps its own handlers; this only covers the controllers listed here.)
 */
@RestControllerAdvice(assignableTypes = {ProductController.class, CategoryController.class, ReportController.class})
public class ApiExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, String>> apiError(ApiException e) {
        return ResponseEntity.status(e.getStatus()).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> invalid(MethodArgumentNotValidException e) {
        String message = e.getAllErrors().stream()
            .map(err -> err.getDefaultMessage())
            .findFirst()
            .orElse("Invalid request.");
        return ResponseEntity.badRequest().body(Map.of("error", message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> unreadable() {
        return ResponseEntity.badRequest()
            .body(Map.of("error", "Invalid request. Check that price is a number and quantities are whole numbers."));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, String>> badParameter(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.badRequest().body(Map.of("error", "Invalid value for " + e.getName() + "."));
    }

    /** Backstop if two requests race past our checks (e.g. same SKU saved twice at once). */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> conflict() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(Map.of("error", "That change conflicts with existing data (duplicate SKU or name, or the item is in use). Refresh and try again."));
    }
}
