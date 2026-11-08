package com.in28minutes.microservices.currency_conversion_service.service;

import com.in28minutes.microservices.currency_conversion_service.client.CurrencyExchangeProxy;
import com.in28minutes.microservices.currency_conversion_service.dto.CurrencyConversion;
import com.in28minutes.microservices.currency_conversion_service.entity.ConversionRecord;
import com.in28minutes.microservices.currency_conversion_service.exception.CurrencyRateUnavailableException;
import com.in28minutes.microservices.currency_conversion_service.repository.ConversionRecordRepository;
import feign.FeignException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
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

        assertEquals("USD", result.getFrom());
        assertEquals("EUR", result.getTo());
        assertEquals(7L, result.getId());
        assertEquals(new BigDecimal("1.2345"), result.getConversionMultiple());
        assertEquals(new BigDecimal("2.47"), result.getTotalCalculatedAmount());
        assertEquals("8000", result.getEnvironment());
        verify(exchangeProxy).retrieveExchangeValue("USD", "EUR");
        verifyNoInteractions(conversionRecordRepository);
    }

    @Test
    void calculateSameCurrencyUsesIdentityRateWithoutCallingExchangeService() {
        CurrencyConversion result = conversionService.calculate(
                "jpy", "JPY", new BigDecimal("10.50"));

        assertEquals("JPY", result.getFrom());
        assertEquals("JPY", result.getTo());
        assertEquals(BigDecimal.ONE, result.getConversionMultiple());
        assertEquals(new BigDecimal("10.50"), result.getTotalCalculatedAmount());
        assertEquals("same-currency", result.getEnvironment());
        verifyNoInteractions(exchangeProxy, conversionRecordRepository);
    }

    @ParameterizedTest
    @MethodSource("invalidCurrencyCodes")
    void calculateRejectsInvalidCurrencyCodes(String from, String to) {
        assertThrows(IllegalArgumentException.class,
                () -> conversionService.calculate(from, to, BigDecimal.ONE));

        verifyNoInteractions(exchangeProxy, conversionRecordRepository);
    }

    static Stream<org.junit.jupiter.params.provider.Arguments> invalidCurrencyCodes() {
        return Stream.of(
                org.junit.jupiter.params.provider.Arguments.of(null, "EUR"),
                org.junit.jupiter.params.provider.Arguments.of("US", "EUR"),
                org.junit.jupiter.params.provider.Arguments.of("US1", "EUR"),
                org.junit.jupiter.params.provider.Arguments.of("USD", "EURO"),
                org.junit.jupiter.params.provider.Arguments.of("USD", " E U"));
    }

    @ParameterizedTest
    @MethodSource("invalidQuantities")
    void calculateRejectsInvalidQuantities(BigDecimal quantity) {
        assertThrows(IllegalArgumentException.class,
                () -> conversionService.calculate("USD", "EUR", quantity));

        verifyNoInteractions(exchangeProxy, conversionRecordRepository);
    }

    static Stream<BigDecimal> invalidQuantities() {
        return Stream.of(
                null,
                BigDecimal.ZERO,
                new BigDecimal("-0.01"),
                new BigDecimal("1000000000"),
                new BigDecimal("1.1234567"));
    }

    @Test
    void calculateWrapsExchangeServiceFailureAsRateUnavailable() {
        FeignException feignException = org.mockito.Mockito.mock(FeignException.class);
        when(exchangeProxy.retrieveExchangeValue("USD", "EUR")).thenThrow(feignException);

        CurrencyRateUnavailableException exception = assertThrows(
                CurrencyRateUnavailableException.class,
                () -> conversionService.calculate("USD", "EUR", BigDecimal.ONE));

        assertSame(feignException, exception.getCause());
        verify(exchangeProxy).retrieveExchangeValue("USD", "EUR");
        verifyNoInteractions(conversionRecordRepository);
    }

    @Test
    void calculateRejectsMissingExchangeRate() {
        when(exchangeProxy.retrieveExchangeValue("USD", "EUR")).thenReturn(null);

        assertThrows(CurrencyRateUnavailableException.class,
                () -> conversionService.calculate("USD", "EUR", BigDecimal.ONE));
    }

    @ParameterizedTest
    @MethodSource("invalidExchangeRates")
    void calculateRejectsNullOrNonPositiveExchangeRate(BigDecimal rate) {
        when(exchangeProxy.retrieveExchangeValue("USD", "EUR"))
                .thenReturn(new CurrencyConversion(1L, "USD", "EUR", null, rate, null, "8000"));

        assertThrows(CurrencyRateUnavailableException.class,
                () -> conversionService.calculate("USD", "EUR", BigDecimal.ONE));
    }

    static Stream<BigDecimal> invalidExchangeRates() {
        return Stream.of(null, BigDecimal.ZERO, new BigDecimal("-0.5"));
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
        assertEquals(7L, persistedRecord.getExchangeRateId());
        assertEquals("USD", persistedRecord.getFrom());
        assertEquals("EUR", persistedRecord.getTo());
        assertEquals(new BigDecimal("2"), persistedRecord.getQuantity());
        assertEquals(new BigDecimal("1.2345"), persistedRecord.getConversionMultiple());
        assertEquals(new BigDecimal("2.47"), persistedRecord.getTotalCalculatedAmount());
        assertEquals("8000", persistedRecord.getEnvironment());
    }

    @Test
    void convertAndRecordDoesNotCallExchangeServiceForSameCurrency() {
        when(conversionRecordRepository.save(any(ConversionRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ConversionRecord result = conversionService.convertAndRecord(
                "CAD", "cad", new BigDecimal("5"));

        assertEquals("CAD", result.getFrom());
        assertEquals("CAD", result.getTo());
        assertEquals(BigDecimal.ONE, result.getConversionMultiple());
        assertEquals(new BigDecimal("5.00"), result.getTotalCalculatedAmount());
        verifyNoInteractions(exchangeProxy);
        verify(conversionRecordRepository).save(any(ConversionRecord.class));
    }

    @Test
    void getRecentConversionsReturnsRequestedNumberFromNewestFirstResults() {
        ConversionRecord first = record("USD", "EUR", "1");
        ConversionRecord second = record("USD", "JPY", "2");
        when(conversionRecordRepository.findTop100ByOrderByCreatedAtDesc())
                .thenReturn(List.of(first, second));

        List<ConversionRecord> result = conversionService.getRecentConversions(1);

        assertEquals(List.of(first), result);
        verify(conversionRecordRepository).findTop100ByOrderByCreatedAtDesc();
        verifyNoInteractions(exchangeProxy);
    }

    @ParameterizedTest
    @MethodSource("invalidHistoryLimits")
    void getRecentConversionsRejectsLimitsOutsideSupportedRange(int limit) {
        assertThrows(IllegalArgumentException.class,
                () -> conversionService.getRecentConversions(limit));

        verifyNoInteractions(conversionRecordRepository, exchangeProxy);
    }

    static Stream<Integer> invalidHistoryLimits() {
        return Stream.of(-1, 0, 101);
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
    void getConversionThrowsNotFoundWhenRecordDoesNotExist() {
        when(conversionRecordRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> conversionService.getConversion(99L));

        verify(conversionRecordRepository).findById(99L);
    }

    @ParameterizedTest
    @MethodSource("invalidConversionIds")
    void getConversionRejectsNullOrNonPositiveIds(Long id) {
        assertThrows(IllegalArgumentException.class, () -> conversionService.getConversion(id));

        verifyNoInteractions(conversionRecordRepository, exchangeProxy);
    }

    static Stream<Long> invalidConversionIds() {
        return Stream.of(null, 0L, -1L);
    }

    private CurrencyConversion exchangeRate(Long id, String rate, String environment) {
        return new CurrencyConversion(
                id, "USD", "EUR", null, new BigDecimal(rate), null, environment);
    }

    private ConversionRecord record(String from, String to, String quantity) {
        ConversionRecord record = new ConversionRecord(
                1L,
                from,
                to,
                new BigDecimal(quantity),
                BigDecimal.ONE,
                new BigDecimal(quantity).setScale(2),
                "test");
        return record;
    }
}
