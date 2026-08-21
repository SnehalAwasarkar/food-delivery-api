package com.example.fooddelivery.service;

import com.example.fooddelivery.dto.OrderItemRequest;
import com.example.fooddelivery.dto.PlaceOrderRequest;
import com.example.fooddelivery.entity.Customer;
import com.example.fooddelivery.entity.DeliveryPartner;
import com.example.fooddelivery.entity.MenuItem;
import com.example.fooddelivery.entity.Order;
import com.example.fooddelivery.entity.OrderItem;
import com.example.fooddelivery.entity.OrderStatus;
import com.example.fooddelivery.entity.Restaurant;
import com.example.fooddelivery.exception.InvalidOrderException;
import com.example.fooddelivery.exception.ResourceNotFoundException;
import com.example.fooddelivery.repository.OrderRepository;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final CustomerService customerService;
    private final RestaurantService restaurantService;
    private final MenuItemService menuItemService;
    private final DeliveryPartnerService deliveryPartnerService;

    public OrderService(
            OrderRepository orderRepository,
            CustomerService customerService,
            RestaurantService restaurantService,
            MenuItemService menuItemService,
            DeliveryPartnerService deliveryPartnerService
    ) {
        this.orderRepository = orderRepository;
        this.customerService = customerService;
        this.restaurantService = restaurantService;
        this.menuItemService = menuItemService;
        this.deliveryPartnerService = deliveryPartnerService;
    }

    @Transactional
    public Order placeOrder(PlaceOrderRequest request) {
        Customer customer = customerService.getById(request.customerId());
        Restaurant restaurant = restaurantService.getById(request.restaurantId());

        if (!restaurant.isOpen()) {
            throw new InvalidOrderException("Restaurant is closed: " + restaurant.getId());
        }

        Order order = new Order(customer, restaurant);
        BigDecimal total = BigDecimal.ZERO;

        for (OrderItemRequest itemRequest : request.items()) {
            MenuItem menuItem = menuItemService.getById(itemRequest.menuItemId());

            if (!menuItem.getRestaurant().getId().equals(restaurant.getId())) {
                throw new InvalidOrderException(
                        "Menu item " + menuItem.getId() + " does not belong to restaurant " + restaurant.getId());
            }
            if (!menuItem.isAvailable()) {
                throw new InvalidOrderException("Menu item is not available: " + menuItem.getId());
            }

            BigDecimal lineTotal = menuItem.getPrice().multiply(BigDecimal.valueOf(itemRequest.quantity()));
            total = total.add(lineTotal);
            order.addItem(new OrderItem(menuItem, itemRequest.quantity(), menuItem.getPrice()));
        }

        order.setTotalAmount(total);
        return orderRepository.save(order);
    }

    public Order getById(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + id));
    }

    public List<Order> listByCustomer(Long customerId) {
        customerService.getById(customerId);
        return orderRepository.findByCustomerId(customerId);
    }

    public List<Order> listByRestaurant(Long restaurantId) {
        restaurantService.getById(restaurantId);
        return orderRepository.findByRestaurantId(restaurantId);
    }

    @Transactional
    public Order updateStatus(Long orderId, String status) {
        Order order = getById(orderId);
        OrderStatus newStatus;
        try {
            newStatus = OrderStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new InvalidOrderException("Invalid order status: " + status);
        }
        order.setStatus(newStatus);
        return orderRepository.save(order);
    }

    @Transactional
    public Order assignPartner(Long orderId, Long partnerId) {
        Order order = getById(orderId);
        DeliveryPartner partner = deliveryPartnerService.getById(partnerId);

        if (!partner.isAvailable()) {
            throw new InvalidOrderException("Delivery partner is not available: " + partner.getId());
        }

        order.setDeliveryPartner(partner);
        partner.setAvailable(false);

        // Persist both in the same transaction via the owning side (order) + partner (entity managed but ensure update happens).
        deliveryPartnerService.save(partner);
        return orderRepository.save(order);
    }

    @Transactional
    public Order completeDelivery(Long orderId) {
        Order order = getById(orderId);

        if (order.getDeliveryPartner() == null) {
            throw new InvalidOrderException("Order has no delivery partner: " + order.getId());
        }
        if (order.getStatus() == OrderStatus.DELIVERED) {
            throw new InvalidOrderException("Order is already completed: " + order.getId());
        }

        order.setStatus(OrderStatus.DELIVERED);
        DeliveryPartner partner = order.getDeliveryPartner();
        partner.setAvailable(true);

        deliveryPartnerService.save(partner);
        return orderRepository.save(order);
    }
}
