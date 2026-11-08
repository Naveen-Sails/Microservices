package com.in28minutes.microservices.currency_conversion_service.exception;

public class CurrencyRateUnavailableException extends RuntimeException {

    public CurrencyRateUnavailableException(String message) {
        super(message);
    }

    public CurrencyRateUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
