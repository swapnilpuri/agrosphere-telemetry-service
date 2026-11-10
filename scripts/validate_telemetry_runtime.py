"""Validate actual exported Telemetry responses/events using only local accepted pins.

Requires jsonschema in the chosen development interpreter; no sibling source import.
"""
import json
from pathlib import Path
from jsonschema import Draft202012Validator, FormatChecker
from validate_contract_pins import run as pins, ROOT


def main():
    pins()
    exports = Path(__file__).resolve().parents[1] / 'target/tlm01-wire'
    api = json.loads((ROOT / 'openapi/telemetry-service.yaml').read_text())
    responses = json.loads((exports / 'responses.json').read_text())
    assert responses and {x['schema'] for x in responses} == {'LatestReadings', 'Problem'}
    for response in responses:
        schema = api['components']['schemas'][response['schema']]
        Draft202012Validator(schema, format_checker=FormatChecker()).validate(response['value'])
        if response['schema'] == 'LatestReadings':
            value = response['value']
            kinds = [reading['kind'] for reading in value['readings']]
            assert kinds == sorted(set(kinds))
            assert value['coverage'] == ('NONE' if not kinds else 'COMPLETE' if len(kinds) == 3 else 'PARTIAL')
            for reading in value['readings']:
                assert reading['estateId'] == value['estateId'] and reading['blockId'] == value['blockId']
    schema = json.loads((ROOT / 'events/telemetry/v1/TelemetryObservationRecorded.schema.json').read_text())
    events = json.loads((exports / 'events.json').read_text())
    assert events
    for event in events:
        Draft202012Validator(schema, format_checker=FormatChecker()).validate(event)
        assert event['aggregateId'] == event['payload']['observationId']
        assert event['timestamp'] == event['payload']['receivedAt']
        assert event['estateId'] == event['payload']['estateId'] and event['blockId'] == event['payload']['blockId']
    print(f'PASS: {len(responses)} actual HTTP shapes and {len(events)} actual event envelopes match local pins')


if __name__ == '__main__':
    main()
