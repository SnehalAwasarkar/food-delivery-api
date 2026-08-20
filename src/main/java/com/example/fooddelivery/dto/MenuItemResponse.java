package com.example.fooddelivery.dto;

import com.example.fooddelivery.entity.MenuItem;
import java.math.BigDecimal;

public record MenuItemResponse(
        Long id,
        Long restaurantId,
        String name,
        BigDecimal price,
        boolean available
) {
    public static MenuItemResponse from(MenuItem menuItem) {
        return new MenuItemResponse(
                menuItem.getId(),
                menuItem.getRestaurant().getId(),
                menuItem.getName(),
                menuItem.getPrice(),
                menuItem.isAvailable()
        );
    }
}
