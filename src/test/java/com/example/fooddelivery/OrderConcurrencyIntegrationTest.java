package com.example.fooddelivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.fooddelivery.dto.CustomerRequest;
import com.example.fooddelivery.dto.DeliveryPartnerRequest;
import com.example.fooddelivery.dto.MenuItemRequest;
import com.example.fooddelivery.dto.OrderItemRequest;
import com.example.fooddelivery.dto.PlaceOrderRequest;
import com.example.fooddelivery.dto.RestaurantRequest;
import com.example.fooddelivery.entity.MenuItem;
import com.example.fooddelivery.repository.MenuItemRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
public class OrderConcurrencyIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MenuItemRepository menuItemRepository;

    private static ExecutorService executor;

    @BeforeAll
    static void setUp() {
        executor = Executors.newFixedThreadPool(2);
    }

    @AfterAll
    static void tearDown() {
        executor.shutdown();
    }

    @Test
    void concurrentOrders_onlyOneSucceeds_whenSingleUnitMenuItemIsConsumed() throws Exception {
        // Create one open restaurant
        String restaurantPayload = objectMapper.writeValueAsString(new RestaurantRequest(
                "R1",
                "Italian",
                "Somewhere",
                true
        ));
        var restaurantResult = mockMvc.perform(post("/api/restaurants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(restaurantPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("id").exists())
                .andReturn();
        Long restaurantId = objectMapper.readTree(restaurantResult.getResponse().getContentAsString()).get("id").asLong();

        // Create one menu item (single-unit available)
        String menuItemPayload = objectMapper.writeValueAsString(new MenuItemRequest(
                "Pasta",
                new BigDecimal("12.50")
        ));
        var menuItemResult = mockMvc.perform(post("/api/restaurants/" + restaurantId + "/menu-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(menuItemPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("id").exists())
                .andReturn();
        Long menuItemId = objectMapper.readTree(menuItemResult.getResponse().getContentAsString()).get("id").asLong();

        MenuItem initially = menuItemRepository.findById(menuItemId).orElseThrow();
        assertThat(initially.isAvailable()).isTrue();

        // Two customers
        Long customer1Id = createCustomer(new CustomerRequest(
                "C1",
                "c1@example.com",
                "111",
                "Addr1"
        ));
        Long customer2Id = createCustomer(new CustomerRequest(
                "C2",
                "c2@example.com",
                "222",
                "Addr2"
        ));

        // Place two orders concurrently, both consuming the same single-unit item (quantity is irrelevant to flag; keep 1 per spec)
        PlaceOrderRequest order1 = new PlaceOrderRequest(customer1Id, restaurantId,
                List.of(new OrderItemRequest(menuItemId, 1)));
        PlaceOrderRequest order2 = new PlaceOrderRequest(customer2Id, restaurantId,
                List.of(new OrderItemRequest(menuItemId, 1)));

        CompletableFuture<Integer> r1 = CompletableFuture.supplyAsync(() -> {
            try {
                String body = objectMapper.writeValueAsString(order1);
                return mockMvc.perform(post("/api/orders")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                        .andReturn().getResponse().getStatus();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, executor);

        CompletableFuture<Integer> r2 = CompletableFuture.supplyAsync(() -> {
            try {
                String body = objectMapper.writeValueAsString(order2);
                return mockMvc.perform(post("/api/orders")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                        .andReturn().getResponse().getStatus();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, executor);

        CompletableFuture.allOf(r1, r2).join();

        int status1 = r1.get();
        int status2 = r2.get();

        int createdCount = 0;
        int conflictCount = 0;
        if (status1 == 201) createdCount++; else if (status1 == 409) conflictCount++;
        if (status2 == 201) createdCount++; else if (status2 == 409) conflictCount++;

        assertThat(createdCount).isEqualTo(1);
        assertThat(conflictCount).isEqualTo(1);

        // Verify conflict message is observable and exact
        // (Re-submit one call that should now fail deterministically.)
        String body = objectMapper.writeValueAsString(order2);
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("message").value("Item not available"));

        // Final state in DB must be sold out
        MenuItem after = menuItemRepository.findById(menuItemId).orElseThrow();
        assertThat(after.isAvailable()).isFalse();
    }

    private Long createCustomer(CustomerRequest request) throws Exception {
        String payload = objectMapper.writeValueAsString(request);
        var result = mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }
}
