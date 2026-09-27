package com.agentic.urlshortener.shortener.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.agentic.urlshortener.common.exception.ApiException;
import com.agentic.urlshortener.common.exception.ErrorCode;
import com.agentic.urlshortener.shortener.config.ShortenerProperties;
import com.agentic.urlshortener.shortener.domain.NormalizedUrl;
import com.agentic.urlshortener.shortener.domain.ShortCodeGenerator;
import com.agentic.urlshortener.shortener.domain.ShortLink;
import com.agentic.urlshortener.shortener.domain.UrlPolicy;
import com.agentic.urlshortener.shortener.dto.CreateLinkCommand;
import com.agentic.urlshortener.shortener.dto.CreatedLink;
import com.agentic.urlshortener.shortener.dto.LinkView;

/**
 * Creates short links (FR-LNK-01, 05, 07, 08, 09, 12). Validation happens first; then each attempt
 * inserts in its own transaction ({@link LinkWriter}), so the retry loop on a code collision runs
 * outside any transaction and a lost race on an idempotency key turns into a replay.
 */
@Service
public class LinkCreationService {

    private static final Logger log = LoggerFactory.getLogger(LinkCreationService.class);
    private static final String CODE_CONSTRAINT = "uk_short_link_code";

    private final UrlPolicy urlPolicy;
    private final ShortCodeGenerator generator;
    private final LinkWriter writer;
    private final IdempotencyService idempotency;
    private final ClickLimitCapability clickLimit;
    private final DefaultExpiryCapability defaultExpiry;
    private final CustomAliasCapability customAlias;
    private final ShortenerProperties properties;
    private final Clock clock;

    public LinkCreationService(UrlPolicy urlPolicy, ShortCodeGenerator generator, LinkWriter writer,
            IdempotencyService idempotency, ClickLimitCapability clickLimit, DefaultExpiryCapability defaultExpiry,
            CustomAliasCapability customAlias,
            ShortenerProperties properties, Clock clock) {
        this.urlPolicy = urlPolicy;
        this.generator = generator;
        this.writer = writer;
        this.idempotency = idempotency;
        this.clickLimit = clickLimit;
        this.defaultExpiry = defaultExpiry;
        this.customAlias = customAlias;
        this.properties = properties;
        this.clock = clock;
    }

    public CreatedLink create(CreateLinkCommand command) {
        String key = command.idempotencyKey();
        if (key != null) {
            idempotency.validateKey(key);
        }
        String alias = command.alias() == null ? null : customAlias.require(command.alias());
        Long maxClicks = command.maxClicks() == null ? null : clickLimit.require(command.maxClicks());
        NormalizedUrl target = urlPolicy.validate(command.url());
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        Instant expiresAt = command.expiresAt() != null ? validateExpiry(command.expiresAt(), now)
                : defaultExpiry.defaultExpiry(now).orElse(null);

        String fingerprint = key == null ? null : IdempotencyService.fingerprint(command);
        if (key != null) {
            Optional<CreatedLink> replay = idempotency.replay(command.consumerId(), key, fingerprint, now);
            if (replay.isPresent()) {
                return replay.get();
            }
        }

        // An alias is the code chosen by the consumer: one attempt; a taken alias is a conflict, not a retry.
        int maxAttempts = alias == null ? properties.codeGenerationAttempts() : 1;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            ShortLink link = newLink(alias == null ? generator.next() : alias, target, now, expiresAt, command).withMaxClicks(maxClicks);
            LinkView view = LinkView.of(link, properties.baseUrl(), now);
            try {
                writer.insert(link, key == null ? null
                        : saved -> idempotency.newRecord(command.consumerId(), key, fingerprint, view, saved.getId(), now));
                return new CreatedLink(view, false);
            } catch (DataIntegrityViolationException | ConcurrencyFailureException e) {
                if (key != null) {
                    Optional<CreatedLink> replay = idempotency.replay(command.consumerId(), key, fingerprint, now);
                    if (replay.isPresent()) {
                        return replay.get();
                    }
                }
                if (!isCodeCollision(e)) {
                    throw e;
                }
                if (alias != null) {
                    throw new ApiException(ErrorCode.ALIAS_CONFLICT, "The alias '" + alias + "' is already in use.");
                }
                log.info("Short-code collision on attempt {} of {}; generating a new code", attempt,
                        properties.codeGenerationAttempts());
            }
        }
        throw new ApiException(ErrorCode.CODE_GENERATION_EXHAUSTED,
                "No unique short code could be generated; retry the request.", List.of(), 1L);
    }

    private static ShortLink newLink(String code, NormalizedUrl target, Instant now, Instant expiresAt, CreateLinkCommand command) {
        ShortLink link = command.syntheticRunId() == null
                ? ShortLink.create(code, target.value(), command.consumerId(), now, expiresAt)
                : ShortLink.synthetic(code, target.value(), now, expiresAt, command.syntheticRunId());
        return command.alias() == null ? link : link.asCustomAlias();
    }

    private Instant validateExpiry(Instant expiresAt, Instant now) {
        if (expiresAt == null) {
            return null;
        }
        Instant truncated = expiresAt.truncatedTo(ChronoUnit.MICROS);
        if (!truncated.isAfter(now)) {
            throw new ApiException(ErrorCode.INVALID_EXPIRY, "expiresAt must be in the future.");
        }
        if (truncated.isAfter(now.plus(properties.maxExpiry()))) {
            throw new ApiException(ErrorCode.INVALID_EXPIRY,
                    "expiresAt must be at most " + properties.maxExpiry().toDays() + " days ahead.");
        }
        return truncated;
    }

    private static boolean isCodeCollision(RuntimeException e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t.getMessage() != null && t.getMessage().toLowerCase(Locale.ROOT).contains(CODE_CONSTRAINT)) {
                return true;
            }
        }
        return false;
    }
}
