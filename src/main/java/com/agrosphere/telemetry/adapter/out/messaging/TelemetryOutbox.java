package com.agrosphere.telemetry.adapter.out.messaging;
import java.time.*;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
public final class TelemetryOutbox {
 private final JdbcTemplate jdbc;private final TransactionTemplate transaction;private final RabbitTemplate rabbit;
 public final AtomicLong failed=new AtomicLong();
 public TelemetryOutbox(JdbcTemplate jdbc,TransactionTemplate transaction,RabbitTemplate rabbit){this.jdbc=jdbc;this.transaction=transaction;this.rabbit=rabbit;}
 public boolean exhausted(){return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM outbox_event WHERE published_at IS NULL AND attempts>=3)",Boolean.class));}
 public void dispatch(){transaction.executeWithoutResult(status->{
  jdbc.execute("SET LOCAL lock_timeout='2s'");jdbc.execute("SET LOCAL statement_timeout='10s'");
  var rows=jdbc.queryForList("SELECT event_id,payload::text,attempts FROM outbox_event WHERE published_at IS NULL AND attempts<3 AND next_attempt_at<=now() ORDER BY occurred_at,event_id LIMIT 20 FOR UPDATE SKIP LOCKED");
  for(var row:rows){UUID id=(UUID)row.get("event_id");int attempt=((Number)row.get("attempts")).intValue()+1;boolean confirmed=false;
   try{
    var p=new MessageProperties();p.setContentType("application/json");p.setDeliveryMode(MessageDeliveryMode.PERSISTENT);p.setMessageId(id.toString());
    String body=row.get("payload").toString();p.setCorrelationId(new tools.jackson.databind.ObjectMapper().readTree(body).path("correlationId").asText());
    var data=new CorrelationData(id.toString());rabbit.send("agrosphere.domain","agrosphere.TelemetryObservationRecorded.v1",new Message(body.getBytes(java.nio.charset.StandardCharsets.UTF_8),p),data);
    confirmed=data.getFuture().get(3,TimeUnit.SECONDS).ack()&&data.getReturned()==null;
   }catch(Exception e){if(e instanceof InterruptedException)Thread.currentThread().interrupt();}
   // A post-confirm database error rolls back, intentionally allowing same-event redelivery.
   if(confirmed)jdbc.update("UPDATE outbox_event SET published_at=now(),attempts=? WHERE event_id=?",attempt,id);
   else {failed.incrementAndGet();jdbc.update("UPDATE outbox_event SET attempts=?,next_attempt_at=? WHERE event_id=?",attempt,Timestamp.from(Instant.now().plusSeconds(attempt)),id);}
  }
 });}
}
