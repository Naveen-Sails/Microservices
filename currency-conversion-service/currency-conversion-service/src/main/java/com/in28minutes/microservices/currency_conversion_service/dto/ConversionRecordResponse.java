package com.in28minutes.microservices.currency_conversion_service.dto;

import com.in28minutes.microservices.currency_conversion_service.entity.ConversionRecord;

import java.math.BigDecimal;
import java.time.Instant;

public record ConversionRecordResponse(
        Long id,
        Long exchangeRateId,
        String from,
        String to,
        BigDecimal quantity,
        BigDecimal conversionMultiple,
        BigDecimal totalCalculatedAmount,
        String environment,
        Instant createdAt) {

    public static ConversionRecordResponse from(ConversionRecord record) {
        return new ConversionRecordResponse(
                record.getId(),
                record.getExchangeRateId(),
                record.getFrom(),
                record.getTo(),
                record.getQuantity(),
                record.getConversionMultiple(),
                record.getTotalCalculatedAmount(),
                record.getEnvironment(),
                record.getCreatedAt());
    }
}
