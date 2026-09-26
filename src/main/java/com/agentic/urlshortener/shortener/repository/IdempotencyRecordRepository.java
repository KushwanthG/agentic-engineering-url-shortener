package com.agentic.urlshortener.shortener.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.agentic.urlshortener.shortener.domain.IdempotencyRecord;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, Long> {

    Optional<IdempotencyRecord> findByConsumerIdAndIdemKey(String consumerId, String idemKey);
}
