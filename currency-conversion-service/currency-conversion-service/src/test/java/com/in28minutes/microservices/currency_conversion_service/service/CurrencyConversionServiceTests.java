package com.in28minutes.microservices.currency_conversion_service.service;

import com.in28minutes.microservices.currency_conversion_service.client.CurrencyExchangeProxy;
import com.in28minutes.microservices.currency_conversion_service.dto.CurrencyConversion;
import com.in28minutes.microservices.currency_conversion_service.entity.ConversionRecord;
import com.in28minutes.microservices.currency_conversion_service.exception.CurrencyRateUnavailableException;
import com.in28minutes.microservices.currency_conversion_service.repository.ConversionRecordRepository;
import feign.FeignException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurrencyConversionServiceTests {

    @Mock
    private CurrencyExchangeProxy exchangeProxy;

    @Mock
    private ConversionRecordRepository conversionRecordRepository;

    @InjectMocks
    private CurrencyConversionService conversionService;

    @Test
    void calculateNormalizesCurrenciesAndRoundsResult() {
        when(exchangeProxy.retrieveExchangeValue("USD", "EUR"))
                .thenReturn(exchangeRate(7L, "1.2345", "8000"));

        CurrencyConversion result = conversionService.calculate(
                " usd ", "eur", new BigDecimal("2"));

        assertAll(
                () -> assertEquals("USD", result.getFrom()),
                () -> assertEquals("EUR", result.getTo()),
                () -> assertEquals(7L, result.getId()),
                () -> assertEquals(new BigDecimal("1.2345"), result.getConversionMultiple()),
                () -> assertEquals(new BigDecimal("2.47"), result.getTotalCalculatedAmount()),
                () -> assertEquals("8000", result.getEnvironment()));
        verify(exchangeProxy).retrieveExchangeValue("USD", "EUR");
        verifyNoInteractions(conversionRecordRepository);
    }

    @Test
    void calculateSameCurrencyUsesIdentityRateWithoutCallingExchangeService() {
        CurrencyConversion result = conversionService.calculate(
                "jpy", "JPY", new BigDecimal("10.50"));

        assertAll(
                () -> assertEquals("JPY", result.getFrom()),
                () -> assertEquals("JPY", result.getTo()),
                () -> assertEquals(BigDecimal.ONE, result.getConversionMultiple()),
                () -> assertEquals(new BigDecimal("10.50"), result.getTotalCalculatedAmount()),
                () -> assertEquals("same-currency", result.getEnvironment()));
        verifyNoInteractions(exchangeProxy, conversionRecordRepository);
    }

    @Test
    void calculateRejectsMalformedCurrencyCodes() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> conversionService.calculate(null, "EUR", BigDecimal.ONE)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> conversionService.calculate("US", "EUR", BigDecimal.ONE)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> conversionService.calculate("US1", "EUR", BigDecimal.ONE)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> conversionService.calculate("USD", "EURO", BigDecimal.ONE)));

        verifyNoInteractions(exchangeProxy, conversionRecordRepository);
    }

    @Test
    void calculateRejectsInvalidQuantities() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> conversionService.calculate("USD", "EUR", null)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> conversionService.calculate("USD", "EUR", BigDecimal.ZERO)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> conversionService.calculate("USD", "EUR", new BigDecimal("-0.01"))),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> conversionService.calculate("USD", "EUR", new BigDecimal("1000000000"))),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> conversionService.calculate("USD", "EUR", new BigDecimal("1.1234567"))));

        verifyNoInteractions(exchangeProxy, conversionRecordRepository);
    }

    @Test
    void calculateWrapsExchangeServiceFailureAsRateUnavailable() {
        FeignException feignException = mock(FeignException.class);
        when(exchangeProxy.retrieveExchangeValue("USD", "EUR")).thenThrow(feignException);

        CurrencyRateUnavailableException exception = assertThrows(
                CurrencyRateUnavailableException.class,
                () -> conversionService.calculate("USD", "EUR", BigDecimal.ONE));

        assertSame(feignException, exception.getCause());
        verify(exchangeProxy).retrieveExchangeValue("USD", "EUR");
        verifyNoInteractions(conversionRecordRepository);
    }

    @Test
    void calculateRejectsMissingAndInvalidExchangeRates() {
        doReturn(null).when(exchangeProxy).retrieveExchangeValue("USD", "EUR");
        assertThrows(CurrencyRateUnavailableException.class,
                () -> conversionService.calculate("USD", "EUR", BigDecimal.ONE));

        doReturn(exchangeRate(1L, null, "8000"))
                .when(exchangeProxy).retrieveExchangeValue("USD", "EUR");
        assertThrows(CurrencyRateUnavailableException.class,
                () -> conversionService.calculate("USD", "EUR", BigDecimal.ONE));

        doReturn(exchangeRate(1L, "0", "8000"))
                .when(exchangeProxy).retrieveExchangeValue("USD", "EUR");
        assertThrows(CurrencyRateUnavailableException.class,
                () -> conversionService.calculate("USD", "EUR", BigDecimal.ONE));

        doReturn(exchangeRate(1L, "-0.5", "8000"))
                .when(exchangeProxy).retrieveExchangeValue("USD", "EUR");
        assertThrows(CurrencyRateUnavailableException.class,
                () -> conversionService.calculate("USD", "EUR", BigDecimal.ONE));

        verify(exchangeProxy, times(4)).retrieveExchangeValue("USD", "EUR");
        verifyNoInteractions(conversionRecordRepository);
    }

    @Test
    void convertAndRecordPersistsNormalizedConversionSnapshot() {
        when(exchangeProxy.retrieveExchangeValue("USD", "EUR"))
                .thenReturn(exchangeRate(7L, "1.2345", "8000"));
        when(conversionRecordRepository.save(any(ConversionRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ConversionRecord result = conversionService.convertAndRecord(
                "usd", "eur", new BigDecimal("2"));

        ArgumentCaptor<ConversionRecord> recordCaptor =
                ArgumentCaptor.forClass(ConversionRecord.class);
        verify(conversionRecordRepository).save(recordCaptor.capture());
        ConversionRecord persistedRecord = recordCaptor.getValue();
        assertSame(persistedRecord, result);
        assertAll(
                () -> assertEquals(7L, persistedRecord.getExchangeRateId()),
                () -> assertEquals("USD", persistedRecord.getFrom()),
                () -> assertEquals("EUR", persistedRecord.getTo()),
                () -> assertEquals(new BigDecimal("2"), persistedRecord.getQuantity()),
                () -> assertEquals(new BigDecimal("1.2345"), persistedRecord.getConversionMultiple()),
                () -> assertEquals(new BigDecimal("2.47"), persistedRecord.getTotalCalculatedAmount()),
                () -> assertEquals("8000", persistedRecord.getEnvironment()));
    }

    @Test
    void getRecentConversionsReturnsRequestedRecordsAndRejectsInvalidLimits() {
        ConversionRecord newest = record("USD", "EUR", "1");
        ConversionRecord nextNewest = record("USD", "JPY", "2");
        when(conversionRecordRepository.findTop100ByOrderByCreatedAtDesc())
                .thenReturn(List.of(newest, nextNewest));

        assertEquals(List.of(newest), conversionService.getRecentConversions(1));
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> conversionService.getRecentConversions(-1)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> conversionService.getRecentConversions(0)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> conversionService.getRecentConversions(101)));

        verify(conversionRecordRepository).findTop100ByOrderByCreatedAtDesc();
        verifyNoInteractions(exchangeProxy);
    }

    @Test
    void getConversionReturnsRecordWhenFound() {
        ConversionRecord record = record("USD", "EUR", "12");
        when(conversionRecordRepository.findById(12L)).thenReturn(Optional.of(record));

        assertSame(record, conversionService.getConversion(12L));

        verify(conversionRecordRepository).findById(12L);
        verifyNoInteractions(exchangeProxy);
    }

    @Test
    void getConversionReportsMissingRecordsAndRejectsInvalidIds() {
        when(conversionRecordRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> conversionService.getConversion(99L));
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> conversionService.getConversion(null)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> conversionService.getConversion(0L)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> conversionService.getConversion(-1L)));

        verify(conversionRecordRepository).findById(99L);
        verifyNoInteractions(exchangeProxy);
    }

    private CurrencyConversion exchangeRate(Long id, String rate, String environment) {
        return new CurrencyConversion(
                id,
                "USD",
                "EUR",
                null,
                rate == null ? null : new BigDecimal(rate),
                null,
                environment);
    }

    private ConversionRecord record(String from, String to, String quantity) {
        BigDecimal amount = new BigDecimal(quantity);
        return new ConversionRecord(
                1L, from, to, amount, BigDecimal.ONE, amount.setScale(2), "test");
    }
}
