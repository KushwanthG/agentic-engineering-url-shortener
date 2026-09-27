package com.agentic.urlshortener.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** T008: canonical JSON and SHA-256 fingerprints underpin artifact provenance, approval binding, and reuse. */
@Tag("FR-ORC-10")
@Tag("FR-GOV-05")
@Tag("FR-RPL-01")
class CanonicalJsonTest {

    @Test
    void sortsKeysRecursivelyAndRemovesWhitespace() {
        String json = "{ \"b\": 1, \"a\": { \"d\": 2, \"c\": [3, { \"f\": 1, \"e\": 2 }] } }";
        assertThat(CanonicalJson.canonicalize(json))
                .isEqualTo("{\"a\":{\"c\":[3,{\"e\":2,\"f\":1}],\"d\":2},\"b\":1}");
    }

    @Test
    void equivalentDocumentsHaveTheSameFingerprint() {
        String one = "{\"x\":[1,2],\"y\":\"z\"}";
        String two = "{\n  \"y\" : \"z\",\n  \"x\" : [ 1, 2 ]\n}";
        assertThat(Fingerprints.ofJson(one)).isEqualTo(Fingerprints.ofJson(two));
    }

    @Test
    void differentDocumentsHaveDifferentFingerprints() {
        assertThat(Fingerprints.ofJson("{\"x\":[1,2]}")).isNotEqualTo(Fingerprints.ofJson("{\"x\":[2,1]}"));
    }

    @Test
    void sha256MatchesTheStandardTestVector() {
        assertThat(Fingerprints.sha256("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void writesObjectsCanonically() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("zeta", List.of("b", "a"));
        map.put("alpha", Map.of("k", true));
        assertThat(CanonicalJson.write(map)).isEqualTo("{\"alpha\":{\"k\":true},\"zeta\":[\"b\",\"a\"]}");
    }

    @Test
    void writesRecordsCanonically() {
        record Sample(String second, int first) {
        }
        assertThat(CanonicalJson.write(new Sample("x", 1))).isEqualTo("{\"first\":1,\"second\":\"x\"}");
    }
}
