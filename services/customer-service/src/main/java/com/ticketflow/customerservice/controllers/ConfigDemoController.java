package com.ticketflow.customerservice.controllers;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Shows a value read from the Config Server. Thanks to @RefreshScope the bean is
 * rebuilt after POST /actuator/refresh, so a new value in Git is picked up
 * without restarting the service.
 */
@RestController
@RefreshScope
public class ConfigDemoController {

    private final String message;

    public ConfigDemoController(@Value("${ticketflow.message}") String message) {
        this.message = message;
    }

    @GetMapping("/api/config/message")
    Map<String, String> message() {
        return Map.of("message", message);
    }
}
