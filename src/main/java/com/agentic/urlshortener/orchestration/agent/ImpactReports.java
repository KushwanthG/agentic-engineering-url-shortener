package com.agentic.urlshortener.orchestration.agent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.agentic.urlshortener.orchestration.knowledge.CapabilityEntry;

/** The parts of an impact analysis that come from the capability catalog, shared by the scan and its fallback. */
final class ImpactReports {

    private ImpactReports() {
    }

    static Map<String, Object> skeleton(String method, boolean degraded, List<CapabilityEntry> capabilities) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("method", method);
        report.put("degraded", degraded);
        report.put("seeds", capabilities.stream().flatMap(c -> c.impactSeeds().stream()).distinct().toList());
        return report;
    }

    /** API operations and schema changes the capability touches. */
    static List<String> interfaces(List<CapabilityEntry> capabilities) {
        Set<String> interfaces = new LinkedHashSet<>();
        for (CapabilityEntry capability : capabilities) {
            if (capability.api() != null) {
                capability.api().changes().forEach(c -> interfaces.add(c.operation() + ": " + c.change() + " [" + c.compatibility() + "]"));
            }
            if (capability.schema() != null) {
                capability.schema().changes().forEach(c -> interfaces.add("schema " + c.migration() + ": " + c.change()
                        + " [" + c.compatibility() + "]"));
            }
        }
        return List.copyOf(interfaces);
    }

    /** Terms that identify documentation of the changed behavior: endpoint paths and the catalog's doc anchors. */
    static List<String> documentationTerms(List<CapabilityEntry> capabilities) {
        Set<String> terms = new LinkedHashSet<>();
        for (CapabilityEntry capability : capabilities) {
            if (capability.api() != null) {
                capability.api().changes().forEach(c -> terms.add(c.operation().substring(c.operation().indexOf(' ') + 1)));
            }
            capability.docAnchors().forEach(a -> terms.add(a.mentions()));
        }
        return List.copyOf(terms);
    }

    static List<Map<String, Object>> regressionRisks(List<CapabilityEntry> capabilities) {
        List<Map<String, Object>> risks = new ArrayList<>();
        for (CapabilityEntry capability : capabilities) {
            for (CapabilityEntry.RegressionRisk risk : capability.regressionRisks()) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("id", risk.id());
                entry.put("description", risk.description());
                entry.put("severity", risk.severity());
                entry.put("mitigation", risk.mitigation());
                risks.add(entry);
            }
        }
        return risks;
    }

    static void finish(Map<String, Object> report, List<CapabilityEntry> capabilities) {
        report.put("regressionRisks", regressionRisks(capabilities));
        report.put("rollout", String.join("; ", capabilities.stream().map(c -> c.id() + ": " + c.release().strategy()).toList()));
        report.put("rollback", String.join("; ", capabilities.stream().map(CapabilityEntry::rollback).toList()));
        report.put("impactedRequirements", capabilities.stream().flatMap(c -> c.impactedRequirements().stream()).distinct().toList());
    }

    static Map<String, Object> entry(String first, Object firstValue, String second, Object secondValue) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put(first, firstValue);
        entry.put(second, secondValue);
        return entry;
    }
}
