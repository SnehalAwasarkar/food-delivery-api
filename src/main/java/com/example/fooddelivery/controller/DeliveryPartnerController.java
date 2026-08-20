package com.example.fooddelivery.controller;

import com.example.fooddelivery.dto.DeliveryPartnerRequest;
import com.example.fooddelivery.dto.DeliveryPartnerResponse;
import com.example.fooddelivery.service.DeliveryPartnerService;
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
@RequestMapping("/api/delivery-partners")
public class DeliveryPartnerController {

    private final DeliveryPartnerService deliveryPartnerService;

    public DeliveryPartnerController(DeliveryPartnerService deliveryPartnerService) {
        this.deliveryPartnerService = deliveryPartnerService;
    }

    @PostMapping
    public ResponseEntity<DeliveryPartnerResponse> create(@Valid @RequestBody DeliveryPartnerRequest request) {
        DeliveryPartnerResponse response = DeliveryPartnerResponse.from(deliveryPartnerService.create(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public List<DeliveryPartnerResponse> listAll() {
        return deliveryPartnerService.listAll().stream().map(DeliveryPartnerResponse::from).toList();
    }

    @GetMapping("/{id}")
    public DeliveryPartnerResponse getById(@PathVariable Long id) {
        return DeliveryPartnerResponse.from(deliveryPartnerService.getById(id));
    }
}
