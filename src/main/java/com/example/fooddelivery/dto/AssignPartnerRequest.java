package com.example.fooddelivery.dto;

import jakarta.validation.constraints.NotNull;

public record AssignPartnerRequest(
        @NotNull Long deliveryPartnerId
) {
}
