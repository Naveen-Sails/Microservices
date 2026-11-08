package com.in28minutes.microservices.api_gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

// 1. Tell Spring to manage this class
@Component
public class LoggingFilter implements GlobalFilter {

    // 2. Create a logger for this class
    private Logger logger = LoggerFactory.getLogger(LoggingFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

        // 3. Extract the path from the request and log it
        logger.info("Path of the request received is {}", exchange.getRequest().getPath());

        // 4. Let the request continue to its destination
        return chain.filter(exchange);
    }
}
