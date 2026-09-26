package com.agentic.urlshortener.shortener.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;

import com.agentic.urlshortener.shortener.domain.ClickEvent;
import com.agentic.urlshortener.shortener.domain.ShortLink;

/** T020: atomic click counting, daily aggregation per UTC day, synthetic cleanup (FR-ANL-01/03/05). */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Tag("FR-ANL-01")
@Tag("FR-ANL-03")
@Tag("FR-ANL-05")
@Tag("FR-LNK-05")
class ShortLinkRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-09-26T10:15:30Z");

    @Autowired
    private ShortLinkRepository links;

    @Autowired
    private ClickEventRepository clicks;

    @Test
    void findsByCodeAndIncrementsAtomically() {
        ShortLink link = links.saveAndFlush(ShortLink.create("abc1234", "https://example.com/", "consumer", NOW, null));
        assertThat(links.findByCode("abc1234")).isPresent();

        assertThat(links.incrementClicks(link.getId(), NOW.plusSeconds(1))).isEqualTo(1);
        assertThat(links.incrementClicks(link.getId(), NOW.plusSeconds(2))).isEqualTo(1);
        links.flush();

        ShortLink reloaded = links.findByCode("abc1234").orElseThrow();
        assertThat(reloaded.getClickCount()).isEqualTo(2);
        assertThat(reloaded.getLastAccessedAt()).isEqualTo(NOW.plusSeconds(2));
    }

    @Test
    void aggregatesClicksPerUtcDay() {
        ShortLink link = links.saveAndFlush(ShortLink.create("day0001", "https://example.com/", "consumer", NOW, null));
        clicks.save(new ClickEvent(link.getId(), Instant.parse("2026-09-25T23:59:00Z"), null));
        clicks.save(new ClickEvent(link.getId(), Instant.parse("2026-09-26T00:01:00Z"), "news.example"));
        clicks.save(new ClickEvent(link.getId(), Instant.parse("2026-09-26T09:00:00Z"), null));
        clicks.save(new ClickEvent(link.getId(), Instant.parse("2026-07-01T09:00:00Z"), null));
        clicks.flush();

        List<ClickEventRepository.DailyCount> daily = clicks.dailyCounts(link.getId(), NOW.minus(30, ChronoUnit.DAYS));
        assertThat(daily).containsExactly(
                new ClickEventRepository.DailyCount(LocalDate.parse("2026-09-25"), 1),
                new ClickEventRepository.DailyCount(LocalDate.parse("2026-09-26"), 2));
    }

    @Test
    void deletesOnlyTheSyntheticLinksOfARun() {
        UUID run = UUID.randomUUID();
        links.saveAndFlush(ShortLink.synthetic("syn0001", "https://example.com/", NOW, run));
        links.saveAndFlush(ShortLink.synthetic("syn0002", "https://example.com/", NOW, UUID.randomUUID()));
        links.saveAndFlush(ShortLink.create("real001", "https://example.com/", "consumer", NOW, null));

        assertThat(links.deleteBySyntheticRunId(run)).isEqualTo(1);
        assertThat(links.findByCode("syn0001")).isEmpty();
        assertThat(links.findByCode("syn0002")).isPresent();
        assertThat(links.findByCode("real001")).isPresent();
    }
}
