package com.agentic.sdlc.platform.security;

import static com.agentic.sdlc.platform.security.Role.API_CONSUMER;
import static com.agentic.sdlc.platform.security.Role.APPROVER;
import static com.agentic.sdlc.platform.security.Role.AUDITOR;
import static com.agentic.sdlc.platform.security.Role.RELEASE_OWNER;
import static com.agentic.sdlc.platform.security.Role.REQUESTER;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

/**
 * URL authorization of ADR-015. Stateless (no sessions, no CSRF surface because there are no
 * cookies), deny-by-default. Gate-specific roles and separation of duties are enforced again in the
 * governance services.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, PrincipalRegistry registry,
                                            ProblemAuthenticationHandlers problems) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable())
                .requestCache(cache -> cache.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(problems).accessDeniedHandler(problems))
                .addFilterBefore(new BearerTokenAuthenticationFilter(registry, problems), AnonymousAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/actuator/**").hasRole(AUDITOR.name())
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/api/v1/links", "/api/v1/links/**").hasRole(API_CONSUMER.name())
                        .requestMatchers(HttpMethod.POST, "/api/v1/workflows").hasRole(REQUESTER.name())
                        .requestMatchers(HttpMethod.POST, "/api/v1/workflows/*/change-requests").hasRole(REQUESTER.name())
                        .requestMatchers(HttpMethod.POST, "/api/v1/workflows/*/change-requests/*/decision").hasRole(APPROVER.name())
                        .requestMatchers(HttpMethod.POST, "/api/v1/workflows/*/policy-exceptions").hasRole(REQUESTER.name())
                        .requestMatchers(HttpMethod.POST, "/api/v1/workflows/*/policy-exceptions/*/decision").hasRole(APPROVER.name())
                        .requestMatchers(HttpMethod.POST, "/api/v1/workflows/*/clarifications").hasRole(APPROVER.name())
                        .requestMatchers(HttpMethod.POST, "/api/v1/workflows/*/gates/*/decision")
                                .hasAnyRole(APPROVER.name(), RELEASE_OWNER.name())
                        .requestMatchers(HttpMethod.POST, "/api/v1/workflows/*/pause", "/api/v1/workflows/*/resume",
                                "/api/v1/workflows/*/safe-stop").hasRole(RELEASE_OWNER.name())
                        .requestMatchers(HttpMethod.GET, "/api/v1/workflows", "/api/v1/workflows/**", "/api/v1/reliability/**",
                                "/api/v1/policies", "/api/v1/capabilities")
                                .hasAnyRole(REQUESTER.name(), APPROVER.name(), RELEASE_OWNER.name(), AUDITOR.name())
                        .requestMatchers(HttpMethod.GET, "/{code:[A-Za-z0-9_-]+}").permitAll()
                        .anyRequest().denyAll());
        return http.build();
    }
}
