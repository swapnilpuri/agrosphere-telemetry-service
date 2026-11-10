package com.agrosphere.telemetry.application.port.out;

import com.agrosphere.telemetry.domain.DeviceRegistration;
import com.agrosphere.telemetry.domain.ObservationPolicy.Recorded;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Adapter obligations, not implemented persistence/security guarantees. */
public final class TelemetryPorts {
    private TelemetryPorts() { }
    public interface Registry {
        Optional<DeviceRegistration> find(UUID deviceId);
        /** Immutable mapping, identical retry is a no-op; conflicting bootstrap must fail. */
        void registerGenerated(DeviceRegistration registration);
    }
    public interface EstateReference {
        /** Authorized HTTP only. Unavailable reference fails closed; never assume active. */
        boolean activeBlock(UUID estateId, UUID blockId);
    }
    public enum SaveOutcome { INSERTED, DUPLICATE, CONFLICT }
    public interface Observations {
        /** One transaction: global observation identity reservation, record, latest and durable outbox.
         * Duplicate returns original receipt without a new event; conflicting payload never overwrites.
         * Runtime adapter must arbitrate races and validate scope before invoking this port. */
        SaveOutcome acceptAtomically(Recorded recorded, UUID eventId, UUID correlationId);
        /** Invoke only after trusted role/estate and authoritative reference authorization. */
        List<Recorded> latest(UUID estateId, UUID blockId);
    }
    public interface Store extends Registry, Observations {
        com.agrosphere.telemetry.domain.ObservationPolicy.Window window(UUID device);
    }
    public interface Reference {
        boolean active(UUID estate, UUID block, String bearer);
    }
    public interface Channel {
        DeviceRegistration registration(String topic);
        String estateBearer();
    }
    public interface Decoder {
        com.agrosphere.telemetry.domain.Observation parse(byte[] bytes);
    }
}
