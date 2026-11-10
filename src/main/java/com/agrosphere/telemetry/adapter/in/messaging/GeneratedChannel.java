package com.agrosphere.telemetry.adapter.in.messaging;
import com.agrosphere.telemetry.adapter.out.persistence.JdbcTelemetry;
import com.agrosphere.telemetry.domain.DeviceRegistration;
import com.agrosphere.telemetry.domain.ObservationPolicy.Window;
import com.agrosphere.telemetry.adapter.in.web.ObservationJson;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import tools.jackson.databind.*;

/** Explicit generated-fixture trust binding; never infers publisher identity from message data. */
public final class GeneratedChannel implements com.agrosphere.telemetry.application.port.out.TelemetryPorts.Channel {
 private final Path manifest,base;private final String digest;private final JdbcTelemetry store;
 private final JsonNode config;private final Map<String,DeviceRegistration> channels=new TreeMap<>();
 public GeneratedChannel(Path manifest,String digest,JdbcTelemetry store){
  try{
   this.manifest=manifest.toRealPath();this.base=this.manifest.getParent();this.digest=digest;this.store=store;
   checkFile(this.manifest,digest);config=new ObjectMapper().readTree(Files.readAllBytes(this.manifest));
   UUID run=ObservationJson.id(config.path("runId").asText());
   if(!Files.readString(owned(".generated-telemetry-owner")).strip().equals(run.toString()))throw new IllegalArgumentException();
   if(!Set.of("localhost","127.0.0.1","::1").contains(config.path("host").asText())||config.path("port").asInt()<1||config.path("port").asInt()>65535)throw new IllegalArgumentException();
   if(!config.path("devices").isArray()||config.path("devices").size()<1||config.path("devices").size()>100)throw new IllegalArgumentException();
   for(JsonNode node:config.path("devices")){
    var id=ObservationJson.id(node.path("deviceId").asText());var registration=store.find(id).orElseThrow();
    if(!registration.active()||!registration.estateId().toString().equals(node.path("estateId").asText())||!registration.blockId().toString().equals(node.path("blockId").asText())||!registration.publisherPrincipal().equals(node.path("publisherPrincipal").asText())||!store.run(id).equals(run))throw new IllegalArgumentException();
    if(channels.put(registration.topic(),registration)!=null)throw new IllegalArgumentException();
    if(!store.window(id).equals(new Window(ObservationJson.time(config.path("windowStart").asText()),ObservationJson.time(config.path("windowEnd").asText()))))throw new IllegalArgumentException();
   }
   verify();
  }catch(Exception e){throw new IllegalArgumentException("Unverified generated channel fixture");}
 }
 public Path owned(String file)throws Exception{Path result=base.resolve(file).toRealPath();if(!result.startsWith(base)||Files.size(result)>65536)throw new IllegalArgumentException();return result;}
 private static void checkFile(Path path,String digest)throws Exception{if(Files.size(path)>65536||!digest.matches("[0-9a-f]{64}"))throw new IllegalArgumentException();if(!HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))).equals(digest))throw new IllegalArgumentException();}
 public synchronized void verify(){try{
   checkFile(manifest,digest);
   checkFile(owned(config.path("aclFile").asText()),config.path("aclSha256").asText());
   checkFile(owned(config.path("brokerConfigFile").asText()),config.path("brokerConfigSha256").asText());
   StringBuilder acl=new StringBuilder();for(var d:channels.values())acl.append("user ").append(d.publisherPrincipal()).append("\ntopic write ").append(d.topic()).append("\n");
   String subscriber=config.path("subscriberUser").asText();if(!subscriber.matches("[A-Za-z0-9._-]{1,64}")||channels.values().stream().anyMatch(d->d.publisherPrincipal().equals(subscriber)))throw new IllegalArgumentException();
   acl.append("user ").append(subscriber).append('\n');for(String topic:channels.keySet())acl.append("topic read ").append(topic).append('\n');
   if(!Files.readString(owned(config.path("aclFile").asText())).equals(acl.toString()))throw new IllegalArgumentException();
   // The fixture verifies this exact immutable config is the running broker's config.
   String expected="listener 1883\nallow_anonymous false\npassword_file /tmp/passwords\nacl_file /tmp/acl\npersistence true\npersistence_location /mosquitto/data/\nlog_type error\nmax_inflight_messages 10\nmax_queued_messages 100\nmessage_size_limit 4096\nmax_packet_size 8192\nuse_username_as_clientid true\n";
   if(!Files.readString(owned(config.path("brokerConfigFile").asText())).equals(expected))throw new IllegalArgumentException();
   for(var d:channels.values())if(!store.find(d.deviceId()).orElseThrow().equals(d)||!store.run(d.deviceId()).toString().equals(config.path("runId").asText())||!store.window(d.deviceId()).equals(new Window(ObservationJson.time(config.path("windowStart").asText()),ObservationJson.time(config.path("windowEnd").asText()))))throw new IllegalArgumentException();
  }catch(Exception e){throw new SecurityException("Generated channel trust changed");}}
 public DeviceRegistration registration(String topic){verify();var d=channels.get(topic);if(d==null)throw new SecurityException("Topic denied");return d;}
 public Set<String> topics(){return Set.copyOf(channels.keySet());}
 public String text(String key){return config.path(key).asText();}
 public String estateBearer(){return secret("estateTokenFile");}
 public int port(){return config.path("port").asInt();}
 public String secret(String key){try{return Files.readString(owned(config.path(key).asText())).strip();}catch(Exception e){throw new IllegalArgumentException("Generated credential unavailable");}}
}
