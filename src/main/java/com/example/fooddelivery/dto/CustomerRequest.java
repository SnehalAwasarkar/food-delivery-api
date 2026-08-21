package com.example.fooddelivery.dto;

import com.example.fooddelivery.entity.IdType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record CustomerRequest(
        @NotBlank String name,
        @NotBlank @Email String email,
        String phone,
        String address,
        @NotNull IdType idType,
        @NotNull @Size(max = 64) String idNumber,
        @NotNull @Past LocalDate dateOfBirth
) {
}
