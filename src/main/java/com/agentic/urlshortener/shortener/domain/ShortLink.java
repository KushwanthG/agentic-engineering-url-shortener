package com.agentic.urlshortener.shortener.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A short link. Links are immutable after creation except for analytics counters, which are only
 * changed through the atomic update in {@code ShortLinkRepository#incrementClicks}.
 */
@Entity
@Table(name = "short_link")
public class ShortLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String code;

    @Column(name = "target_url", nullable = false, length = 2048)
    private String targetUrl;

    @Column(name = "created_by", nullable = false, length = 64)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "click_count", nullable = false)
    private long clickCount;

    @Column(name = "last_accessed_at")
    private Instant lastAccessedAt;

    @Column(nullable = false)
    private boolean synthetic;

    @Column(name = "synthetic_run_id")
    private UUID syntheticRunId;

    protected ShortLink() {
    }

    private ShortLink(String code, String targetUrl, String createdBy, Instant createdAt, Instant expiresAt,
            boolean synthetic, UUID syntheticRunId) {
        this.code = code;
        this.targetUrl = targetUrl;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.synthetic = synthetic;
        this.syntheticRunId = syntheticRunId;
    }

    public static ShortLink create(String code, String targetUrl, String createdBy, Instant createdAt, Instant expiresAt) {
        return new ShortLink(code, targetUrl, createdBy, createdAt, expiresAt, false, null);
    }

    /** A link created by an orchestration run for verification only; removed by run cleanup. */
    public static ShortLink synthetic(String code, String targetUrl, Instant createdAt, UUID runId) {
        return new ShortLink(code, targetUrl, "run:" + runId.toString().substring(0, 8), createdAt, null, true, runId);
    }

    /** Expired at and after the expiry instant ({@code expires_at == now} is expired, FR-RED-04). */
    public boolean isExpiredAt(Instant now) {
        return expiresAt != null && !now.isBefore(expiresAt);
    }

    public LinkStatus statusAt(Instant now) {
        return isExpiredAt(now) ? LinkStatus.EXPIRED : LinkStatus.ACTIVE;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getTargetUrl() {
        return targetUrl;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public long getClickCount() {
        return clickCount;
    }

    public Instant getLastAccessedAt() {
        return lastAccessedAt;
    }

    public boolean isSynthetic() {
        return synthetic;
    }

    public UUID getSyntheticRunId() {
        return syntheticRunId;
    }
}
