# Repository instructions

Work only within this independent Git repository. No parent repository, submodules, shared persistence models, cross-service SQL or multi-service Maven parent.

Current scope: Milestone 0 foundation only. Do not add business APIs, GIS functionality, AI models or Unreal gameplay/assets without a new milestone request.

Ownership: Device registry, MQTT ingestion, weather/soil readings, normalization and environmental anomaly events.

Use ports/adapters and service-owned migrations. APIs and events are designed in agrosphere-contracts; coordinate breaking changes through a versioned contract release, never a source path dependency. UUID estateId/blockId remain stable across clients. Mark data REAL, SIMULATED, ESTIMATED or PREDICTED. GeoJSON is WGS84 longitude/latitude (RFC 7946). Procedural trees are illustrative.

Never commit credentials, local environment files, generated datasets or large geospatial assets. Use environment examples containing placeholders only. No remote creation, push or cloud deployment is authorized.

Run repository checks documented in README.md. Report actual checks separately from unavailable checks. Version independently using SemVer and CHANGELOG.md; changes to 0.x contracts can still break consumers and require review.


## Owner-approved TLM-01 preparation — 2026-11-01

Architecture docs/milestones/MQTT_POC_APPROVAL.md records acceptance of exact MQTT_POC_REVIEW.md SHA-256 84ba22535e81de2296d76746c612325571ea6364962bb42d7fe2138716dd3dbd. Authorizes isolated Contracts candidate and Telemetry pure domain/ports/tests only, SIMULATED. Exact executable acceptance/local release precedes promotion/pins/adapters; broker ACL/provisioning/migrations need later scoped review. Preserve all old release bytes, V1, indexes/config/data and independent builds. No live MQTT, database, actual account, role/credential, private configuration, REAL, Git publication/deployment or AEO action.


## TLM-01 exact local release/pins approved — 2026-11-01

Architecture MQTT_POC_CONTRACT_APPROVAL.md records owner acceptance of all eight artifacts in manifest 174140bc1db129ab879d9e431e9065a6715746c31c70fa0ec2bb7992063f01b0 and local Contracts 0.10.0 archive/promotion/release metadata plus exact Telemetry API/MQTT/event pins. Historical pending labels are satisfied only for this scope. Preserve exact candidate/review/archive/old artifacts and all existing indexes/config/data. Prepare broker ACL/provisioning/migration review before adapters; no live stack, role/credential, private configuration, REAL, Git publication/deployment or AEO action.


## TLM-01 adapters authorized — 2026-11-01

Architecture MQTT_POC_ADAPTER_APPROVAL.md accepts exact package 389f1d3cfa6ca80fd602c2f945b9f23965723b53d5ca169919f7e11099e2bc75. Implement Telemetry SIMULATED ingestion/persistence/outbox/latest API, Gateway telemetry route only and Infrastructure generated-only owned disposable drills. Exact SQL/ACL/client engineering review and generated fixture credentials included. No repeated domain/executable approval. Preserve Contracts 0.10.0 pins, old migrations/config/data/indexes, existing stacks/private config/roles/credentials, REAL/Git/deployment/AEO exclusions.
