package com.example.fooddelivery.dto;

import com.example.fooddelivery.entity.Customer;

public record CustomerResponse(
        Long id,
        String name,
        String email,
        String phone,
        String address
) {
    public static CustomerResponse from(Customer customer) {
        return new CustomerResponse(
                customer.getId(),
                customer.getName(),
                customer.getEmail(),
                customer.getPhone(),
                customer.getAddress()
        );
    }
}
