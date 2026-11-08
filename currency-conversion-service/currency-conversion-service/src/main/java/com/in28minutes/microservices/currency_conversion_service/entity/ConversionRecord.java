package com.in28minutes.microservices.currency_conversion_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "currency_conversion")
public class ConversionRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long exchangeRateId;

    @Column(name = "currency_from", nullable = false, length = 3)
    private String from;

    @Column(name = "currency_to", nullable = false, length = 3)
    private String to;

    @Column(nullable = false, precision = 15, scale = 6)
    private BigDecimal quantity;

    @Column(nullable = false, precision = 15, scale = 8)
    private BigDecimal conversionMultiple;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal totalCalculatedAmount;

    private String environment;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected ConversionRecord() {
    }

    public ConversionRecord(
            Long exchangeRateId,
            String from,
            String to,
            BigDecimal quantity,
            BigDecimal conversionMultiple,
            BigDecimal totalCalculatedAmount,
            String environment) {
        this.exchangeRateId = exchangeRateId;
        this.from = from;
        this.to = to;
        this.quantity = quantity;
        this.conversionMultiple = conversionMultiple;
        this.totalCalculatedAmount = totalCalculatedAmount;
        this.environment = environment;
    }

    @PrePersist
    void setCreatedAt() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Long getExchangeRateId() {
        return exchangeRateId;
    }

    public String getFrom() {
        return from;
    }

    public String getTo() {
        return to;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getConversionMultiple() {
        return conversionMultiple;
    }

    public BigDecimal getTotalCalculatedAmount() {
        return totalCalculatedAmount;
    }

    public String getEnvironment() {
        return environment;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
