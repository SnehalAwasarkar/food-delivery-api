package com.example.fooddelivery.service;

import com.example.fooddelivery.dto.MenuItemRequest;
import com.example.fooddelivery.entity.MenuItem;
import com.example.fooddelivery.entity.Restaurant;
import com.example.fooddelivery.exception.ResourceNotFoundException;
import com.example.fooddelivery.repository.MenuItemRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class MenuItemService {

    private final MenuItemRepository menuItemRepository;
    private final RestaurantService restaurantService;

    public MenuItemService(MenuItemRepository menuItemRepository, RestaurantService restaurantService) {
        this.menuItemRepository = menuItemRepository;
        this.restaurantService = restaurantService;
    }

    public MenuItem create(Long restaurantId, MenuItemRequest request) {
        Restaurant restaurant = restaurantService.getById(restaurantId);
        MenuItem menuItem = new MenuItem(restaurant, request.name(), request.price());
        return menuItemRepository.save(menuItem);
    }

    public MenuItem getById(Long id) {
        return menuItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Menu item not found: " + id));
    }

    public List<MenuItem> listByRestaurant(Long restaurantId) {
        restaurantService.getById(restaurantId);
        return menuItemRepository.findByRestaurantId(restaurantId);
    }
}
