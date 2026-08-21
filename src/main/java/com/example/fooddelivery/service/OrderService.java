package com.example.fooddelivery.service;

import com.example.fooddelivery.dto.OrderItemRequest;
import com.example.fooddelivery.dto.PlaceOrderRequest;
import com.example.fooddelivery.entity.Customer;
import com.example.fooddelivery.entity.DeliveryPartner;
import com.example.fooddelivery.entity.IdempotencyRecord;
import com.example.fooddelivery.entity.MenuItem;
import com.example.fooddelivery.entity.Order;
import com.example.fooddelivery.entity.OrderItem;
import com.example.fooddelivery.entity.OrderStatus;
import com.example.fooddelivery.entity.Restaurant;
import com.example.fooddelivery.exception.InvalidOrderException;
import com.example.fooddelivery.exception.ResourceNotFoundException;
import com.example.fooddelivery.repository.IdempotencyRecordRepository;
import com.example.fooddelivery.repository.OrderRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final CustomerService customerService;
    private final RestaurantService restaurantService;
    private final MenuItemService menuItemService;
    private final DeliveryPartnerService deliveryPartnerService;
    private final IdempotencyRecordRepository idempotencyRecordRepository;

    private final Duration idempotencyTtl;

    public OrderService(
            OrderRepository orderRepository,
            CustomerService customerService,
            RestaurantService restaurantService,
            MenuItemService menuItemService,
            DeliveryPartnerService deliveryPartnerService,
            IdempotencyRecordRepository idempotencyRecordRepository,
            @Value("${app.order.idempotency.ttl-seconds:30}") long idempotencyTtlSeconds
    ) {
        this.orderRepository = orderRepository;
        this.customerService = customerService;
        this.restaurantService = restaurantService;
        this.menuItemService = menuItemService;
        this.deliveryPartnerService = deliveryPartnerService;
        this.idempotencyRecordRepository = idempotencyRecordRepository;
        this.idempotencyTtl = Duration.ofSeconds(idempotencyTtlSeconds);
    }

    @Transactional
    public Order placeOrder(PlaceOrderRequest request, String idempotencyKey) {
        Customer customer = customerService.getById(request.customerId());
        Restaurant restaurant = restaurantService.getById(request.restaurantId());

        if (!restaurant.isOpen()) {
            throw new InvalidOrderException("Restaurant is closed: " + restaurant.getId());
        }

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
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

        String fingerprint = computeFingerprint(request);
        Instant now = Instant.now();

        try {
            IdempotencyRecord newRecord = new IdempotencyRecord();
            newRecord.setIdempotencyKey(idempotencyKey);
            newRecord.setFingerprint(fingerprint);
            newRecord.setCreatedAt(now);
            newRecord.setOrderId(null);
            newRecord = idempotencyRecordRepository.save(newRecord);
        } catch (DataIntegrityViolationException ex) {
            // Another concurrent request inserted the same idempotencyKey.
        }

        IdempotencyRecord existingRecord = idempotencyRecordRepository
                .findByIdempotencyKey(idempotencyKey)
                .orElseThrow(() -> new InvalidOrderException("Idempotency record missing after insert attempt"));

        boolean expired = existingRecord.getCreatedAt() == null
                || existingRecord.getCreatedAt().plus(idempotencyTtl).isBefore(now);

        if (!expired) {
            if (!existingRecord.getFingerprint().equals(fingerprint)) {
                throw new InvalidOrderException("Idempotency-Key used with different request body");
            }

            if (existingRecord.getOrderId() != null) {
                return orderRepository.findById(existingRecord.getOrderId())
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Order not found for idempotency key: " + idempotencyKey));
            }

            // Record exists and body matches, but orderId not set yet. Let the client retry shortly.
            throw new InvalidOrderException("Idempotency request is already in progress");
        }

        // Expired: allow creating a new record and a new order exactly once.
        // (We update in-place since we have a unique key; we rely on fingerprint check + TTL.
        //  This keeps the implementation small and consistent with the existing repository usage.)
        if (!existingRecord.getFingerprint().equals(fingerprint)) {
            existingRecord.setFingerprint(fingerprint);
        }
        existingRecord.setCreatedAt(now);
        existingRecord.setOrderId(null);
        existingRecord = idempotencyRecordRepository.save(existingRecord);

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
        order = orderRepository.save(order);

        existingRecord.setOrderId(order.getId());
        idempotencyRecordRepository.save(existingRecord);
        return order;
    }

    private String computeFingerprint(PlaceOrderRequest request) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            // Stable fingerprint: sort items by menuItemId so request ordering doesn't matter.
            List<OrderItemRequest> sortedItems = request.items().stream()
                    .sorted(Comparator.comparing(OrderItemRequest::menuItemId))
                    .toList();

            StringBuilder sb = new StringBuilder();
            sb.append("customerId=").append(request.customerId()).append("; ");
            sb.append("restaurantId=").append(request.restaurantId()).append("; ");
            sb.append("items=[");
            for (int i = 0; i < sortedItems.size(); i++) {
                OrderItemRequest item = sortedItems.get(i);
                if (i > 0) {
                    sb.append(",");
                }
                sb.append(item.menuItemId()).append(":").append(item.quantity());
            }
            sb.append("]");

            byte[] hash = digest.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (Exception ex) {
            throw new InvalidOrderException("Unable to compute idempotency fingerprint");
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
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
