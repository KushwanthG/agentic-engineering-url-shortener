package com.agentic.urlshortener.support;

/** Labeled non-production demo tokens (their SHA-256 hashes are configured in application-test.yml). */
public final class Tokens {

    public static final String CONSUMER = "demo-consumer-token";
    public static final String REQUESTER = "demo-requester-token";
    public static final String APPROVER = "demo-approver-token";
    public static final String RELEASE_OWNER = "demo-release-token";
    public static final String AUDITOR = "demo-auditor-token";
    /** Test-only principal "dave" holding REQUESTER, APPROVER and RELEASE_OWNER (separation-of-duties tests). */
    public static final String DUAL_ROLE = "test-dual-role-token";

    private Tokens() {
    }

    public static String bearer(String token) {
        return "Bearer " + token;
    }
}
