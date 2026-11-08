package com.in28minutes.microservices.currency_conversion_service.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

public class ConversionRequest {

    @NotBlank
    @Pattern(regexp = "[A-Za-z]{3}", message = "Currency codes must contain three letters")
    private String from;

    @NotBlank
    @Pattern(regexp = "[A-Za-z]{3}", message = "Currency codes must contain three letters")
    private String to;

    @NotNull
    @DecimalMin(value = "0", inclusive = false, message = "Quantity must be greater than zero")
    @Digits(integer = 9, fraction = 6, message = "Quantity supports up to 9 integer and 6 decimal digits")
    private BigDecimal quantity;

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }
}
