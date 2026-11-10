package com.agrosphere.telemetry.adapter.in.web;

import com.agrosphere.telemetry.domain.Observation;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.time.Instant;
import java.util.*;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.*;
import tools.jackson.databind.json.JsonMapper;

/** Strict bounded wire adapter, independent of sibling sources. */
public final class ObservationJson implements com.agrosphere.telemetry.application.port.out.TelemetryPorts.Decoder {
    private final ObjectMapper json = JsonMapper.builder(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
            .enable(tools.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    public Observation parse(byte[] bytes) {
        try {
            if (bytes.length == 0 || bytes.length > 4096) throw new IllegalArgumentException();
            String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            JsonNode n = json.readTree(text);
            var fields = new HashSet<>(Set.of("schemaVersion","observationId","deviceId","estateId","blockId",
                    "kind","unit","value","observedAt","dataProvenance"));
            var kind = Observation.Kind.valueOf(string(n,"kind"));
            if (kind == Observation.Kind.INTERVAL_RAINFALL) fields.addAll(Set.of("intervalStart","intervalEnd"));
            if (!n.isObject() || !new HashSet<>(n.propertyNames()).equals(fields)
                    || !n.path("schemaVersion").isIntegralNumber() || n.path("schemaVersion").asInt()!=1)
                throw new IllegalArgumentException();
            String value=string(n,"value");
            if (!value.matches("-?(?:0|[1-9][0-9]{0,11})(?:\\.[0-9]{1,6})?")) throw new IllegalArgumentException();
            return new Observation(id(string(n,"observationId")),id(string(n,"deviceId")),id(string(n,"estateId")),
                    id(string(n,"blockId")),kind,string(n,"unit"),new BigDecimal(value),time(string(n,"observedAt")),
                    string(n,"dataProvenance"),kind==Observation.Kind.INTERVAL_RAINFALL
                    ?new Observation.Interval(time(string(n,"intervalStart")),time(string(n,"intervalEnd"))):null);
        } catch (Exception e) { throw new IllegalArgumentException("Invalid telemetry observation"); }
    }
    private String string(JsonNode n,String key) {
        if (!n.path(key).isTextual()) throw new IllegalArgumentException();
        return n.path(key).asText();
    }
    public static UUID id(String value) {
        if (!value.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) throw new IllegalArgumentException();
        return UUID.fromString(value);
    }
    public static Instant time(String value) {
        if (!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(?:\\.[0-9]{1,3})?Z")
                || value.contains(":60")) throw new IllegalArgumentException();
        Instant result=Instant.parse(value); Observation.instant(result); return result;
    }
    public Map<String,Object> view(Observation o,Instant received) {
        Map<String,Object> v=new LinkedHashMap<>();
        v.put("schemaVersion",1);v.put("observationId",o.observationId());v.put("deviceId",o.deviceId());
        v.put("estateId",o.estateId());v.put("blockId",o.blockId());v.put("kind",o.kind().name());
        v.put("unit",o.unit());v.put("value",o.value().toPlainString());v.put("observedAt",o.observedAt().toString());
        v.put("dataProvenance","SIMULATED");v.put("receivedAt",received.toString());
        if(o.interval()!=null){v.put("intervalStart",o.interval().start().toString());v.put("intervalEnd",o.interval().end().toString());}
        return v;
    }
    public String event(Observation o,Instant received,UUID event,UUID correlation) {
        return json.writeValueAsString(Map.ofEntries(Map.entry("eventId",event),Map.entry("eventType","TelemetryObservationRecorded"),
                Map.entry("eventVersion",1),Map.entry("timestamp",received.toString()),Map.entry("source","agrosphere-telemetry-service"),
                Map.entry("correlationId",correlation),Map.entry("estateId",o.estateId()),Map.entry("blockId",o.blockId()),
                Map.entry("dataProvenance","SIMULATED"),Map.entry("aggregateId",o.observationId()),Map.entry("aggregateVersion",1),
                Map.entry("payload",view(o,received))));
    }
}
