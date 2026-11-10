package com.agrosphere.telemetry.adapter.in.messaging;
import com.agrosphere.telemetry.application.TelemetryIngestion;
import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.springframework.web.server.ResponseStatusException;

/** Bounded worker; manual broker ACK is issued only after committed acceptance or visible rejection. */
public final class MqttTelemetry implements AutoCloseable {
 private final GeneratedChannel channel;private final TelemetryIngestion ingestion;private final Mqtt5AsyncClient client;
 private final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(100));
 private final AtomicBoolean halted=new AtomicBoolean();
 public final AtomicLong accepted=new AtomicLong(),duplicate=new AtomicLong(),rejected=new AtomicLong(),unknown=new AtomicLong();
 public MqttTelemetry(GeneratedChannel channel,TelemetryIngestion ingestion){
  this.channel=channel;this.ingestion=ingestion;
  String clientId=channel.text("clientId");if(!clientId.matches("[A-Za-z0-9._-]{1,64}"))throw new IllegalArgumentException("Client ID required");
  client=MqttClient.builder().useMqttVersion5().identifier(clientId).serverHost(channel.text("host")).serverPort(channel.port())
    .addDisconnectedListener(context->{if(!halted.getAndSet(true))unknown.incrementAndGet();}).buildAsync();
 }
 public void start()throws Exception{
  channel.verify();client.publishes(com.hivemq.client.mqtt.MqttGlobalPublishFilter.ALL,this::enqueue,true);
  client.connectWith().cleanStart(false).sessionExpiryInterval(3600).restrictions().receiveMaximum(10).applyRestrictions()
    .simpleAuth().username(channel.text("subscriberUser")).password(channel.secret("passwordFile").getBytes(StandardCharsets.UTF_8)).applySimpleAuth().send().get(10,TimeUnit.SECONDS);
  for(String topic:channel.topics()){
   var ack=client.subscribeWith().topicFilter(topic).qos(MqttQos.AT_LEAST_ONCE).retainAsPublished(true).send().get(10,TimeUnit.SECONDS);
   if(ack.getReasonCodes().stream().anyMatch(code->code.isError()))throw new SecurityException("Subscription denied");
  }
 }
 private void enqueue(Mqtt5Publish publish){
  if(halted.get())return;
  try{worker.execute(()->consume(publish));}catch(RejectedExecutionException e){stopUnknown();}
 }
 private void consume(Mqtt5Publish publish){
  if(halted.get())return;
  for(int attempt=0;attempt<3;attempt++){
   try{
    channel.verify();byte[] payload=publish.getPayloadAsBytes();
    var result=ingestion.receive(publish.getTopic().toString(),payload,publish.isRetain());
    switch(result){case INSERTED->accepted.incrementAndGet();case DUPLICATE->duplicate.incrementAndGet();case CONFLICT->rejected.incrementAndGet();}
    publish.acknowledge();return;
   }catch(IllegalArgumentException e){rejected.incrementAndGet();publish.acknowledge();return;}
   catch(SecurityException e){stopUnknown();return;}
   catch(ResponseStatusException e){if(e.getStatusCode().value()==404){rejected.incrementAndGet();publish.acknowledge();return;}}
   catch(Exception e){/* Ambiguous commit and dependency errors retain original observation identity for safe retry. */}
   if(attempt<2)try{Thread.sleep((attempt+1)*1000L);}catch(InterruptedException e){Thread.currentThread().interrupt();stopUnknown();return;}
  }
  stopUnknown();
 }
 private void stopUnknown(){unknown.incrementAndGet();halted.set(true);client.disconnect();}
 public boolean halted(){return halted.get();}
 public void close(){halted.set(true);worker.shutdownNow();try{client.disconnect().get(5,TimeUnit.SECONDS);}catch(Exception ignored){}try{worker.awaitTermination(5,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}}
}
