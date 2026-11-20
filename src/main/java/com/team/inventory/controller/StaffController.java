package com.team.inventory.controller;

import com.team.inventory.dto.StaffDto;
import com.team.inventory.dto.StaffRequest;
import com.team.inventory.service.StaffService;
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
 * Staff management (Sprint 3). Admin only (SecurityConfig: /api/users/**).
 *
 * GET    /api/users       -> [{id, username}] of active Staff accounts
 * POST   /api/users       -> 201 + the new Staff account ({username, password}; always role STAFF)
 * DELETE /api/users/{id}  -> 204; the account can't log in any more, its sales are kept
 */
@RestController
@RequestMapping("/api/users")
public class StaffController {

    private final StaffService service;

    public StaffController(StaffService service) {
        this.service = service;
    }

    @GetMapping
    public List<StaffDto> list() {
        return service.list();
    }

    @PostMapping
    public ResponseEntity<StaffDto> create(@Valid @RequestBody StaffRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> remove(@PathVariable long id) {
        service.remove(id);
        return ResponseEntity.noContent().build();
    }
}
