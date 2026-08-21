package com.example.fooddelivery.service;

import com.example.fooddelivery.dto.CustomerRequest;
import com.example.fooddelivery.entity.Customer;
import com.example.fooddelivery.entity.IdType;
import com.example.fooddelivery.exception.ResourceNotFoundException;
import com.example.fooddelivery.repository.CustomerRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;

    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    public Customer create(CustomerRequest request) {
        Customer customer = new Customer(request.name(), request.email(), request.phone(), request.address());
        customer.setIdType(request.idType());
        customer.setIdNumber(request.idNumber());
        customer.setDateOfBirth(request.dateOfBirth());
        return customerRepository.save(customer);
    }

    public Customer getById(Long id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found: " + id));
    }

    public List<Customer> listAll() {
        return customerRepository.findAll();
    }

    public Customer update(Long id, CustomerRequest request) {
        Customer customer = getById(id);
        customer.setName(request.name());
        customer.setEmail(request.email());
        customer.setPhone(request.phone());
        customer.setAddress(request.address());
        customer.setIdType((IdType) request.idType());
        customer.setIdNumber(request.idNumber());
        customer.setDateOfBirth(request.dateOfBirth());
        return customerRepository.save(customer);
    }
}
