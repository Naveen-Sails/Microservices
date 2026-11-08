package com.in28minutes.microservices.currency_conversion_service.service;

import com.in28minutes.microservices.currency_conversion_service.client.CurrencyExchangeProxy;
import com.in28minutes.microservices.currency_conversion_service.dto.CurrencyConversion;
import com.in28minutes.microservices.currency_conversion_service.entity.ConversionRecord;
import com.in28minutes.microservices.currency_conversion_service.exception.CurrencyRateUnavailableException;
import com.in28minutes.microservices.currency_conversion_service.repository.ConversionRecordRepository;
import feign.FeignException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;

@Service
public class CurrencyConversionService {

    private static final BigDecimal MAX_QUANTITY = new BigDecimal("999999999");
    private static final BigDecimal IDENTITY_RATE = BigDecimal.ONE;
    private static final int RESULT_SCALE = 2;
    private static final int MAX_HISTORY_LIMIT = 100;

    private final CurrencyExchangeProxy exchangeProxy;
    private final ConversionRecordRepository conversionRecordRepository;

    public CurrencyConversionService(
            CurrencyExchangeProxy exchangeProxy,
            ConversionRecordRepository conversionRecordRepository) {
        this.exchangeProxy = exchangeProxy;
        this.conversionRecordRepository = conversionRecordRepository;
    }

    public CurrencyConversion calculate(String from, String to, BigDecimal quantity) {
        String normalizedFrom = normalizeCurrency(from, "from");
        String normalizedTo = normalizeCurrency(to, "to");
        validateQuantity(quantity);

        if (normalizedFrom.equals(normalizedTo)) {
            return createConversion(null, normalizedFrom, normalizedTo, quantity,
                    IDENTITY_RATE, "same-currency");
        }

        CurrencyConversion exchangeRate;
        try {
            exchangeRate = exchangeProxy.retrieveExchangeValue(normalizedFrom, normalizedTo);
        } catch (FeignException exception) {
            throw new CurrencyRateUnavailableException(
                    "Exchange rate is currently unavailable for "
                            + normalizedFrom + " to " + normalizedTo,
                    exception);
        }

        if (exchangeRate == null || exchangeRate.getConversionMultiple() == null
                || exchangeRate.getConversionMultiple().signum() <= 0) {
            throw new CurrencyRateUnavailableException(
                    "Exchange service returned an invalid rate for "
                            + normalizedFrom + " to " + normalizedTo);
        }

        return createConversion(exchangeRate.getId(), normalizedFrom, normalizedTo, quantity,
                exchangeRate.getConversionMultiple(), exchangeRate.getEnvironment());
    }

    public ConversionRecord convertAndRecord(String from, String to, BigDecimal quantity) {
        CurrencyConversion conversion = calculate(from, to, quantity);
        return conversionRecordRepository.save(new ConversionRecord(
                conversion.getId(),
                conversion.getFrom(),
                conversion.getTo(),
                conversion.getQuantity(),
                conversion.getConversionMultiple(),
                conversion.getTotalCalculatedAmount(),
                conversion.getEnvironment()));
    }

    @Transactional(readOnly = true)
    public List<ConversionRecord> getRecentConversions(int limit) {
        if (limit < 1 || limit > MAX_HISTORY_LIMIT) {
            throw new IllegalArgumentException(
                    "History limit must be between 1 and " + MAX_HISTORY_LIMIT);
        }
        return conversionRecordRepository.findTop100ByOrderByCreatedAtDesc()
                .stream()
                .limit(limit)
                .toList();
    }

    @Transactional(readOnly = true)
    public ConversionRecord getConversion(Long id) {
        if (id == null || id < 1) {
            throw new IllegalArgumentException("Conversion ID must be positive");
        }
        return conversionRecordRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException(
                        "Conversion record " + id + " was not found"));
    }

    private CurrencyConversion createConversion(
            Long exchangeRateId,
            String from,
            String to,
            BigDecimal quantity,
            BigDecimal rate,
            String environment) {
        // Currency amounts use two fractional digits and half-even rounding.
        BigDecimal total = quantity.multiply(rate).setScale(RESULT_SCALE, RoundingMode.HALF_EVEN);
        return new CurrencyConversion(exchangeRateId, from, to, quantity, rate, total, environment);
    }

    private String normalizeCurrency(String currency, String fieldName) {
        if (currency == null || !currency.trim().matches("[A-Za-z]{3}")) {
            throw new IllegalArgumentException(
                    fieldName + " must be a three-letter currency code");
        }
        return currency.trim().toUpperCase(Locale.ROOT);
    }

    private void validateQuantity(BigDecimal quantity) {
        if (quantity == null || quantity.signum() <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero");
        }
        if (quantity.compareTo(MAX_QUANTITY) > 0) {
            throw new IllegalArgumentException("Quantity must not exceed " + MAX_QUANTITY);
        }
        if (Math.max(0, quantity.stripTrailingZeros().scale()) > 6) {
            throw new IllegalArgumentException("Quantity supports at most six decimal places");
        }
    }
}
