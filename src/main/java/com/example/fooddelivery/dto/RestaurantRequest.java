package com.example.fooddelivery.dto;

import jakarta.validation.constraints.NotBlank;

public record RestaurantRequest(
        @NotBlank String name,
        String cuisine,
        String address
) {
}
