# TLM-01 adapter engineering review — 2026-11-01

Scope authorized by Architecture MQTT_POC_ADAPTER_APPROVAL.md. Internal engineering review before any V2 fixture migration application; no external reviewer, owner functional acceptance or production security certification claimed. Original approved package bytes preserved.

## SQL and privileges before application

Exact additive V2 SQL SHA-256 `b42b936bfa4b74a59c7d9728cd0838cfc41d20fbad24a131c6a43a174c33a0d6`. V1 remains unchanged. Registry immutable under generated runtime grants; canonical composite FK and unique block/principal enforce fixture binding. Observation UUID global primary key arbitrates races. Decimal rounding is forbidden by strict application validation before numeric(18,6). DB checks independently constrain kind/unit/provenance/ranges/interval. Latest index/query explicitly use C-collated UUID text and observed time. Existing outbox gains only an additive next-attempt clock; no old row conversion or mutation of V1 bytes. Rollback testing uses only new disposable databases/schema transactions.

Generated owner performs Flyway/bootstrap; a distinct generated LOGIN receives CONNECT/schema USAGE, SELECT registry/observations/outbox, INSERT observations/outbox, column UPDATE attempts/published_at/next_attempt_at only. Revoke PUBLIC schema CREATE; no runtime owner membership, DDL, registry mutation or observation UPDATE/DELETE. Verify actual generated login and inherited/PUBLIC denial tests. No SQL principal/password command-line args or tracked files. No existing target provisioned.

## MQTT client experiment and sources

Isolated target/mqtt-probe used HiveMQ client 1.4.1 without first pinning a runtime dependency. One actual disposable Mosquitto 2.0.22 test passed: retained flag with retainAsPublished, delayed manual ACK, reconnect under persistent session and redelivery. Probe APIs were corrected after a compile failure; final PASS log: target/mqtt-probe/test.log. Source selection: [official release](https://github.com/hivemq/hivemq-mqtt-client/releases/tag/v1.4.1), [manual acknowledgement documentation](https://hivemq.github.io/hivemq-mqtt-client/docs/advanced-usage/). Dependency selected only after probe, pinned 1.4.1 in the owning POM; resolved Java 25 build compiles. This review is not an exhaustive CVE or transitive dependency attestation; no vulnerability-free claim.

## ACL/trust and budgets

Exclusive random generated run manifests bind active DB registry/window/run UUID, exact topic/publisher principal, protected subscriber/estate token files, ACL and broker config SHA-256. Paths stay inside real owned root; marker/run must match. Channels/ACL/config/registry/window are reverified before each message. Source identity derives from enforced immutable broker ACL, not observed username metadata or payload. Broker anonymous=false; exact write/read-only users, no broad wildcard publication. Subscriber cannot publish; publisher cannot read. Use username-as-client-ID to prevent a different principal taking over the subscriber's chosen client ID. Same-principal collisions stop visibly. Retained publications explicitly rejected; manual ACK occurs only after committed acceptance/duplicate or counted deterministic rejection.

Approved broker settings add 4096-byte message/8192-byte packet cap, 10 in-flight/100 queued; client worker bounded to 100. Three transient processing attempts (1/2 second delays); exhausted/ambiguous outcomes stop with UNKNOWN counter and leave unacknowledged broker delivery for operator-controlled restart/reobservation. No silent resume after changed trust. Outbox bounds: 20 locked rows/batch, max three delivery attempts, confirms/mandatory-return checks, 1/2 second starting delays; failed rows remain visible, replay only scoped fixture operator. SQL lock/statement limits 2/10s; Estate connect/read 2/5s with 64KiB bounded response and no redirects. This is generated test capacity, not a measured SLA.

Default demo/MQTT/outbox disabled; explicit runtime/migrator credentials, signed issuer/audience/role/estate checks and active Estate HTTP references. No new role, contract field or endpoint beyond accepted GET. No private config, existing data, real devices, stack/deployment or AEO work.


## Completion notes — 2026-11-04

The resolved HiveMQ 1.4.1 Maven POM identifies Apache-2.0; this does not settle the application's pending ownership/redistribution decision or certify transitive vulnerability absence. Runtime/API checks now include exact Problem bodies for invalid bearer tokens. Health/aggregate metrics expose halted MQTT or exhausted outbox work without identities. No domain/event/API bytes changed. Broker fixture initialization hashes its generated password file once; actual restart verifies readiness before retry. Mosquitto may grant a wildcard SUBACK while denying every unauthorized delivery; the test verifies actual delivery isolation. The final 30 unit/14 integration run passed on November 1. November 4's final-source platform rerun is unavailable because Docker is stopped; no existing stack is started to satisfy it.


### Docker availability resolved later on 2026-11-04

The owner made Docker available; the final-source full-platform drill passed in agrosphere-3a-check-71279daf and verified owned cleanup. Earlier unavailable notes describe the initial attempt only. Runtime/pin/SQL bytes and the accepted adapter package are unchanged; final source/report/image identities are recorded in Architecture machine evidence.
