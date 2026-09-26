package com.agentic.urlshortener.shortener.service;

import java.time.Instant;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.agentic.urlshortener.common.exception.ApiException;
import com.agentic.urlshortener.common.exception.ErrorCode;
import com.agentic.urlshortener.common.util.CanonicalJson;
import com.agentic.urlshortener.common.util.Fingerprints;
import com.agentic.urlshortener.shortener.config.ShortenerProperties;
import com.agentic.urlshortener.shortener.domain.IdempotencyRecord;
import com.agentic.urlshortener.shortener.dto.CreateLinkCommand;
import com.agentic.urlshortener.shortener.dto.CreatedLink;
import com.agentic.urlshortener.shortener.dto.LinkView;
import com.agentic.urlshortener.shortener.repository.IdempotencyRecordRepository;

/**
 * Idempotency keys per API consumer (FR-LNK-08/09, research R-09). The request fingerprint covers the
 * client-supplied payload; an identical request within the window replays the stored response, a
 * different payload with the same key is a conflict, and an expired record is replaced.
 */
@Service
public class IdempotencyService {

    private static final Pattern KEY = Pattern.compile("^[A-Za-z0-9_-]{1,128}$");
    private static final int CREATED = 201;

    private final IdempotencyRecordRepository records;
    private final ShortenerProperties properties;

    public IdempotencyService(IdempotencyRecordRepository records, ShortenerProperties properties) {
        this.records = records;
        this.properties = properties;
    }

    public void validateKey(String key) {
        if (key == null || !KEY.matcher(key).matches()) {
            throw new ApiException(ErrorCode.INVALID_IDEMPOTENCY_KEY,
                    "Idempotency-Key must be 1 to 128 characters of letters, digits, '_' or '-'.");
        }
    }

    public static String fingerprint(CreateLinkCommand command) {
        TreeMap<String, Object> payload = new TreeMap<>();
        payload.put("url", command.url());
        payload.put("expiresAt", command.expiresAt() == null ? null : command.expiresAt().toString());
        payload.put("alias", command.alias());
        payload.put("maxClicks", command.maxClicks());
        return Fingerprints.ofValue(payload);
    }

    /**
     * Returns the replay for an existing, unexpired record with the same fingerprint; empty when there
     * is no usable record (an expired one is removed first).
     */
    public Optional<CreatedLink> replay(String consumerId, String key, String fingerprint, Instant now) {
        Optional<IdempotencyRecord> found = records.findByConsumerIdAndIdemKey(consumerId, key);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        IdempotencyRecord record = found.get();
        if (record.isExpiredAt(now)) {
            records.deleteById(record.getId());
            return Optional.empty();
        }
        if (!record.getRequestFingerprint().equals(fingerprint)) {
            throw new ApiException(ErrorCode.IDEMPOTENCY_KEY_REUSED,
                    "This Idempotency-Key was already used with a different request.");
        }
        return Optional.of(new CreatedLink(CanonicalJson.read(record.getResponseBody(), LinkView.class), true));
    }

    public IdempotencyRecord newRecord(String consumerId, String key, String fingerprint, LinkView view, Long linkId, Instant now) {
        return new IdempotencyRecord(consumerId, key, fingerprint, CREATED, CanonicalJson.write(view), linkId, now,
                now.plus(properties.idempotencyWindow()));
    }
}
