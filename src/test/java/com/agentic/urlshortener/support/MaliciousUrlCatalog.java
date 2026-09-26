package com.agentic.urlshortener.support;

import java.util.List;

import com.agentic.urlshortener.common.exception.ErrorCode;

/**
 * The security URL catalog (PVT-15: at least 25 malicious or invalid patterns), shared by the unit
 * test of the URL policy and the HTTP-level catalog test so both check exactly the same inputs.
 */
public final class MaliciousUrlCatalog {

    /** One catalog input and the error code it must be rejected with. */
    public record Entry(String url, ErrorCode code) {
    }

    private MaliciousUrlCatalog() {
    }

    public static List<Entry> entries() {
        return List.of(
                new Entry("javascript:alert(1)", ErrorCode.URL_SCHEME_NOT_ALLOWED),
                new Entry("data:text/html,<script>alert(1)</script>", ErrorCode.URL_SCHEME_NOT_ALLOWED),
                new Entry("file:///etc/passwd", ErrorCode.URL_SCHEME_NOT_ALLOWED),
                new Entry("ftp://files.example.com/a", ErrorCode.URL_SCHEME_NOT_ALLOWED),
                new Entry("mailto:someone@example.com", ErrorCode.URL_SCHEME_NOT_ALLOWED),
                new Entry("vbscript:msgbox(1)", ErrorCode.URL_SCHEME_NOT_ALLOWED),
                new Entry("https://user:secret@example.com/", ErrorCode.URL_CREDENTIALS_NOT_ALLOWED),
                new Entry("https://example.com@evil.example/", ErrorCode.URL_CREDENTIALS_NOT_ALLOWED),
                new Entry("https:///path-only", ErrorCode.URL_HOST_MISSING),
                new Entry("https://" + "a".repeat(2030) + ".example.com/", ErrorCode.URL_TOO_LONG),
                new Entry("http://localhost:8080/admin", ErrorCode.URL_HOST_NOT_ALLOWED),
                new Entry("http://api.localhost/", ErrorCode.URL_HOST_NOT_ALLOWED),
                new Entry("http://printer.local/", ErrorCode.URL_HOST_NOT_ALLOWED),
                new Entry("http://svc.internal/", ErrorCode.URL_HOST_NOT_ALLOWED),
                new Entry("http://intranet/", ErrorCode.URL_HOST_NOT_ALLOWED),
                new Entry("http://127.0.0.1/", ErrorCode.URL_HOST_NOT_ALLOWED),
                new Entry("http://127.1/", ErrorCode.URL_HOST_NOT_ALLOWED),
                new Entry("http://2130706433/", ErrorCode.URL_HOST_NOT_ALLOWED),
                new Entry("http://0x7f000001/", ErrorCode.URL_HOST_NOT_ALLOWED),
                new Entry("http://0177.0.0.1/", ErrorCode.URL_HOST_NOT_ALLOWED),
                new Entry("http://10.0.0.1/", ErrorCode.URL_HOST_NOT_ALLOWED),
                new Entry("http://172.16.0.1/", ErrorCode.URL_HOST_NOT_ALLOWED),
                new Entry("http://192.168.1.1/", ErrorCode.URL_HOST_NOT_ALLOWED),
                new Entry("http://169.254.169.254/latest/meta-data/", ErrorCode.URL_HOST_NOT_ALLOWED),
                new Entry("http://100.64.0.1/", ErrorCode.URL_HOST_NOT_ALLOWED),
                new Entry("http://0.0.0.0/", ErrorCode.URL_HOST_NOT_ALLOWED),
                new Entry("http://[::1]/", ErrorCode.URL_HOST_NOT_ALLOWED),
                new Entry("http://[::ffff:127.0.0.1]/", ErrorCode.URL_HOST_NOT_ALLOWED),
                new Entry("http://[fe80::1]/", ErrorCode.URL_HOST_NOT_ALLOWED),
                new Entry("http://[fc00::1]/", ErrorCode.URL_HOST_NOT_ALLOWED),
                new Entry("http://224.0.0.1/", ErrorCode.URL_HOST_NOT_ALLOWED),
                new Entry("https://sho.rt/abc1234", ErrorCode.URL_HOST_NOT_ALLOWED),
                new Entry("http://%6c%6f%63%61%6c%68%6f%73%74/", ErrorCode.URL_INVALID),
                new Entry("not a url", ErrorCode.URL_INVALID),
                new Entry("/relative/path", ErrorCode.URL_INVALID),
                new Entry("", ErrorCode.URL_INVALID));
    }
}
