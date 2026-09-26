# ADR-014: Executable Jar with Maven Wrapper and Profile-Based Configuration

## Status

Proposed (2026-09-26) — pending acceptance at Gate G4.

## Context

The prototype must run end-to-end on a reviewer's machine with documented commands and no paid
services or real secrets (constitution Delivery Constraints; SC-001). The candidate's machine
defaults to JDK 11, with JDK 21 installed separately.

## Decision Drivers

- Minimal prerequisites (a JDK only)
- Secure defaults vs. convenient demonstrations
- Reproducible build tool version

## Options Considered

| Option | Advantages | Disadvantages / risks |
|--------|------------|-----------------------|
| **Executable jar / `spring-boot:run`; Maven Wrapper; profiles `default`, `demo`, `json-logs`** | only a JDK needed | no container isolation |
| Docker image + compose | uniform runtime | Docker required; slower iteration (backlog BL-03) |

## Decision

- Maven Wrapper pinned to Maven 3.9.x (`.mvn/wrapper/maven-wrapper.properties`).
- `default` profile: secure. No principals are configured (authenticated APIs return 401), fault
  injection is off, only `health` and `info` are exposed publicly, and the H2 file database is
  under `./data/`.
- `demo` profile: the five labeled demo principals (token hashes), fault injection enabled, gate
  deadline overrides allowed.
- `json-logs` profile: ECS structured console logs.
- Configuration via `application*.yml` and environment variables; no secrets in the repository.

## Rationale

The chosen setup gives the fewest prerequisites for reviewers while keeping production-like
secure defaults.

## Consequences

- **Positive**: `mvnw verify` and `mvnw spring-boot:run -Dspring-boot.run.profiles=demo` are the
  only commands a reviewer needs.
- **Negative**: no container image to deploy (documented).
- **Operational**: `./data/` holds state; deleting it resets the system.

## Risks and Mitigations

| Risk | Mitigation |
|------|------------|
| Reviewer runs without the demo profile and sees 401s | quickstart and startup log line explain the profiles |

## Reversibility

High (a Dockerfile can be added without code changes).

## Traceability

- Requirements: CON-02, SC-001, NFR-SEC (secure defaults), FR-REL-11
- Plan: §8 secure defaults; quickstart.md; research R-20
- Tasks: baseline and quickstart tasks

## Validation

Clean-clone verification during convergence (guide step 61): build, test, run, and one scenario
from an empty checkout.
