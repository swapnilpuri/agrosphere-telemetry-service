package com.agrosphere.telemetry.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Pure SIMULATED observation. Candidate wire precision is not a released contract. */
public record Observation(UUID observationId, UUID deviceId, UUID estateId, UUID blockId,
                          Kind kind, String unit, BigDecimal value, Instant observedAt,
                          String dataProvenance, Interval interval) {
    public enum Kind {
        AIR_TEMPERATURE("CELSIUS"), SOIL_MOISTURE("PERCENT"), INTERVAL_RAINFALL("MILLIMETRE");
        private final String unit;
        Kind(String unit) { this.unit = unit; }
        public String unit() { return unit; }
    }

    public record Interval(Instant start, Instant end) {
        public Interval {
            instant(start); instant(end);
            if (!start.isBefore(end)) throw new IllegalArgumentException("Invalid rainfall interval");
        }
    }

    public Observation {
        Objects.requireNonNull(observationId); Objects.requireNonNull(deviceId);
        Objects.requireNonNull(estateId); Objects.requireNonNull(blockId); Objects.requireNonNull(kind);
        instant(observedAt);
        if (!kind.unit().equals(unit) || !"SIMULATED".equals(dataProvenance))
            throw new IllegalArgumentException("Invalid unit or provenance");
        Objects.requireNonNull(value);
        value = value.stripTrailingZeros();
        if (value.scale() > 6 || value.abs().compareTo(new BigDecimal("999999999999.999999")) > 0)
            throw new IllegalArgumentException("Unrepresentable measurement");
        if (kind == Kind.SOIL_MOISTURE && (value.signum() < 0 || value.compareTo(new BigDecimal("100")) > 0))
            throw new IllegalArgumentException("Moisture outside percent range");
        if (kind == Kind.INTERVAL_RAINFALL) {
            if (value.signum() < 0 || interval == null || interval.end().isAfter(observedAt))
                throw new IllegalArgumentException("Invalid interval rainfall");
        } else if (interval != null) throw new IllegalArgumentException("Unexpected interval");
    }

    public static void instant(Instant value) {
        Objects.requireNonNull(value);
        if (value.getNano() % 1_000_000 != 0 || value.isBefore(Instant.parse("0001-01-01T00:00:00Z"))
                || value.isAfter(Instant.parse("9999-12-31T23:59:59.999Z")))
            throw new IllegalArgumentException("Timestamp outside candidate UTC millisecond bounds");
    }
}
