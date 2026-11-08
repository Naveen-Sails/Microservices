package com.in28minutes.microservices.currency_conversion_service.service;

import com.in28minutes.microservices.currency_conversion_service.dto.CurrencyConversion;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CurrencyConversionServiceTests {

    @Test
    void normalizesCurrencyCodesAndRoundsAmount() {
        CurrencyConversionService service = new CurrencyConversionService(
                (from, to) -> new CurrencyConversion(
                        7L, from, to, null, new BigDecimal("1.2345"), null, "8000"),
                null);

        CurrencyConversion result = service.calculate(" usd ", "eur", new BigDecimal("2"));

        assertEquals("USD", result.getFrom());
        assertEquals("EUR", result.getTo());
        assertEquals(new BigDecimal("2.47"), result.getTotalCalculatedAmount());
    }

    @Test
    void sameCurrencyConversionUsesIdentityRateWithoutCallingExchangeService() {
        CurrencyConversionService service = new CurrencyConversionService(
                (from, to) -> {
                    throw new AssertionError("Exchange service should not be called");
                },
                null);

        CurrencyConversion result = service.calculate("jpy", "JPY", new BigDecimal("10.50"));

        assertEquals(BigDecimal.ONE, result.getConversionMultiple());
        assertEquals(new BigDecimal("10.50"), result.getTotalCalculatedAmount());
        assertEquals("same-currency", result.getEnvironment());
    }

    @Test
    void rejectsNonPositiveQuantityBeforeCallingExchangeService() {
        CurrencyConversionService service = new CurrencyConversionService(
                (from, to) -> {
                    throw new AssertionError("Exchange service should not be called");
                },
                null);

        assertThrows(IllegalArgumentException.class,
                () -> service.calculate("USD", "EUR", BigDecimal.ZERO));
    }
}
