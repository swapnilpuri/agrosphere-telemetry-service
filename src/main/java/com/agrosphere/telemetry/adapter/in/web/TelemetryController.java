package com.agrosphere.telemetry.adapter.in.web;
import com.agrosphere.telemetry.adapter.out.persistence.JdbcTelemetry;
import com.agrosphere.telemetry.adapter.out.web.EstateClient;
import com.agrosphere.telemetry.domain.ObservationPolicy;
import java.time.*;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;
@RestController
@ConditionalOnProperty(name="telemetry.demo.enabled",havingValue="true")
public class TelemetryController {
 private final JdbcTelemetry store;private final EstateClient estate;private final Clock clock;
 private final TelemetryAccess access=new TelemetryAccess();private final ObservationJson json=new ObservationJson();
 public TelemetryController(JdbcTelemetry store,EstateClient estate,Clock clock){this.store=store;this.estate=estate;this.clock=clock;}
 @GetMapping("/api/v1/telemetry/estates/{estateId}/blocks/{blockId}/latest")
 public Map<String,Object> latest(@PathVariable String estateId,@PathVariable String blockId){
  UUID e=ObservationJson.id(estateId),b=ObservationJson.id(blockId);String bearer=access.read(e);
  estate.active(e,b,bearer);var rows=store.latest(e,b);var now=clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
  return Map.of("estateId",e,"blockId",b,"generatedAt",now.toString(),"dataProvenance","SIMULATED","coverage",ObservationPolicy.coverage(rows).name(),"readings",rows.stream().map(r->json.view(r.observation(),r.receivedAt())).toList());
 }
}
