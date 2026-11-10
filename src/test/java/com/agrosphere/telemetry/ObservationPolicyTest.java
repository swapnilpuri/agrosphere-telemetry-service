package com.agrosphere.telemetry;

import com.agrosphere.telemetry.domain.DeviceRegistration;
import com.agrosphere.telemetry.domain.Observation;
import com.agrosphere.telemetry.domain.Observation.Kind;
import com.agrosphere.telemetry.domain.Observation.Interval;
import com.agrosphere.telemetry.domain.ObservationPolicy;
import com.agrosphere.telemetry.domain.ObservationPolicy.Recorded;
import com.agrosphere.telemetry.domain.ObservationPolicy.Window;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ObservationPolicyTest {
    final UUID device = id(4), estate = id(5), block = id(6);
    final Instant now = Instant.parse("2026-01-01T12:00:00Z");
    final Window window = new Window(now.minusSeconds(3600), now.plusSeconds(600));
    final DeviceRegistration registration = new DeviceRegistration(device, estate, block, "fixture-device", true);
    static UUID id(int value) { return UUID.fromString("00000000-0000-4000-8000-%012d".formatted(value)); }
    Observation observation(int sequence, Kind kind, String value, Instant at) {
        return new Observation(id(sequence), device, estate, block, kind, kind.unit(), new BigDecimal(value), at,
                "SIMULATED", kind == Kind.INTERVAL_RAINFALL ? new Interval(at.minusSeconds(60), at) : null);
    }
    Recorded admit(Observation o) {
        return ObservationPolicy.admit(o, registration, "fixture-device", registration.topic(), true, window, now);
    }

    @Test void acceptsAllKindsAndKeepsObservationAndReceiptDistinct() {
        for (Kind kind : Kind.values()) {
            var result = admit(observation(1, kind, "2.125", now.minusSeconds(60)));
            assertEquals(now, result.receivedAt());
            assertEquals(now.minusSeconds(60), result.observation().observedAt());
            assertEquals("SIMULATED", result.observation().dataProvenance());
        }
    }
    @Test void unknownInactiveAndAuthoritativelyInactiveDevicesFailClosed() {
        var o = observation(1, Kind.AIR_TEMPERATURE, "27", now);
        assertThrows(SecurityException.class, () -> ObservationPolicy.admit(o, null, "fixture-device", registration.topic(), true, window, now));
        var inactive = new DeviceRegistration(device, estate, block, "fixture-device", false);
        assertThrows(SecurityException.class, () -> ObservationPolicy.admit(o, inactive, "fixture-device", registration.topic(), true, window, now));
        assertThrows(SecurityException.class, () -> ObservationPolicy.admit(o, registration, "fixture-device", registration.topic(), false, window, now));
    }
    @Test void publisherAndTopicCannotBeSuppliedByPayload() {
        var o = observation(1, Kind.AIR_TEMPERATURE, "27", now);
        for (String publisher : List.of("anonymous", "other-device", ""))
            assertThrows(SecurityException.class, () -> ObservationPolicy.admit(o, registration, publisher, registration.topic(), true, window, now));
        for (String topic : List.of(registration.topic() + "/extra", "agrosphere/#", registration.topic().replace(estate.toString(), id(9).toString())))
            assertThrows(SecurityException.class, () -> ObservationPolicy.admit(o, registration, "fixture-device", topic, true, window, now));
    }
    @Test void mappingMustMatchEveryCanonicalIdentifier() {
        var o = observation(1, Kind.AIR_TEMPERATURE, "27", now);
        for (var mapping : List.of(new DeviceRegistration(id(9), estate, block, "fixture-device", true),
                new DeviceRegistration(device, id(9), block, "fixture-device", true),
                new DeviceRegistration(device, estate, id(9), "fixture-device", true)))
            assertThrows(SecurityException.class, () -> ObservationPolicy.admit(o, mapping, "fixture-device", mapping.topic(), true, window, now));
    }
    @Test void exactFutureSkewAndManifestBoundariesAreInclusive() {
        admit(observation(1, Kind.AIR_TEMPERATURE, "27", now.plusSeconds(300)));
        admit(observation(1, Kind.AIR_TEMPERATURE, "27", window.start()));
        assertThrows(IllegalArgumentException.class, () -> admit(observation(1, Kind.AIR_TEMPERATURE, "27", now.plusMillis(300001))));
        assertThrows(IllegalArgumentException.class, () -> admit(observation(1, Kind.AIR_TEMPERATURE, "27", window.start().minusMillis(1))));
        var narrow = new Window(now.minusSeconds(60), now);
        ObservationPolicy.admit(observation(1, Kind.AIR_TEMPERATURE, "27", now), registration, "fixture-device", registration.topic(), true, narrow, now);
        assertThrows(IllegalArgumentException.class, () -> ObservationPolicy.admit(observation(1, Kind.AIR_TEMPERATURE, "27", now.plusMillis(1)), registration, "fixture-device", registration.topic(), true, narrow, now));
    }
    @Test void invalidWindowsAndSubMillisecondTimesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Window(now, now.minusMillis(1)));
        assertThrows(IllegalArgumentException.class, () -> observation(1, Kind.AIR_TEMPERATURE, "1", now.plusNanos(1)));
        assertThrows(IllegalArgumentException.class, () -> new Recorded(observation(1, Kind.AIR_TEMPERATURE, "1", now), now.plusNanos(1)));
        assertThrows(IllegalArgumentException.class, () -> Observation.instant(Instant.parse("+10000-01-01T00:00:00Z")));
    }
    @Test void decimalPrecisionIsExactAndNotAnAgronomicTemperatureThreshold() {
        admit(observation(1, Kind.AIR_TEMPERATURE, "-999999999999.999999", now));
        admit(observation(1, Kind.INTERVAL_RAINFALL, "999999999999.999999", now));
        for (String value : List.of("1000000000000", "0.0000001"))
            assertThrows(IllegalArgumentException.class, () -> observation(1, Kind.AIR_TEMPERATURE, value, now));
        assertThrows(NumberFormatException.class, () -> new BigDecimal("NaN"));
        assertThrows(NumberFormatException.class, () -> new BigDecimal("Infinity"));
    }
    @Test void percentageAndRainfallRangesAreEnforced() {
        admit(observation(1, Kind.SOIL_MOISTURE, "0", now));
        admit(observation(1, Kind.SOIL_MOISTURE, "100", now));
        assertThrows(IllegalArgumentException.class, () -> observation(1, Kind.SOIL_MOISTURE, "100.000001", now));
        assertThrows(IllegalArgumentException.class, () -> observation(1, Kind.SOIL_MOISTURE, "-0.000001", now));
        assertThrows(IllegalArgumentException.class, () -> observation(1, Kind.INTERVAL_RAINFALL, "-1", now));
    }
    @Test void noUnitConversionOrRealDataAdmission() {
        assertThrows(IllegalArgumentException.class, () -> new Observation(id(1), device, estate, block, Kind.AIR_TEMPERATURE, "FAHRENHEIT", BigDecimal.ONE, now, "SIMULATED", null));
        for (String provenance : List.of("REAL", "ESTIMATED", "PREDICTED"))
            assertThrows(IllegalArgumentException.class, () -> new Observation(id(1), device, estate, block, Kind.AIR_TEMPERATURE, "CELSIUS", BigDecimal.ONE, now, provenance, null));
    }
    @Test void rainfallIntervalCannotBeMissingReversedEmptyOrInFuture() {
        assertThrows(IllegalArgumentException.class, () -> new Interval(now, now));
        assertThrows(IllegalArgumentException.class, () -> new Interval(now, now.minusMillis(1)));
        assertThrows(IllegalArgumentException.class, () -> new Observation(id(1), device, estate, block, Kind.INTERVAL_RAINFALL, "MILLIMETRE", BigDecimal.ONE, now, "SIMULATED", null));
        assertThrows(IllegalArgumentException.class, () -> new Observation(id(1), device, estate, block, Kind.INTERVAL_RAINFALL, "MILLIMETRE", BigDecimal.ONE, now, "SIMULATED", new Interval(now, now.plusMillis(1))));
        assertThrows(IllegalArgumentException.class, () -> new Observation(id(1), device, estate, block, Kind.AIR_TEMPERATURE, "CELSIUS", BigDecimal.ONE, now, "SIMULATED", new Interval(now.minusSeconds(1), now)));
    }
    @Test void equivalentDecimalEncodingsAreDuplicateNotConflict() {
        var a = observation(1, Kind.AIR_TEMPERATURE, "27.500000", now);
        var b = observation(1, Kind.AIR_TEMPERATURE, "27.5", now);
        assertEquals(ObservationPolicy.Replay.NEW, ObservationPolicy.replay(a, null));
        assertEquals(ObservationPolicy.Replay.DUPLICATE, ObservationPolicy.replay(a, b));
        assertEquals(ObservationPolicy.Replay.CONFLICT, ObservationPolicy.replay(a, observation(1, Kind.AIR_TEMPERATURE, "27.6", now)));
        assertThrows(IllegalArgumentException.class, () -> ObservationPolicy.replay(a, observation(2, Kind.AIR_TEMPERATURE, "27.5", now)));
    }
    @Test void conflictingMappingUnderSameIdentityCannotOverwrite() {
        var a = observation(1, Kind.AIR_TEMPERATURE, "27", now);
        var b = new Observation(id(1), device, estate, id(9), Kind.AIR_TEMPERATURE, "CELSIUS", new BigDecimal("27"), now, "SIMULATED", null);
        assertEquals(ObservationPolicy.Replay.CONFLICT, ObservationPolicy.replay(a, b));
    }
    @Test void observedTimeWinsOverLaterReceiptAndTieUsesUuidLexicalOrder() {
        var old = new Recorded(observation(2, Kind.AIR_TEMPERATURE, "26", now.minusMillis(1)), now.plusSeconds(30));
        var current = admit(observation(1, Kind.AIR_TEMPERATURE, "27", now));
        assertEquals(current, ObservationPolicy.latest(old, current));
        assertEquals(current, ObservationPolicy.latest(current, old));
        var tie = admit(observation(3, Kind.AIR_TEMPERATURE, "28", now));
        assertEquals(tie, ObservationPolicy.latest(current, tie));
        assertEquals(tie, ObservationPolicy.latest(tie, current));
    }
    @Test void replayPreservesOriginalReceiptAndRejectsConflictingIdentity() {
        var first = admit(observation(1, Kind.AIR_TEMPERATURE, "27", now));
        var retry = new Recorded(first.observation(), now.plusSeconds(30));
        assertEquals(first, ObservationPolicy.latest(first, retry));
        assertThrows(IllegalArgumentException.class, () -> ObservationPolicy.latest(first, admit(observation(1, Kind.AIR_TEMPERATURE, "28", now))));
    }
    @Test void uuidTieOrderIsUnsignedTextOrderAndIsIndependentOfArrivalOrder() {
        var low = admit(observation(1, Kind.AIR_TEMPERATURE, "27", now));
        var high = new Recorded(new Observation(UUID.fromString("ffffffff-ffff-4fff-8fff-ffffffffffff"),
                device, estate, block, Kind.AIR_TEMPERATURE, "CELSIUS", BigDecimal.ONE, now, "SIMULATED", null), now);
        assertEquals(high, ObservationPolicy.latest(low, high));
        assertEquals(high, ObservationPolicy.latest(high, low));
    }
    @Test void latestCannotMixKindsOrScopes() {
        var first = admit(observation(1, Kind.AIR_TEMPERATURE, "27", now));
        assertThrows(IllegalArgumentException.class, () -> ObservationPolicy.latest(first, admit(observation(2, Kind.SOIL_MOISTURE, "27", now))));
        var other = new Observation(id(2), device, estate, id(9), Kind.AIR_TEMPERATURE, "CELSIUS", BigDecimal.ONE, now, "SIMULATED", null);
        assertThrows(IllegalArgumentException.class, () -> ObservationPolicy.latest(first, new Recorded(other, now)));
        assertThrows(IllegalArgumentException.class, () -> ObservationPolicy.coverage(List.of(first,
                new Recorded(new Observation(id(3), device, estate, id(9), Kind.SOIL_MOISTURE,
                        "PERCENT", BigDecimal.ONE, now, "SIMULATED", null), now))));
    }
    @Test void coverageIsPresenceNotFreshnessOrAccuracy() {
        assertEquals(ObservationPolicy.Coverage.NONE, ObservationPolicy.coverage(List.of()));
        var temp = admit(observation(1, Kind.AIR_TEMPERATURE, "27", window.start()));
        assertEquals(ObservationPolicy.Coverage.PARTIAL, ObservationPolicy.coverage(List.of(temp)));
        assertEquals(ObservationPolicy.Coverage.COMPLETE, ObservationPolicy.coverage(List.of(temp,
                admit(observation(2, Kind.SOIL_MOISTURE, "45", now)), admit(observation(3, Kind.INTERVAL_RAINFALL, "1", now)))));
        assertThrows(IllegalArgumentException.class, () -> ObservationPolicy.coverage(List.of(temp, temp)));
    }
    @Test void generatedPrincipalHasNoWildcardsOrTrimming() {
        for (String principal : List.of("", "device/#", " device", "device ", "x".repeat(65)))
            assertThrows(IllegalArgumentException.class, () -> new DeviceRegistration(device, estate, block, principal, true));
    }
}
