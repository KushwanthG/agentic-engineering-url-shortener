package com.agentic.urlshortener.shortener.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One successful redirect. Stores no personal identifiers: time and referrer host only (FR-ANL-02). */
@Entity
@Table(name = "click_event")
public class ClickEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "link_id", nullable = false)
    private Long linkId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "referrer_host")
    private String referrerHost;

    protected ClickEvent() {
    }

    public ClickEvent(Long linkId, Instant occurredAt, String referrerHost) {
        this.linkId = linkId;
        this.occurredAt = occurredAt;
        this.referrerHost = referrerHost;
    }

    public Long getId() {
        return id;
    }

    public Long getLinkId() {
        return linkId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getReferrerHost() {
        return referrerHost;
    }
}
