"""Verify this repository's independent exact Contracts 0.10.0 pins; stdlib only."""
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / 'src/main/resources/contracts'
MANIFEST = '174140bc1db129ab879d9e431e9065a6715746c31c70fa0ec2bb7992063f01b0'
EXPECTED = {
    'openapi/telemetry-service.yaml': '140ec8ce185dee4e62d5ef53309f06b3319d4be77d28d8078fd5cb9126c58ffa',
    'mqtt/Observation.schema.json': 'bf3a58d8e5929f367a95344fc7a34c4f548ad9c44fc833d7415dbe8e7b44b255',
    'events/telemetry/v1/TelemetryObservationRecorded.schema.json': '43f652fbbe98c6be3e545307a0fc6c50b2ebce0834c986dfe87d5c3bc5a4a2d2',
}


def run():
    release = json.loads((ROOT / 'RELEASE.json').read_text(encoding='utf-8'))
    assert release['release'] == '0.10.0' and release['sourceManifestSha256'] == MANIFEST
    assert release['files'] == EXPECTED
    for relative, expected in EXPECTED.items():
        assert hashlib.sha256((ROOT / relative).read_bytes()).hexdigest() == expected, relative
    print('PASS: three exact local Contracts 0.10.0 pins; no sibling source required')


if __name__ == '__main__':
    run()
