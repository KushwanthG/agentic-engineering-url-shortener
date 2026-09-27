# ADR-002: Java 21 and Spring Boot 4.1.1

## Status

Proposed (2026-09-26) — pending acceptance by the human candidate at Gate G4.
**Java 21** and **Spring Boot** are candidate directives recorded verbatim in
`docs/governance/human-gate-register.md`; the Spring Boot **version** is an assistant proposal.

## Context

The candidate directed a Spring Boot project on Java 21. The remaining choice is the Spring Boot
release line. Evidence gathered on 2026-09-26 (research.md): Spring Boot 3.5's open-source
support ended on 2026-06-30; 4.0 is supported until 2026-12-31; 4.1 until 2027-07-31. Latest
patches: 3.5.16, 4.0.8, 4.1.1. Boot 4 changed several things: modular starters
(`spring-boot-starter-webmvc`), per-technology test starters, Jackson 3 (`tools.jackson`), JUnit 6,
and Hibernate 7. Relevant requirements: NFR-SEC-05 (dependency risk), CON-02.

## Decision Drivers

- Supported, patched framework line through the assessment and beyond (dependency risk policy)
- Compatibility with Java 21 and the installed Maven
- Stability and implementation risk within the timebox

## Options Considered

| Option | Advantages | Disadvantages / risks | Assessment implications |
|--------|------------|-----------------------|-------------------------|
| Spring Boot 3.5.16 | most familiar APIs; largest body of examples | OSS end-of-life since 2026-06-30: a known dependency-risk finding at release readiness | reviewers may flag an EOL framework |
| Spring Boot 4.0.8 | current generation | support ends 2026-12-31 (three months after submission) | acceptable but short-lived |
| **Spring Boot 4.1.1** | longest OSS support (to 2027-07-31); current Spring Framework 7 / Security 7 | newest APIs; package moves in test auto-configuration; Jackson 3 | demonstrates currency and dependency-risk awareness |

## Decision

Spring Boot **4.1.1** on **Java 21** (`maven.compiler.release=21`), built with the Maven Wrapper
(Maven 3.9.x). `maven-enforcer-plugin` requires Java ≥ 21 and Maven ≥ 3.6.3, so a build started
with the machine's default JDK 11 fails immediately with a clear message.

## Rationale

It meets the candidate's directives while minimizing dependency risk; the API-change risk is
contained by the walking-skeleton milestone (M1), which compiles and tests every framework seam
(web, security, JPA, Flyway, Actuator, test slices) before feature work.

## Consequences

- **Positive**: supported framework; modern language features (records, sealed types, pattern
  matching) for state and result modelling.
- **Negative**: fewer public examples for Boot 4 test packages; Jackson 3 packages differ from
  Jackson 2 (test-only libraries may still pull Jackson 2, which is harmless).
- **Operational**: JDK 21 required at runtime.
- **Testing**: JUnit Jupiter 6 and Boot 4 test starters.
- **Governance**: the version choice is re-evaluated at each release by dependency policy.

## Risks and Mitigations

| Risk | Mitigation |
|------|------------|
| Boot 4.1 incompatibility with a needed library | libraries chosen are Boot-managed or framework-agnostic; stop condition in plan §14 escalates an ADR revision (to 4.0.x) |
| Reviewer machine has only JDK 17 | enforcer message plus quickstart prerequisites |

## Reversibility

Medium. Moving to 4.0.x is a version change; moving back to 3.5 would require reverting Jackson
3 and test-package changes (not recommended: end-of-life).

## Traceability

- Requirements: NFR-SEC-05, CON-02, NFR-TST-02; candidate directives (gate register)
- Plan sections: Technical Context; §12; research R-01..R-03
- Tasks: engineering baseline and walking skeleton tasks

## Validation

`mvnw -v` and `mvnw verify` on JDK 21; the enforcer rule is verified by a documented manual check
with JDK 11 (expected failure).
