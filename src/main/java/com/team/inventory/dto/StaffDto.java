package com.team.inventory.dto;

/** A staff account in the staff management list. Never includes the password or its hash. */
public record StaffDto(long id, String username) {
}
