package com.agrosphere.telemetry.application;
import com.agrosphere.telemetry.application.port.out.TelemetryPorts.*;
import com.agrosphere.telemetry.domain.ObservationPolicy;
import com.agrosphere.telemetry.application.port.out.TelemetryPorts.SaveOutcome;
import java.time.*;
import java.util.UUID;
public final class TelemetryIngestion {
 private final Store store;private final Reference estate;private final Channel channel;private final Clock clock;private final Decoder json;
 public TelemetryIngestion(Store store,Reference estate,Channel channel,Clock clock,Decoder json){this.store=store;this.estate=estate;this.channel=channel;this.clock=clock;this.json=json;}
 public SaveOutcome receive(String topic,byte[] payload,boolean retained){
  if(retained)throw new IllegalArgumentException("Retained observation rejected");
  var registration=channel.registration(topic);var observation=json.parse(payload);
  var received=clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
  // Authorization/mapping checks precede any dependency call, as well as persistence.
  var admitted=ObservationPolicy.admit(observation,registration,registration.publisherPrincipal(),topic,true,store.window(registration.deviceId()),received);
  estate.active(registration.estateId(),registration.blockId(),channel.estateBearer());
  return store.acceptAtomically(admitted,UUID.randomUUID(),UUID.randomUUID());
 }
}
