package com.agrosphere.telemetry.adapter.out.persistence;

import com.agrosphere.telemetry.domain.*;
import com.agrosphere.telemetry.domain.ObservationPolicy.*;
import com.agrosphere.telemetry.application.port.out.TelemetryPorts.*;
import com.agrosphere.telemetry.adapter.in.web.ObservationJson;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

public final class JdbcTelemetry implements Store {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final ObservationJson json=new ObservationJson();
    public JdbcTelemetry(JdbcTemplate jdbc,TransactionTemplate transaction){this.jdbc=jdbc;this.transaction=transaction;transaction.setTimeout(10);}
    public Optional<DeviceRegistration> find(UUID id){return jdbc.query("SELECT * FROM telemetry_device WHERE device_id=?",
        (r,n)->new DeviceRegistration(id,r.getObject("estate_id",UUID.class),r.getObject("block_id",UUID.class),r.getString("publisher_principal"),r.getBoolean("active")),id).stream().findFirst();}
    public Window window(UUID device){return jdbc.queryForObject("SELECT window_start,window_end FROM telemetry_device WHERE device_id=?",
        (r,n)->new Window(r.getTimestamp(1).toInstant(),r.getTimestamp(2).toInstant()),device);}
    public UUID run(UUID device){return jdbc.queryForObject("SELECT generated_run_id FROM telemetry_device WHERE device_id=?",UUID.class,device);}
    public void registerGenerated(DeviceRegistration ignored){throw new UnsupportedOperationException("Generated migrator fixture only");}
    public SaveOutcome acceptAtomically(Recorded recorded,UUID event,UUID correlation){
      return transaction.execute(status->{
        jdbc.execute("SET LOCAL lock_timeout='2s'");jdbc.execute("SET LOCAL statement_timeout='10s'");
        var o=recorded.observation();
        int inserted=jdbc.update("INSERT INTO telemetry_observation(observation_id,device_id,estate_id,block_id,schema_version,data_provenance,kind,unit,value,observed_at,received_at,interval_start,interval_end) VALUES(?,?,?,?,1,'SIMULATED',?,?,?,?,?,?,?) ON CONFLICT(observation_id) DO NOTHING",
          o.observationId(),o.deviceId(),o.estateId(),o.blockId(),o.kind().name(),o.unit(),o.value(),Timestamp.from(o.observedAt()),Timestamp.from(recorded.receivedAt()),o.interval()==null?null:Timestamp.from(o.interval().start()),o.interval()==null?null:Timestamp.from(o.interval().end()));
        if(inserted==0){var original=jdbc.queryForObject("SELECT * FROM telemetry_observation WHERE observation_id=?",this::map,o.observationId());
          return ObservationPolicy.replay(o,original.observation())==Replay.DUPLICATE?SaveOutcome.DUPLICATE:SaveOutcome.CONFLICT;}
        jdbc.update("INSERT INTO outbox_event(event_id,aggregate_id,event_type,event_version,correlation_id,occurred_at,payload) VALUES(?,?,'TelemetryObservationRecorded',1,?,?,?::jsonb)",event,o.observationId(),correlation,Timestamp.from(recorded.receivedAt()),json.event(o,recorded.receivedAt(),event,correlation));
        return SaveOutcome.INSERTED;
      });
    }
    private Recorded map(ResultSet r,int row)throws SQLException{
      Timestamp start=r.getTimestamp("interval_start"),end=r.getTimestamp("interval_end");
      return new Recorded(new Observation(r.getObject("observation_id",UUID.class),r.getObject("device_id",UUID.class),r.getObject("estate_id",UUID.class),r.getObject("block_id",UUID.class),Observation.Kind.valueOf(r.getString("kind")),r.getString("unit"),r.getBigDecimal("value"),r.getTimestamp("observed_at").toInstant(),r.getString("data_provenance"),start==null?null:new Observation.Interval(start.toInstant(),end.toInstant())),r.getTimestamp("received_at").toInstant());
    }
    public List<Recorded> latest(UUID estate,UUID block){return jdbc.query("SELECT DISTINCT ON(kind) * FROM telemetry_observation WHERE estate_id=? AND block_id=? ORDER BY kind,observed_at DESC,(observation_id::text) COLLATE \"C\" DESC",this::map,estate,block);}
}
