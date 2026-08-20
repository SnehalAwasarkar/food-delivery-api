package com.example.fooddelivery.dto;

import com.example.fooddelivery.entity.Restaurant;

public record RestaurantResponse(
        Long id,
        String name,
        String cuisine,
        String address,
        boolean open
) {
    public static RestaurantResponse from(Restaurant restaurant) {
        return new RestaurantResponse(
                restaurant.getId(),
                restaurant.getName(),
                restaurant.getCuisine(),
                restaurant.getAddress(),
                restaurant.isOpen()
        );
    }
}
