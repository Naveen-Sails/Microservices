package com.in28minutes.microservices.currency_exchange_service;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

@RestController
public class CircuitBreakerController {

private Logger logger = LoggerFactory.getLogger(CircuitBreakerController.class);

    @GetMapping("/sample-api")
    //@Retry(name = "sample-api", fallbackMethod = "hardcodedResponse") // 2. Apply the default Resilience4j retry logic
    //@CircuitBreaker(name = "default", fallbackMethod = "hardcodedResponse")
    @RateLimiter(name= "default")
    public String sampleApi() {

        // 3. Log every time this method is attempted
        logger.info("Sample API call received");

        // 4. Intentionally cause an error by calling a non-existent URL
//        ResponseEntity<String> forEntity = new RestTemplate().getForEntity(
//                "http://localhost:8080/some-dummy-url",
//                String.class
//        );
//
//        return forEntity.getBody();
        return "sample-api";

    }
    public String hardcodedResponse(Exception ex) {
        return "fallback-response";
    }
}
