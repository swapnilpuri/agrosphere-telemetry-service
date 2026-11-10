package com.agrosphere.telemetry.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Pure rules only: no MQTT/JWT parsing, database or event side effects. */
public final class ObservationPolicy {
    private ObservationPolicy() { }
    public record Window(Instant start, Instant end) {
        public Window {
            Observation.instant(start); Observation.instant(end);
            if (start.isAfter(end)) throw new IllegalArgumentException("Reversed manifest window");
        }
    }
    public record Recorded(Observation observation, Instant receivedAt) {
        public Recorded { Objects.requireNonNull(observation); Observation.instant(receivedAt); }
    }
    public enum Replay { NEW, DUPLICATE, CONFLICT }
    public enum Coverage { NONE, PARTIAL, COMPLETE }

    public static Recorded admit(Observation observation, DeviceRegistration registration,
                                 String trustedPublisher, String topic, boolean authoritativeBlockActive,
                                 Window window, Instant receivedAt) {
        Objects.requireNonNull(observation); Objects.requireNonNull(window);
        if (registration == null) throw new SecurityException("Unknown device");
        Observation.instant(receivedAt);
        if (!registration.active() || !authoritativeBlockActive
                || !registration.publisherPrincipal().equals(trustedPublisher)
                || !registration.topic().equals(topic)
                || !registration.deviceId().equals(observation.deviceId())
                || !registration.estateId().equals(observation.estateId())
                || !registration.blockId().equals(observation.blockId()))
            throw new SecurityException("Publisher or mapping denied");
        Instant at = observation.observedAt();
        if (at.isBefore(window.start()) || at.isAfter(window.end())
                || Duration.between(receivedAt, at).compareTo(Duration.ofMinutes(5)) > 0)
            throw new IllegalArgumentException("Observation outside admission window");
        return new Recorded(observation, receivedAt);
    }

    /** Received time is not part of canonical publisher payload identity. */
    public static Replay replay(Observation incoming, Observation existing) {
        Objects.requireNonNull(incoming);
        if (existing == null) return Replay.NEW;
        if (!incoming.observationId().equals(existing.observationId()))
            throw new IllegalArgumentException("Lookup must use incoming observation identity");
        return incoming.equals(existing) ? Replay.DUPLICATE : Replay.CONFLICT;
    }

    public static Recorded latest(Recorded left, Recorded right) {
        Objects.requireNonNull(left); Objects.requireNonNull(right);
        Observation a = left.observation(), b = right.observation();
        if (!a.estateId().equals(b.estateId()) || !a.blockId().equals(b.blockId()) || a.kind() != b.kind())
            throw new IllegalArgumentException("Different latest-reading key");
        if (a.observationId().equals(b.observationId())) {
            if (!a.equals(b)) throw new IllegalArgumentException("Conflicting observation identity");
            // Keep original accepted receipt; replay admission is never an update.
            return left;
        }
        int comparison = Comparator.comparing(Observation::observedAt)
                .thenComparing(o -> o.observationId().toString()).compare(a, b);
        return comparison >= 0 ? left : right;
    }

    public static Coverage coverage(List<Recorded> latest) {
        Objects.requireNonNull(latest);
        if (latest.size() > Observation.Kind.values().length)
            throw new IllegalArgumentException("Too many latest readings");
        if (!latest.isEmpty()) {
            Observation first = latest.getFirst().observation();
            if (latest.stream().anyMatch(r -> !r.observation().estateId().equals(first.estateId())
                    || !r.observation().blockId().equals(first.blockId()))
                    || latest.stream().map(r -> r.observation().kind()).distinct().count() != latest.size())
                throw new IllegalArgumentException("Invalid latest-reading set");
        }
        return latest.isEmpty() ? Coverage.NONE
                : latest.size() == Observation.Kind.values().length ? Coverage.COMPLETE : Coverage.PARTIAL;
    }
}
