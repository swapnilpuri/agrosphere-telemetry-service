package com.agrosphere.telemetry;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Protect exact independently reviewed bytes actually packaged by this build. */
class ContractPinsTest {
    @Test void packagedContractsMatchAcceptedIndependentRelease() throws Exception {
        var expected = Map.of(
            "openapi/telemetry-service.yaml", "140ec8ce185dee4e62d5ef53309f06b3319d4be77d28d8078fd5cb9126c58ffa",
            "mqtt/Observation.schema.json", "bf3a58d8e5929f367a95344fc7a34c4f548ad9c44fc833d7415dbe8e7b44b255",
            "events/telemetry/v1/TelemetryObservationRecorded.schema.json", "43f652fbbe98c6be3e545307a0fc6c50b2ebce0834c986dfe87d5c3bc5a4a2d2",
            "RELEASE.json", "be4cd4065bce3ae8bafc8e4c6204b19ff1803700ae58130e765168e10807bbc1");
        for (var entry : expected.entrySet()) {
            try (var stream = getClass().getResourceAsStream("/contracts/" + entry.getKey())) {
                assertNotNull(stream, entry.getKey());
                assertEquals(entry.getValue(), HexFormat.of().formatHex(
                        MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes())), entry.getKey());
            }
        }
    }
}
