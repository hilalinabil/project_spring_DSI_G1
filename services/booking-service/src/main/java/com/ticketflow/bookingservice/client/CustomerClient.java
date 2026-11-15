package com.ticketflow.bookingservice.client;

import com.ticketflow.bookingservice.client.dto.CustomerResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/** customer-service, resolved through Eureka (no url attribute). */
@FeignClient(name = "customer-service")
public interface CustomerClient {

    @GetMapping("/api/customers/{id}")
    CustomerResponse getCustomer(@PathVariable("id") Long id);
}
