# TLM-01 generated local runtime

Updated 2026-11-04 (America/Chicago). Implementation is present under the accepted adapter package; exact review bytes and Contracts 0.10.0 pins remain unchanged. This guide supersedes historical foundation/no-adapter labels in README and earlier preparation notes. [Architecture verification](../../agrosphere-architecture/docs/milestones/MQTT_POC_ADAPTER_VERIFICATION.md) distinguishes implementation, historical executed checks, initially unavailable checks and their subsequent resolution and owner acceptance.

## Delivered behavior and boundaries

A generated registry binds device/publisher/topic to canonical estate/block/run/window IDs. Strict 4096-byte UTF-8/JSON admission rejects duplicate/unknown fields, wrong schema/provenance/UUID/unit/decimal/time/interval forms. Broker ACL enforcement supplies channel trust; MQTT does not expose publisher username to subscribers. Immutable manifest/ACL/config/registry binding is reverified; changed trust stops ingestion. Retained observations are rejected. Original observedAt and server receivedAt remain distinct.

Service-owned V2 adds registry/observation tables, ordered latest lookup and the outbox next-attempt clock; V1 is unchanged. Runtime uses JDBC, Flyway and a separate generated migration login. First observation, global UUID reservation and event outbox commit atomically. Canonically identical retries preserve original receipt/event; UUID conflicts never overwrite. Latest values use observedAt and C-collated UUID text order. Existing Estate metadata is fetched through bounded authenticated HTTP, never SQL.

Only accepted GET `/api/v1/telemetry/estates/{estateId}/blocks/{blockId}/latest` is added, independently validating signed issuer/audience/subject/roles/estate scope before reference requests or SQL. Known active block without data returns NONE/empty readings. Coverage means measurement-kind presence, not freshness, calibration or accuracy. Replies retain SIMULATED provenance. Gateway adds only `/api/v1/telemetry/**` with existing transport controls.

Bounded MQTT worker/manual ACK, retries and outbox confirms/returns/NACK/timeout behavior preserve original identity through failure. Unknown/exhausted work stops visibly for operator-controlled recovery; no automatic trust change or unbounded retry. Internal aggregate gauges and health indicate MQTT halt/outbox exhaustion without public identity-bearing details. Failed outbox rows remain for scoped operator investigation; there is no general replay/delete API. Generated fixture broker uses per-principal ACLs, username-as-client-ID, packet/queue/in-flight caps and idempotent password-file initialization.

## Run independent checks

Requires Java 25 and Docker for integration. From this repository:

```powershell
python scripts/validate_contract_pins.py
python -m pip install -r requirements-test.txt
.\mvnw.cmd --batch-mode --no-transfer-progress verify -Pintegration
python scripts/validate_telemetry_runtime.py
```

For a workspace with concurrent IDE output, use `'-Dtelemetry.build.directory=target/tlm01-adapter-build'` in the Maven command. Default output remains `target`. This avoids sharing compiled classes, without removing existing build output. Integration exports generated HTTP/event fixtures under `target/tlm01-wire`; the final Python command checks actual shapes against this repository's pins only. CI runs these commands; no hosted CI result is asserted here.

The integration suite creates random, loopback-bound owned Postgres/Mosquitto/RabbitMQ resources, generated credentials and signed JWT/reference fixtures; it closes its resources and deletes its credential directory in `finally`. Runtime permissions are exercised with an actual generated LOGIN, not SET ROLE alone. Cross-database, DDL and mutation denial tests retain separate migration ownership. No existing stack or private .env is used.

For the real Keycloak/Gateway/Estate drill, use Infrastructure `python scripts/verify_telemetry.py`. It reuses the new-project tapping harness and adds only its own labelled containers, generated files and bounded durable sink. It builds services independently, validates real PKCE/JWT/reference/API/event flow and duplicate no-op, then checks exact owned-container and project-volume/network cleanup. It reads no developer configuration. Run only when Docker is available. The initial 2026-11-04 attempt could not reach Docker's engine and is retained as unavailable. After the owner made Docker available, the final-source drill passed with owned cleanup verification.

## Activation and recovery

Do not start this code against an existing environment under this approval. Default `telemetry.demo.enabled`, `telemetry.mqtt.enabled` and `telemetry.outbox.enabled` are disabled. The generated fixture explicitly supplies the true flags, owned manifest path/hash, runtime/migration database credentials, Estate URL, issuer/audience/JWKS and RabbitMQ settings. All secrets stay in protected generated transient files/environment; no command-line password arguments, tracked credentials or ambient private-file discovery. The run marker, real owned root, ACL/config hashes, active registry/run/window and loopback broker endpoint must all match.

Stop on unknown target/trust, exhausted processing, revoked/expired dependency identity or ambiguous commit. Re-observe stored UUID/outbox state; preserve original observation/event IDs. Restart only the same authorized fixture after dependencies recover. A changed endpoint or manifest needs explicit fixture-owner re-verification while ingestion is stopped, not automatic rebinding. Original timestamps never reset. Broker session lifetime and fixture admission window are demo limits, not field retention/access or outage promises. In a persistent environment the corresponding privacy, retention, security, backup/recovery and operating gates still apply.

No REAL device/account rollout, product simulator/Web overlay, existing-stack upgrade, hosting/deployment, Git publication or AEO change is delivered. Parent M3, M1/M2 acceptance and the original M0–M5 finish line remain open; field Android/offline/English–French requirements remain separate.
