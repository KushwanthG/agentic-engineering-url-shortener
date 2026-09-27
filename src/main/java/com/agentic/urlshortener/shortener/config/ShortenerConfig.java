package com.agentic.urlshortener.shortener.config;

import java.net.URI;
import java.time.Clock;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.agentic.urlshortener.shortener.domain.AliasPolicy;
import com.agentic.urlshortener.shortener.domain.SecureRandomShortCodeGenerator;
import com.agentic.urlshortener.shortener.domain.ShortCodeGenerator;
import com.agentic.urlshortener.shortener.domain.UrlPolicy;
import com.agentic.urlshortener.shortener.service.TokenBucketRateLimiter;

@Configuration(proxyBeanMethods = false)
public class ShortenerConfig {

    /** The URL policy blocks the configured self hosts and the host of the public base URL. */
    @Bean
    UrlPolicy urlPolicy(ShortenerProperties properties) {
        Set<String> selfHosts = new HashSet<>(properties.selfHosts());
        String baseHost = URI.create(properties.baseUrl()).getHost();
        if (baseHost != null) {
            selfHosts.add(baseHost.toLowerCase(Locale.ROOT));
        }
        return new UrlPolicy(properties.maxUrlLength(), selfHosts);
    }

    @Bean
    AliasPolicy aliasPolicy(ShortenerProperties properties) {
        return new AliasPolicy(properties.reservedAliases());
    }

    @Bean
    ShortCodeGenerator shortCodeGenerator(ShortenerProperties properties) {
        return new SecureRandomShortCodeGenerator(properties.codeLength());
    }

    @Bean
    RateLimiters rateLimiters(ShortenerProperties properties, Clock clock) {
        ShortenerProperties.RateLimit limits = properties.rateLimit();
        return new RateLimiters(
                new TokenBucketRateLimiter(limits.creationPerMinute(), limits.maxTrackedClients(), clock),
                new TokenBucketRateLimiter(limits.notFoundPerMinute(), limits.maxTrackedClients(), clock));
    }
}
