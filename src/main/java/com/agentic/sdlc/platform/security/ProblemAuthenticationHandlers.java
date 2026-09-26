package com.agentic.sdlc.platform.security;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import com.agentic.sdlc.platform.json.CanonicalJson;
import com.agentic.sdlc.platform.web.CorrelationIdFilter;
import com.agentic.sdlc.platform.web.ErrorCode;

/**
 * Writes 401 and 403 as RFC 9457 problems (codes {@code UNAUTHENTICATED}, {@code FORBIDDEN}). The
 * 401 body never says which part of the credential was wrong.
 */
@Component
public class ProblemAuthenticationHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        write(response, ErrorCode.UNAUTHENTICATED, "Provide a valid bearer token.", request.getRequestURI());
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
            throws IOException {
        write(response, ErrorCode.FORBIDDEN, "Your role does not permit this operation.", request.getRequestURI());
    }

    private static void write(HttpServletResponse response, ErrorCode code, String detail, String instance) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", code.type());
        body.put("title", code.title());
        body.put("status", code.status().value());
        body.put("detail", detail);
        body.put("instance", instance);
        body.put("code", code.name());
        body.put("correlationId", CorrelationIdFilter.currentId());
        response.setStatus(code.status().value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write(CanonicalJson.write(body));
    }
}
