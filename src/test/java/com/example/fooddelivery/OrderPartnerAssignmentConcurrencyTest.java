package com.example.fooddelivery;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.fooddelivery.dto.AssignPartnerRequest;
import com.example.fooddelivery.dto.CustomerRequest;
import com.example.fooddelivery.dto.DeliveryPartnerRequest;
import com.example.fooddelivery.dto.MenuItemRequest;
import com.example.fooddelivery.dto.OrderItemRequest;
import com.example.fooddelivery.dto.PlaceOrderRequest;
import com.example.fooddelivery.dto.RestaurantRequest;
import com.example.fooddelivery.entity.DeliveryPartner;
import com.example.fooddelivery.entity.Order;
import com.example.fooddelivery.entity.OrderItem;
import com.example.fooddelivery.entity.OrderStatus;
import com.example.fooddelivery.repository.DeliveryPartnerRepository;
import com.example.fooddelivery.repository.OrderRepository;
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
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class OrderPartnerAssignmentConcurrencyTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private com.example.fooddelivery.repository.CustomerRepository customerRepository;

    @Autowired
    private com.example.fooddelivery.repository.RestaurantRepository restaurantRepository;

    @Autowired
    private com.example.fooddelivery.repository.MenuItemRepository menuItemRepository;

    @Autowired
    private DeliveryPartnerRepository deliveryPartnerRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Test
    @Transactional
    void exactlyOneAssignmentSucceedsInRace() throws Exception {
        var restaurant = restaurantRepository.save(new com.example.fooddelivery.entity.Restaurant("R1", "Cuisine", "Address"));
        restaurant.setOpen(true);
        restaurantRepository.save(restaurant);

        var customer = customerRepository.save(new com.example.fooddelivery.entity.Customer("C1", "c1@example.com", "111", "Addr"));

        var menuItem = menuItemRepository.save(new com.example.fooddelivery.entity.MenuItem(restaurant, "Burger", java.math.BigDecimal.valueOf(10), true));

        var partner = deliveryPartnerRepository.save(new DeliveryPartner("P1", "222"));
        partner.setAvailable(true);
        partner = deliveryPartnerRepository.save(partner);

        Order order1 = orderRepository.save(new Order(customer, restaurant));
        order1.setTotalAmount(java.math.BigDecimal.ZERO);
        orderRepository.save(order1);

        Order order2 = orderRepository.save(new Order(customer, restaurant));
        order2.setTotalAmount(java.math.BigDecimal.ZERO);
        orderRepository.save(order2);

        // Ensure initial status
        assertThat(order1.getStatus()).isEqualTo(OrderStatus.PLACED);
        assertThat(order2.getStatus()).isEqualTo(OrderStatus.PLACED);
        assertThat(partner.isAvailable()).isTrue();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        Callable<ResponseEntity<String>> task1 = () -> {
            start.await();
            AssignPartnerRequest req = new AssignPartnerRequest(partner.getId());
            HttpEntity<AssignPartnerRequest> entity = new HttpEntity<>(req);
            return restTemplate.postForEntity(
                    "/api/orders/" + order1.getId() + "/delivery-partner",
                    entity,
                    String.class
            );
        };

        Callable<ResponseEntity<String>> task2 = () -> {
            start.await();
            AssignPartnerRequest req = new AssignPartnerRequest(partner.getId());
            HttpEntity<AssignPartnerRequest> entity = new HttpEntity<>(req);
            return restTemplate.postForEntity(
                    "/api/orders/" + order2.getId() + "/delivery-partner",
                    entity,
                    String.class
            );
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
        assertThat(codes).containsExactlyInAnyOrder(200, 409);

        DeliveryPartner updatedPartner = deliveryPartnerRepository.findById(partner.getId()).orElseThrow();
        assertThat(updatedPartner.isAvailable()).isFalse();

        Order updated1 = orderRepository.findById(order1.getId()).orElseThrow();
        Order updated2 = orderRepository.findById(order2.getId()).orElseThrow();

        Long assignedCount = 0L
                + (updated1.getDeliveryPartner() != null ? 1L : 0L)
                + (updated2.getDeliveryPartner() != null ? 1L : 0L);

        assertThat(assignedCount).isEqualTo(1L);
    }
}
