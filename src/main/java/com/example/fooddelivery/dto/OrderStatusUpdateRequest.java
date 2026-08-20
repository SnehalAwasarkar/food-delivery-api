package com.example.fooddelivery.dto;

import jakarta.validation.constraints.NotBlank;

public record OrderStatusUpdateRequest(
        @NotBlank String status
) {
}
