package com.example.fooddelivery.dto;

import jakarta.validation.constraints.NotBlank;

public record DeliveryPartnerRequest(
        @NotBlank String name,
        String phone
) {
}
