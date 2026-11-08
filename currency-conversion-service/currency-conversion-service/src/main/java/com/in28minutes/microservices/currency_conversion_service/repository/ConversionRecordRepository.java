package com.in28minutes.microservices.currency_conversion_service.repository;

import com.in28minutes.microservices.currency_conversion_service.entity.ConversionRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConversionRecordRepository extends JpaRepository<ConversionRecord, Long> {

    List<ConversionRecord> findTop100ByOrderByCreatedAtDesc();
}
