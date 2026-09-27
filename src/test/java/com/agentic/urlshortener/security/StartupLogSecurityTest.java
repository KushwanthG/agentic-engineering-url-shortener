package com.agentic.urlshortener.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;

import com.agentic.urlshortener.UrlShortenerApplication;

/**
 * T133 and T135 (constitution V, NFR-SEC-01, NFR-SEC-03). Starts the real application and checks
 * what it logs at startup:
 * <ul>
 * <li>no credential is ever logged; in particular, Spring Boot's generated default-user password is
 *   not, in any profile;</li>
 * <li>the demo profile logs a clear non-production warning (ADR-015); the default profile does not.</li>
 * </ul>
 */
@ExtendWith(OutputCaptureExtension.class)
@Tag("NFR-SEC-01")
@Tag("NFR-SEC-03")
class StartupLogSecurityTest {

    private static final String DEMO_WARNING = "DEMO PROFILE ACTIVE";

    private static ConfigurableApplicationContext start(String... profiles) {
        // Command-line arguments outrank profile datasources; each start gets its own in-memory database.
        return new SpringApplicationBuilder(UrlShortenerApplication.class).profiles(profiles)
                .run("--spring.datasource.url=jdbc:h2:mem:startup-" + UUID.randomUUID()
                        + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1", "--server.port=0");
    }

    @Test
    void theDefaultProfileLogsNoCredentialAndNoDemoWarning(CapturedOutput output) {
        try (ConfigurableApplicationContext context = start()) {
            assertThat(context.isActive()).isTrue();
        }
        assertThat(output.getAll()).doesNotContainIgnoringCase("generated security password").doesNotContain(DEMO_WARNING);
    }

    @Test
    void theDemoProfileWarnsThatItIsNonProductionAndLogsNoCredential(CapturedOutput output) {
        try (ConfigurableApplicationContext context = start("demo")) {
            assertThat(context.isActive()).isTrue();
        }
        assertThat(output.getAll()).doesNotContainIgnoringCase("generated security password").contains(DEMO_WARNING)
                .doesNotContain("demo-requester-token");
    }
}
