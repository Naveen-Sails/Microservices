package com.in28minutes.microservices.currency_conversion_service.controller;

import com.in28minutes.microservices.currency_conversion_service.dto.ConversionRecordResponse;
import com.in28minutes.microservices.currency_conversion_service.dto.ConversionRequest;
import com.in28minutes.microservices.currency_conversion_service.dto.CurrencyConversion;
import com.in28minutes.microservices.currency_conversion_service.service.CurrencyConversionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
public class CurrencyConversionController {

    private final CurrencyConversionService conversionService;

    public CurrencyConversionController(CurrencyConversionService conversionService) {
        this.conversionService = conversionService;
    }

    @GetMapping("/currency-conversion/from/{from}/to/{to}/quantity/{quantity}")
    public CurrencyConversion calculateCurrencyConversion(
            @PathVariable String from,
            @PathVariable String to,
            @PathVariable BigDecimal quantity) {
        return conversionService.calculate(from, to, quantity);
    }

    @GetMapping("/currency-conversion-feign/from/{from}/to/{to}/quantity/{quantity}")
    public CurrencyConversion calculateCurrencyConversionFeign(
            @PathVariable String from,
            @PathVariable String to,
            @PathVariable BigDecimal quantity) {
        return conversionService.calculate(from, to, quantity);
    }

    @PostMapping("/currency-conversion")
    @ResponseStatus(HttpStatus.CREATED)
    public ConversionRecordResponse createConversion(@Valid @RequestBody ConversionRequest request) {
        return ConversionRecordResponse.from(conversionService.convertAndRecord(
                request.getFrom(), request.getTo(), request.getQuantity()));
    }

    @GetMapping("/currency-conversion/history")
    public List<ConversionRecordResponse> getRecentConversions(
            @RequestParam(defaultValue = "20") int limit) {
        return conversionService.getRecentConversions(limit).stream()
                .map(ConversionRecordResponse::from)
                .toList();
    }

    @GetMapping("/currency-conversion/history/{id}")
    public ConversionRecordResponse getConversion(@PathVariable Long id) {
        return ConversionRecordResponse.from(conversionService.getConversion(id));
    }
}
