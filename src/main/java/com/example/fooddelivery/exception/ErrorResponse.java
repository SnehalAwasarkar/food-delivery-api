package com.example.fooddelivery.exception;

public record ErrorResponse(
        int status,
        String message
) {
}
