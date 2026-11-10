package com.agrosphere.telemetry.domain;

import java.util.Objects;
import java.util.UUID;

/** Immutable assignment. Authenticated publisher identity must come from a trusted adapter. */
public record DeviceRegistration(UUID deviceId, UUID estateId, UUID blockId,
                                 String publisherPrincipal, boolean active) {
    public DeviceRegistration {
        Objects.requireNonNull(deviceId); Objects.requireNonNull(estateId); Objects.requireNonNull(blockId);
        if (publisherPrincipal == null || !publisherPrincipal.matches("[A-Za-z0-9._-]{1,64}"))
            throw new IllegalArgumentException("Invalid generated publisher identity");
    }

    public String topic() {
        return "agrosphere/v1/estates/" + estateId + "/devices/" + deviceId + "/observations";
    }
}
