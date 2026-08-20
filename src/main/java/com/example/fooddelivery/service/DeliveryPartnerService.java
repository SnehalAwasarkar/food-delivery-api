package com.example.fooddelivery.service;

import com.example.fooddelivery.dto.DeliveryPartnerRequest;
import com.example.fooddelivery.entity.DeliveryPartner;
import com.example.fooddelivery.exception.ResourceNotFoundException;
import com.example.fooddelivery.repository.DeliveryPartnerRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class DeliveryPartnerService {

    private final DeliveryPartnerRepository deliveryPartnerRepository;

    public DeliveryPartnerService(DeliveryPartnerRepository deliveryPartnerRepository) {
        this.deliveryPartnerRepository = deliveryPartnerRepository;
    }

    public DeliveryPartner create(DeliveryPartnerRequest request) {
        DeliveryPartner partner = new DeliveryPartner(request.name(), request.phone());
        return deliveryPartnerRepository.save(partner);
    }

    public DeliveryPartner getById(Long id) {
        return deliveryPartnerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery partner not found: " + id));
    }

    public List<DeliveryPartner> listAll() {
        return deliveryPartnerRepository.findAll();
    }
}
