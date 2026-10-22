package com.team.inventory.dto;

/** A product category (story 8). productCount = how many products use it. */
public record CategoryDto(long id, String name, int productCount) {
}
