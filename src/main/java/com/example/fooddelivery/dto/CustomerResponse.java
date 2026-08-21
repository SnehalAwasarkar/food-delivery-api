package com.example.fooddelivery.dto;

import com.example.fooddelivery.entity.Customer;
import com.example.fooddelivery.entity.IdType;
import java.time.LocalDate;

public record CustomerResponse(
        Long id,
        String name,
        String email,
        String phone,
        String address,
        IdType idType,
        String idNumber,
        LocalDate dateOfBirth
) {
    public static CustomerResponse from(Customer customer) {
        return new CustomerResponse(
                customer.getId(),
                customer.getName(),
                customer.getEmail(),
                customer.getPhone(),
                customer.getAddress(),
                customer.getIdType(),
                customer.getIdNumber(),
                customer.getDateOfBirth()
        );
    }
}
