package com.example.fooddelivery.dto;

import com.example.fooddelivery.entity.DeliveryPartner;

public record DeliveryPartnerResponse(
        Long id,
        String name,
        String phone,
        boolean available
) {
    public static DeliveryPartnerResponse from(DeliveryPartner partner) {
        return new DeliveryPartnerResponse(
                partner.getId(),
                partner.getName(),
                partner.getPhone(),
                partner.isAvailable()
        );
    }
}
