package com.example.fooddelivery;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.fooddelivery.dto.AssignPartnerRequest;
import com.example.fooddelivery.dto.CustomerRequest;
import com.example.fooddelivery.dto.DeliveryPartnerRequest;
import com.example.fooddelivery.dto.MenuItemRequest;
import com.example.fooddelivery.dto.OrderItemRequest;
import com.example.fooddelivery.dto.PlaceOrderRequest;
import com.example.fooddelivery.dto.RestaurantRequest;
import com.example.fooddelivery.entity.Customer;
import com.example.fooddelivery.entity.DeliveryPartner;
import com.example.fooddelivery.entity.IdempotencyRecord;
import com.example.fooddelivery.entity.MenuItem;
import com.example.fooddelivery.entity.Order;
import com.example.fooddelivery.entity.OrderItem;
import com.example.fooddelivery.entity.Restaurant;
import com.example.fooddelivery.repository.DeliveryPartnerRepository;
import com.example.fooddelivery.repository.IdempotencyRecordRepository;
import com.example.fooddelivery.repository.MenuItemRepository;
import com.example.fooddelivery.repository.OrderRepository;
import com.example.fooddelivery.repository.RestaurantRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class OrderPlacementIdempotencyConcurrencyTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private com.example.fooddelivery.repository.CustomerRepository customerRepository;

    @Autowired
    private RestaurantRepository restaurantRepository;

    @Autowired
    private MenuItemRepository menuItemRepository;

    @Autowired
    private DeliveryPartnerRepository deliveryPartnerRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private IdempotencyRecordRepository idempotencyRecordRepository;

    @Test
    @Transactional
    void exactlyOneOrderCreatedUnderDoubleTapIdempotencyKey() throws Exception {
        var restaurant = restaurantRepository.save(new Restaurant("R1", "Cuisine", "Address"));
        restaurant.setOpen(true);
        restaurant = restaurantRepository.save(restaurant);

        var customer = customerRepository.save(new Customer("C1", "c1@example.com", "111", "Addr"));

        var menuItem = menuItemRepository.save(new MenuItem(restaurant, "Burger", BigDecimal.valueOf(10), true));

        // Setup not strictly required for place order, but keep domain consistent with other tests.
        var partner = deliveryPartnerRepository.save(new DeliveryPartner("P1", "222"));
        partner.setAvailable(true);
        deliveryPartnerRepository.save(partner);

        var request = new PlaceOrderRequest(
                customer.getId(),
                restaurant.getId(),
                List.of(new OrderItemRequest(menuItem.getId(), 1))
        );

        String idempotencyKey = "idem-" + System.currentTimeMillis();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        Callable<ResponseEntity<String>> task1 = () -> {
            start.await();
            HttpHeaders headers = new HttpHeaders();
            headers.add("Idempotency-Key", idempotencyKey);
            HttpEntity<PlaceOrderRequest> entity = new HttpEntity<>(request, headers);
            return restTemplate.postForEntity("/api/orders", entity, String.class);
        };

        Callable<ResponseEntity<String>> task2 = () -> {
            start.await();
            HttpHeaders headers = new HttpHeaders();
            headers.add("Idempotency-Key", idempotencyKey);
            HttpEntity<PlaceOrderRequest> entity = new HttpEntity<>(request, headers);
            return restTemplate.postForEntity("/api/orders", entity, String.class);
        };

        Future<ResponseEntity<String>> f1 = executor.submit(task1);
        Future<ResponseEntity<String>> f2 = executor.submit(task2);

        start.countDown();

        ResponseEntity<String> r1;
        ResponseEntity<String> r2;
        try {
            r1 = f1.get();
            r2 = f2.get();
        } finally {
            executor.shutdownNow();
        }

        List<Integer> codes = List.of(r1.getStatusCodeValue(), r2.getStatusCodeValue());
        assertThat(codes).contains(201);

        // Only one order should exist for this customer+restaurant (within this test scope).
        List<Order> orders = orderRepository.findByCustomerId(customer.getId());
        assertThat(orders).hasSize(1);

        IdempotencyRecord record = idempotencyRecordRepository.findByIdempotencyKey(idempotencyKey).orElseThrow();
        assertThat(record.getOrderId()).isNotNull();
        assertThat(record.getOrderId()).isEqualTo(orders.get(0).getId());
    }
}
