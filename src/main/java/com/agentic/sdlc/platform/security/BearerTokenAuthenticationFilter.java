package com.agentic.sdlc.platform.security;

import java.io.IOException;
import java.util.Optional;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Stateless bearer-token authentication. A missing header leaves the request anonymous (the URL
 * rules decide); a present but unknown token is rejected immediately with 401. Tokens are never
 * logged (NFR-SEC-03).
 */
public class BearerTokenAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(BearerTokenAuthenticationFilter.class);
    private static final String PREFIX = "Bearer ";

    private final PrincipalRegistry registry;
    private final AuthenticationEntryPoint entryPoint;

    public BearerTokenAuthenticationFilter(PrincipalRegistry registry, AuthenticationEntryPoint entryPoint) {
        this.registry = registry;
        this.entryPoint = entryPoint;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.regionMatches(true, 0, PREFIX, 0, PREFIX.length())) {
            chain.doFilter(request, response);
            return;
        }
        Optional<ApiPrincipal> principal = registry.authenticate(header.substring(PREFIX.length()).trim());
        if (principal.isEmpty()) {
            log.warn("Rejected bearer token for {} {} (credentials are never logged)", request.getMethod(), request.getRequestURI());
            SecurityContextHolder.clearContext();
            entryPoint.commence(request, response, new BadCredentialsException("Unknown bearer token"));
            return;
        }
        ApiPrincipal authenticated = principal.get();
        UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken.authenticated(
                authenticated, null,
                authenticated.roles().stream().map(role -> new SimpleGrantedAuthority(role.authority())).toList());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        try {
            chain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
