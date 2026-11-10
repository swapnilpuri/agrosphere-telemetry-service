# Current TLM-01 runtime — 2026-11-04

SIMULATED-only MQTT ingestion, V2 registry/observation persistence, transactional outbox and scoped latest-reading API are implemented under the accepted adapter package. See [runtime guide](docs/TLM01_RUNTIME.md) for independent checks, generated-only activation/recovery and actual verification limits. Contracts 0.10.0 pins are unchanged. Earlier foundation/no-runtime/JPA statements below are historical: runtime persistence uses JDBC/Flyway. Final-source generated platform verification passed after Docker became available; owner functional acceptance remains open and no existing-stack rollout is inferred.

# TLM-01 local contract release/pins — 2026-11-01

Owner-accepted Contracts **0.10.0** API/MQTT/event artifacts are pinned under src/main/resources/contracts; scripts/validate_contract_pins.py and Maven ContractPinsTest verify exact local/packaged bytes without sibling source. Run python scripts/validate_contract_pins.py plus the existing Java 25 verify command. Earlier no-pin labels below are historical; runtime adapters and V2 remain unimplemented.

# TLM-01 preparation — 2026-11-01

Owner-approved TLM-01 pure domain/ports/tests are now prepared; see docs/TLM01_DOMAIN_PREPARATION.md. The foundation-only language below remains accurate for runtime adapters: no business endpoint/MQTT handler/migration or contract pin is delivered. Run the existing Java 25 verify command; exact Contracts candidate acceptance precedes runtime implementation.

# agrosphere-telemetry-service

Device registry, MQTT ingestion, weather/soil readings, normalization and environmental anomaly events.

This is an independent project and Git root. Version: 0.1.0; use independent SemVer tags and CHANGELOG.md. License decision: LICENSE.md (all rights reserved pending selection).

See AGENTS.md for scope and engineering rules. This scaffold provides no business APIs or production features. Architecture lives in the sibling agrosphere-architecture repository; versioned API/event definitions live in agrosphere-contracts. Do not add path dependencies to sibling projects.

## Build and test

Requires JDK 25. Run ./mvnw verify (Windows: .\mvnw.cmd verify). No infrastructure is needed for the security policy tests. Four tests verify public health, unauthenticated denial, authenticated access and invalid-token rejection using test-only routes and a test decoder. Real issuer/audience, database and Testcontainers integration tests arrive with the first use case.

Copy .env.example to .env and supply private values when runtime work begins. Spring does not load .env automatically: export variables or use Compose. Run ./mvnw spring-boot:run only after database and OIDC configuration. Only Actuator health is public.

Packages reserve clean domain, application, ports, web/messaging and persistence adapters. V1 creates outbox and idempotency infrastructure only; no handlers or relays are implemented. Flyway owns DDL; JPA validates schemas.

Spring Boot 4.0.9 / Java 25. JUnit Jupiter 5.14.4 overrides the Boot 4 JUnit 6 default to retain the requested JUnit 5 baseline. CI verifies this combination. Boot manages other dependencies except those explicitly pinned in pom.xml.

JUnit compatibility: Spring 7 SpringExtension calls JUnit 6-only APIs. Foundation security tests use JUnit 5 with an explicitly managed Spring web context instead. Do not introduce @SpringBootTest/SpringExtension until the JUnit baseline decision is revisited or a supported JUnit 5 integration approach is tested. These tests use a mock decoder and do not verify live OIDC claims.
