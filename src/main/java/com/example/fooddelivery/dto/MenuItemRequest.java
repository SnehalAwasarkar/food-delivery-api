package com.example.fooddelivery.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record MenuItemRequest(
        @NotBlank String name,
        @NotNull @Positive BigDecimal price
) {
}
