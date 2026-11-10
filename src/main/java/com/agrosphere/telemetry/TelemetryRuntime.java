package com.agrosphere.telemetry;
import com.agrosphere.telemetry.adapter.in.messaging.*;
import com.agrosphere.telemetry.adapter.out.messaging.TelemetryOutbox;
import com.agrosphere.telemetry.adapter.out.persistence.JdbcTelemetry;
import com.agrosphere.telemetry.adapter.out.web.EstateClient;
import com.agrosphere.telemetry.application.TelemetryIngestion;
import java.nio.file.Path;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.context.SmartLifecycle;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.*;
import org.springframework.boot.health.contributor.*;
import io.micrometer.core.instrument.MeterRegistry;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name="telemetry.demo.enabled",havingValue="true")
public class TelemetryRuntime {
 @Bean @ConditionalOnProperty(name="telemetry.mqtt.enabled",havingValue="true")
 MqttJob mqttLifecycle(JdbcTelemetry store,EstateClient estate,Clock clock,MeterRegistry metrics,
   @Value("${telemetry.fixture.manifest}")String manifest,@Value("${telemetry.fixture.sha256}")String hash){
  var channel=new GeneratedChannel(Path.of(manifest),hash,store);
  var transport=new MqttTelemetry(channel,new TelemetryIngestion(store,estate,channel,clock,new com.agrosphere.telemetry.adapter.in.web.ObservationJson()));
  metrics.gauge("telemetry.ingress.accepted",transport.accepted);
  metrics.gauge("telemetry.ingress.duplicate",transport.duplicate);
  metrics.gauge("telemetry.ingress.rejected",transport.rejected);
  metrics.gauge("telemetry.ingress.unknown",transport.unknown);
  return new MqttJob(transport);
 }
 public static class MqttJob implements SmartLifecycle,HealthIndicator {
  private final MqttTelemetry transport;private boolean running;
  MqttJob(MqttTelemetry transport){this.transport=transport;}
  public void start(){try{transport.start();running=true;}catch(Exception e){transport.close();throw new IllegalStateException("Generated MQTT startup failed");}}
  public void stop(){transport.close();running=false;}
  public boolean isRunning(){return running&&!transport.halted();}
  public Health health(){return isRunning()?Health.up().build():Health.down().build();}
 }
 @Bean @ConditionalOnProperty(name="telemetry.outbox.enabled",havingValue="true")
 OutboxJob outboxJob(JdbcTemplate jdbc,PlatformTransactionManager manager,RabbitTemplate rabbit){return new OutboxJob(new TelemetryOutbox(jdbc,new TransactionTemplate(manager),rabbit));}
 public static class OutboxJob implements HealthIndicator {
  private final TelemetryOutbox outbox;
  public OutboxJob(TelemetryOutbox outbox){this.outbox=outbox;}
  @Scheduled(fixedDelayString="${telemetry.outbox.poll-ms:1000}")public void dispatch(){outbox.dispatch();}
  public Health health(){try{return outbox.exhausted()?Health.down().build():Health.up().build();}catch(Exception e){return Health.down().build();}}
 }
}
