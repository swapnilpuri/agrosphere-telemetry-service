# Local pins added — 2026-11-01

Exact executable acceptance/local Contracts 0.10.0 release and three Telemetry pins are now recorded in Architecture MQTT_POC_CONTRACT_APPROVAL.md. Earlier no-pin/pending-release labels below are historical. Domain policy is unchanged; no adapters/migration/runtime behavior is delivered. Maven now checks packaged pin bytes, and the standalone stdlib verifier reads only this repository. Next concrete design: [adapter review](TLM01_ADAPTER_REVIEW.md).

# TLM-01 pure-domain preparation

Date: 2026-11-01. Owner accepted the bounded package; Architecture docs/milestones/MQTT_POC_APPROVAL.md records exact approval. This increment contains pure Observation/DeviceRegistration/ObservationPolicy and outbound port contracts only. No bean, listener, controller, JWT decoder change, migration, registry seed or outbox adapter is installed.

Policies cover trusted publisher/topic/mapping checks, SIMULATED units/provenance, exact decimal and interval validation, bounded UTC instants, manifest/future-skew admission, normalized duplicate/conflict classification, observed-time/UUID latest selection and coverage as presence. Numeric/wire precision remains subject to the independently reviewed executable candidate. Ports document required atomic registry/dedup/latest/outbox behavior and reference authorization; interface declarations do not prove those guarantees.

Run `mvnw.cmd --batch-mode --no-transfer-progress verify` with Java 25 as required by README. New tests are pure; the retained foundation security tests still use a mock decoder and test-only routes. No Testcontainers, live broker, authenticated MQTT, signed business JWT, database durability/restart/replay or HTTP contract enforcement has been exercised. Do not interpret this as TLM-01 delivery or M3 acceptance.

Contracts candidate is isolated in its own independent repository under docs/telemetry-poc. No artifact is pinned or loaded by Telemetry yet. Candidate maximum payload/strict JSON, schema format and API/event semantics belong in later adapters only after exact acceptance; BigDecimal domain construction is not raw wire parsing. Java Instant precision is explicitly bounded. No sibling runtime import or shared model.

Next: exact executable acceptance/local release/pins, then concrete broker ACL/provisioning/migration review before adapters. SIMULATED-only, existing config/data/credentials/roles/Git/AEO exclusions persist. Actual results and source hashes belong in Architecture's preparation evidence and checkpoint.
