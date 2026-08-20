package com.example.fooddelivery.controller;

import com.example.fooddelivery.dto.AssignPartnerRequest;
import com.example.fooddelivery.dto.OrderResponse;
import com.example.fooddelivery.dto.OrderStatusUpdateRequest;
import com.example.fooddelivery.dto.PlaceOrderRequest;
import com.example.fooddelivery.service.OrderService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> placeOrder(@Valid @RequestBody PlaceOrderRequest request) {
        OrderResponse response = OrderResponse.from(orderService.placeOrder(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public OrderResponse getById(@PathVariable Long id) {
        return OrderResponse.from(orderService.getById(id));
    }

    @GetMapping
    public List<OrderResponse> list(
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) Long restaurantId) {
        if (customerId != null) {
            return orderService.listByCustomer(customerId).stream().map(OrderResponse::from).toList();
        }
        if (restaurantId != null) {
            return orderService.listByRestaurant(restaurantId).stream().map(OrderResponse::from).toList();
        }
        throw new IllegalArgumentException("Either customerId or restaurantId query param is required");
    }

    @PatchMapping("/{id}/status")
    public OrderResponse updateStatus(@PathVariable Long id, @Valid @RequestBody OrderStatusUpdateRequest request) {
        return OrderResponse.from(orderService.updateStatus(id, request.status()));
    }

    @PatchMapping("/{id}/delivery-partner")
    public OrderResponse assignPartner(@PathVariable Long id, @Valid @RequestBody AssignPartnerRequest request) {
        return OrderResponse.from(orderService.assignPartner(id, request.deliveryPartnerId()));
    }
}
