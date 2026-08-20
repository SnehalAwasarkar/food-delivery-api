package com.example.fooddelivery.controller;

import com.example.fooddelivery.dto.MenuItemRequest;
import com.example.fooddelivery.dto.MenuItemResponse;
import com.example.fooddelivery.dto.RestaurantRequest;
import com.example.fooddelivery.dto.RestaurantResponse;
import com.example.fooddelivery.service.MenuItemService;
import com.example.fooddelivery.service.RestaurantService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/restaurants")
public class RestaurantController {

    private final RestaurantService restaurantService;
    private final MenuItemService menuItemService;

    public RestaurantController(RestaurantService restaurantService, MenuItemService menuItemService) {
        this.restaurantService = restaurantService;
        this.menuItemService = menuItemService;
    }

    @PostMapping
    public ResponseEntity<RestaurantResponse> create(@Valid @RequestBody RestaurantRequest request) {
        RestaurantResponse response = RestaurantResponse.from(restaurantService.create(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public List<RestaurantResponse> listAll() {
        return restaurantService.listAll().stream().map(RestaurantResponse::from).toList();
    }

    @GetMapping("/{id}")
    public RestaurantResponse getById(@PathVariable Long id) {
        return RestaurantResponse.from(restaurantService.getById(id));
    }

    @PostMapping("/{id}/menu-items")
    public ResponseEntity<MenuItemResponse> addMenuItem(
            @PathVariable Long id, @Valid @RequestBody MenuItemRequest request) {
        MenuItemResponse response = MenuItemResponse.from(menuItemService.create(id, request));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}/menu-items")
    public List<MenuItemResponse> listMenuItems(@PathVariable Long id) {
        return menuItemService.listByRestaurant(id).stream().map(MenuItemResponse::from).toList();
    }
}
