package com.agrosphere.telemetry;

import com.agrosphere.telemetry.adapter.in.messaging.*;
import com.agrosphere.telemetry.adapter.in.web.ObservationJson;
import com.agrosphere.telemetry.adapter.out.persistence.JdbcTelemetry;
import com.agrosphere.telemetry.adapter.out.web.EstateClient;
import com.agrosphere.telemetry.adapter.out.messaging.TelemetryOutbox;
import com.agrosphere.telemetry.application.TelemetryIngestion;
import com.agrosphere.telemetry.application.port.out.TelemetryPorts.SaveOutcome;
import com.agrosphere.telemetry.domain.*;
import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.*;
import com.sun.net.httpserver.HttpServer;
import com.github.dockerjava.api.model.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.images.builder.Transferable;
import tools.jackson.databind.*;
import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TelemetryAdaptersIT {
 static final String run=UUID.randomUUID().toString(),runtime="rt"+run.replace("-","");
 static final String runtimePass=UUID.randomUUID().toString(),pubUser="p"+run.replace("-",""),subUser="s"+run.replace("-","");
 static final String pubPass=UUID.randomUUID().toString(),subPass=UUID.randomUUID().toString();
 static final UUID estate=UUID.randomUUID(),block=UUID.randomUUID(),device=UUID.randomUUID();
 static final DeviceRegistration registration=new DeviceRegistration(device,estate,block,pubUser,true);
 static PostgreSQLContainer pg;static GenericContainer<?> mqtt,rabbit;static Path directory;
 static DriverManagerDataSource ownerDS,runtimeDS;static JdbcTemplate owner,jdbc;static JdbcTelemetry store;static GeneratedChannel channel;static EstateClient estateClient;
 static TelemetryIngestion ingestion;static MqttTelemetry transport;static ConfigurableApplicationContext app;
 static CachingConnectionFactory amqp;static RabbitTemplate template;static RabbitAdmin admin;
 static HttpServer identity;static RSAKey key;static String issuer,base,fixtureToken;
 static volatile int referenceStatus=200;static AtomicInteger referenceCalls=new AtomicInteger();
 static final ObjectMapper json=new ObjectMapper();static final HttpClient http=HttpClient.newHttpClient();
 static final List<String> ownedContainerIds=new ArrayList<>();
 static String hash(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
 static DriverManagerDataSource data(String user,String pass){return new DriverManagerDataSource(pg.getJdbcUrl(),user,pass);}
 static void bind(GenericContainer<?> c,int port){c.withLabel("agrosphere.tlm.run",run).withCreateContainerCmdModifier(cmd->cmd.getHostConfig().withPortBindings(new PortBinding(Ports.Binding.bindIpAndPort("127.0.0.1",0),ExposedPort.tcp(port))));}
 @BeforeAll static void start()throws Exception{
  pg=new PostgreSQLContainer("postgres:17-alpine").withDatabaseName("telemetrydb");bind(pg,5432);pg.start();ownedContainerIds.add(pg.getContainerId());
  ownerDS=data(pg.getUsername(),pg.getPassword());owner=new JdbcTemplate(ownerDS);
  Flyway.configure().dataSource(data(pg.getUsername(),pg.getPassword())).target("1").load().migrate();
  owner.update("INSERT INTO outbox_event(event_id,aggregate_id,event_type,event_version,correlation_id,occurred_at,payload,published_at) VALUES(?,?, 'FixtureLegacy',1,?,now(),'{}',now())",UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID());
  String sql=Files.readString(Path.of("src/main/resources/db/migration/V2__simulated_telemetry.sql"));
  var ownerTx=new TransactionTemplate(new DataSourceTransactionManager(ownerDS));
  assertThrows(IllegalStateException.class,()->ownerTx.execute(s->{owner.execute(sql);throw new IllegalStateException("Fixture rollback");}));
  assertNull(owner.queryForObject("SELECT to_regclass('telemetry_device')",String.class));
  Flyway.configure().dataSource(data(pg.getUsername(),pg.getPassword())).load().migrate();
  assertEquals(1,owner.queryForObject("SELECT count(*) FROM outbox_event",Integer.class));
  // Generated login DDL is never printed and failures omit SQL/credentials.
  try(var connection=data(pg.getUsername(),pg.getPassword()).getConnection();var s=connection.createStatement()){
   s.execute("CREATE ROLE "+runtime+" LOGIN PASSWORD '"+runtimePass+"'");
   s.execute("REVOKE CREATE ON SCHEMA public FROM PUBLIC");
   s.execute("REVOKE CONNECT ON DATABASE postgres FROM PUBLIC");s.execute("REVOKE CONNECT ON DATABASE template1 FROM PUBLIC");
   s.execute("GRANT CONNECT ON DATABASE telemetrydb TO "+runtime);s.execute("GRANT USAGE ON SCHEMA public TO "+runtime);
   s.execute("GRANT SELECT ON telemetry_device,telemetry_observation,outbox_event TO "+runtime);
   s.execute("GRANT INSERT ON telemetry_observation,outbox_event TO "+runtime);
   s.execute("GRANT UPDATE(attempts,published_at,next_attempt_at) ON outbox_event TO "+runtime);
  }catch(SQLException e){throw new IllegalStateException("Generated principal provisioning failed");}
  runtimeDS=data(runtime,runtimePass);jdbc=new JdbcTemplate(runtimeDS);store=new JdbcTelemetry(jdbc,new TransactionTemplate(new DataSourceTransactionManager(runtimeDS)));
  Instant now=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
  owner.update("INSERT INTO telemetry_device VALUES(?,?,?,?,true,?,?,?)",device,estate,block,pubUser,UUID.fromString(run),Timestamp.from(now.minusSeconds(3600)),Timestamp.from(now.plusSeconds(3600)));
  key=new RSAKeyGenerator(2048).keyID("fixture").generate();identity=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  issuer="http://127.0.0.1:"+identity.getAddress().getPort()+"/realms/fixture";
  identity.createContext("/certs",ex->{byte[] bytes=new JWKSet(key.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);ex.getResponseHeaders().set("Content-Type","application/json");ex.sendResponseHeaders(200,bytes.length);ex.getResponseBody().write(bytes);ex.close();});
  identity.createContext("/api/",ex->{referenceCalls.incrementAndGet();int status=referenceStatus;String body="{}";
   if(ex.getRequestHeaders().getFirst("Authorization")==null)status=401;
   if(status==200){boolean isEstate=ex.getRequestURI().getPath().startsWith("/api/v1/estates/");body=isEstate?"{\"id\":\""+estate+"\",\"status\":\"ACTIVE\"}":"{\"id\":\""+block+"\",\"estateId\":\""+estate+"\",\"status\":\"ACTIVE\",\"cropType\":\"RUBBER\"}";}
   byte[] bytes=body.getBytes(StandardCharsets.UTF_8);ex.getResponseHeaders().set("Content-Type","application/json");ex.sendResponseHeaders(status,bytes.length);ex.getResponseBody().write(bytes);ex.close();});identity.start();
  fixtureToken=token("agrosphere-api",issuer,estate,key,"ESTATE_MANAGER");
  directory=Files.createTempDirectory("agrosphere-tlm-"+run);
  String config="listener 1883\nallow_anonymous false\npassword_file /tmp/passwords\nacl_file /tmp/acl\npersistence true\npersistence_location /mosquitto/data/\nlog_type error\nmax_inflight_messages 10\nmax_queued_messages 100\nmessage_size_limit 4096\nmax_packet_size 8192\nuse_username_as_clientid true\n";
  String acl="user "+pubUser+"\ntopic write "+registration.topic()+"\nuser "+subUser+"\ntopic read "+registration.topic()+"\n";
  Files.writeString(directory.resolve("broker.conf"),config);Files.writeString(directory.resolve("acl"),acl);Files.writeString(directory.resolve("subscriber.password"),subPass);Files.writeString(directory.resolve("estate.token"),fixtureToken);Files.writeString(directory.resolve(".generated-telemetry-owner"),run);
  mqtt=new GenericContainer<>("eclipse-mosquitto:2.0.22").withExposedPorts(1883)
   .withCopyToContainer(Transferable.of(config),"/tmp/config").withCopyToContainer(Transferable.of(acl),"/tmp/acl")
   .withCopyToContainer(Transferable.of(pubUser+":"+pubPass+"\n"+subUser+":"+subPass+"\n"),"/tmp/passwords")
   .withCommand("sh","-c","(test -f /tmp/passwords-hashed || (mosquitto_passwd -U /tmp/passwords && touch /tmp/passwords-hashed)) && chmod 644 /tmp/passwords && exec mosquitto -c /tmp/config");bind(mqtt,1883);mqtt.start();ownedContainerIds.add(mqtt.getContainerId());
  Map<String,Object> manifest=new LinkedHashMap<>();manifest.put("runId",run);manifest.put("host","127.0.0.1");manifest.put("port",mqtt.getMappedPort(1883));manifest.put("clientId",subUser);manifest.put("subscriberUser",subUser);manifest.put("passwordFile","subscriber.password");manifest.put("estateTokenFile","estate.token");manifest.put("aclFile","acl");manifest.put("aclSha256",hash(acl.getBytes(StandardCharsets.UTF_8)));manifest.put("brokerConfigFile","broker.conf");manifest.put("brokerConfigSha256",hash(config.getBytes(StandardCharsets.UTF_8)));manifest.put("windowStart",now.minusSeconds(3600).toString());manifest.put("windowEnd",now.plusSeconds(3600).toString());manifest.put("devices",List.of(Map.of("deviceId",device.toString(),"estateId",estate.toString(),"blockId",block.toString(),"publisherPrincipal",pubUser)));
  Path manifestFile=directory.resolve("manifest.json");Files.writeString(manifestFile,json.writeValueAsString(manifest));
  channel=new GeneratedChannel(manifestFile,hash(Files.readAllBytes(manifestFile)),store);estateClient=new EstateClient("http://127.0.0.1:"+identity.getAddress().getPort());ingestion=new TelemetryIngestion(store,estateClient,channel,Clock.systemUTC(),new ObservationJson());
  rabbit=new GenericContainer<>("rabbitmq:4.3.6-management").withExposedPorts(5672).withEnv("RABBITMQ_DEFAULT_USER","u"+run.replace("-","")).withEnv("RABBITMQ_DEFAULT_PASS",UUID.randomUUID().toString());bind(rabbit,5672);rabbit.start();ownedContainerIds.add(rabbit.getContainerId());
  amqp=new CachingConnectionFactory("127.0.0.1",rabbit.getMappedPort(5672));amqp.setUsername(rabbit.getEnvMap().get("RABBITMQ_DEFAULT_USER"));amqp.setPassword(rabbit.getEnvMap().get("RABBITMQ_DEFAULT_PASS"));amqp.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);amqp.setPublisherReturns(true);
  template=new RabbitTemplate(amqp);template.setMandatory(true);admin=new RabbitAdmin(amqp);
  admin.declareExchange(new TopicExchange("agrosphere.domain",true,false));
  admin.declareExchange(new FanoutExchange("tlm.dead",true,false));admin.declareQueue(new org.springframework.amqp.core.Queue("tlm-dead",true));admin.declareBinding(new Binding("tlm-dead",Binding.DestinationType.QUEUE,"tlm.dead","",null));
  admin.declareQueue(new org.springframework.amqp.core.Queue("tlm-sink",true,false,false,Map.of("x-dead-letter-exchange","tlm.dead","x-max-length",10000)));admin.declareBinding(new Binding("tlm-sink",Binding.DestinationType.QUEUE,"agrosphere.domain","agrosphere.TelemetryObservationRecorded.v1",null));
  app=new SpringApplication(Application.class).run("--server.address=127.0.0.1","--server.port=0","--spring.security.oauth2.resourceserver.jwt.issuer-uri="+issuer,"--OIDC_JWK_SET_URI=http://127.0.0.1:"+identity.getAddress().getPort()+"/certs","--telemetry.demo.enabled=true","--telemetry.estate-url=http://127.0.0.1:"+identity.getAddress().getPort(),"--spring.datasource.url="+pg.getJdbcUrl(),"--spring.datasource.username="+runtime,"--spring.datasource.password="+runtimePass,"--spring.flyway.enabled=false","--spring.rabbitmq.host=127.0.0.1","--spring.rabbitmq.port="+rabbit.getMappedPort(5672),"--spring.rabbitmq.username="+amqp.getUsername(),"--spring.rabbitmq.password="+rabbit.getEnvMap().get("RABBITMQ_DEFAULT_PASS"),"--management.tracing.enabled=false");
  base="http://127.0.0.1:"+((WebServerApplicationContext)app).getWebServer().getPort();
 }
 static String token(String audience,String tokenIssuer,UUID scope,RSAKey signer,String role)throws Exception{
  var claims=new JWTClaimsSet.Builder().issuer(tokenIssuer).subject("generated"+run).audience(audience).issueTime(new java.util.Date()).expirationTime(java.util.Date.from(Instant.now().plusSeconds(900))).claim("realm_access",Map.of("roles",List.of(role))).claim("estate_ids",List.of(scope.toString())).build();
  var jwt=new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("fixture").build(),claims);jwt.sign(new RSASSASigner(signer));return jwt.serialize();
 }
 static byte[] payload(UUID id,String value,Instant at){return json.writeValueAsBytes(Map.ofEntries(Map.entry("schemaVersion",1),Map.entry("observationId",id),Map.entry("deviceId",device),Map.entry("estateId",estate),Map.entry("blockId",block),Map.entry("kind","AIR_TEMPERATURE"),Map.entry("unit","CELSIUS"),Map.entry("value",value),Map.entry("observedAt",at.toString()),Map.entry("dataProvenance","SIMULATED")));}
 static HttpResponse<String> get(String bearer)throws Exception{
  var builder=HttpRequest.newBuilder(URI.create(base+"/api/v1/telemetry/estates/"+estate+"/blocks/"+block+"/latest"));if(bearer!=null)builder.header("Authorization","Bearer "+bearer);return http.send(builder.GET().build(),HttpResponse.BodyHandlers.ofString());
 }
 static Mqtt5AsyncClient publisher(String user,String password)throws Exception{
  var client=MqttClient.builder().useMqttVersion5().identifier("fixture"+UUID.randomUUID()).serverHost("127.0.0.1").serverPort(channel.port()).buildAsync();
  try{client.connectWith().simpleAuth().username(user).password(password.getBytes(StandardCharsets.UTF_8)).applySimpleAuth().send().get(10,TimeUnit.SECONDS);return client;}catch(Exception e){client.disconnect();throw e;}
 }
 static void await(java.util.function.BooleanSupplier condition)throws Exception{long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);while(!condition.getAsBoolean()&&System.nanoTime()<deadline)Thread.sleep(50);assertTrue(condition.getAsBoolean(),"Generated condition not reached");}
 @Test @Order(1) void effectiveLoginDeniesDdlRegistryMutationObservationMutationAndOtherDatabase()throws Exception{
  assertThrows(Exception.class,()->jdbc.execute("CREATE TABLE forbidden(id int)"));assertThrows(Exception.class,()->jdbc.update("UPDATE telemetry_device SET active=false"));assertThrows(Exception.class,()->jdbc.update("DELETE FROM telemetry_observation"));assertThrows(Exception.class,()->jdbc.update("UPDATE telemetry_observation SET value=0"));
  assertThrows(Exception.class,()->DriverManager.getConnection(pg.getJdbcUrl().replace("/telemetrydb","/postgres"),runtime,runtimePass));
 }
 @Test @Order(2) void strictSignedJwtDenialsPrecedeReferenceAndQueries()throws Exception{
  int calls=referenceCalls.get();assertEquals(401,get(null).statusCode());
  assertEquals(401,get(token("wrong",issuer,estate,key,"ESTATE_MANAGER")).statusCode());assertEquals(401,get(token("agrosphere-api",issuer+"wrong",estate,key,"ESTATE_MANAGER")).statusCode());assertEquals(401,get(token("agrosphere-api",issuer,estate,new RSAKeyGenerator(2048).keyID("fixture").generate(),"ESTATE_MANAGER")).statusCode());
  assertEquals(403,get(token("agrosphere-api",issuer,UUID.randomUUID(),key,"ESTATE_MANAGER")).statusCode());assertEquals(403,get(token("agrosphere-api",issuer,estate,key,"UNKNOWN")).statusCode());assertEquals(calls,referenceCalls.get());
  var response=get(fixtureToken);assertEquals(200,response.statusCode());assertEquals("NONE",json.readTree(response.body()).path("coverage").asText());
 }
 @Test @Order(3) void atomicDuplicatesConflictsAndObservationTimeLatest()throws Exception{
  Instant at=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);UUID id=UUID.randomUUID();byte[] body=payload(id,"27.500000",at);
  assertEquals(SaveOutcome.INSERTED,ingestion.receive(registration.topic(),body,false));var original=store.latest(estate,block).getFirst().receivedAt();
  assertEquals(SaveOutcome.DUPLICATE,ingestion.receive(registration.topic(),payload(id,"27.5",at),false));assertEquals(original,store.latest(estate,block).getFirst().receivedAt());
  assertEquals(SaveOutcome.CONFLICT,ingestion.receive(registration.topic(),payload(id,"28",at),false));
  ingestion.receive(registration.topic(),payload(UUID.randomUUID(),"26",at.minusSeconds(60)),false);assertEquals("27.5",store.latest(estate,block).getFirst().observation().value().toPlainString());
  assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM outbox_event WHERE aggregate_id=?",Integer.class,id));
  referenceStatus=503;try{assertEquals(503,get(fixtureToken).statusCode());assertThrows(Exception.class,()->ingestion.receive(registration.topic(),payload(UUID.randomUUID(),"29",at),false));}finally{referenceStatus=200;}
 }
 @Test @Order(4) void concurrencyAndRollbackNeverSplitObservationAndOutbox()throws Exception{
  var pool=Executors.newFixedThreadPool(2);try{
   UUID id=UUID.randomUUID();byte[] body=payload(id,"30",Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS));
   var futures=pool.invokeAll(List.of(()->ingestion.receive(registration.topic(),body,false),()->ingestion.receive(registration.topic(),body,false)));
   var values=List.of(futures.get(0).get(),futures.get(1).get());assertTrue(values.contains(SaveOutcome.INSERTED));assertTrue(values.contains(SaveOutcome.DUPLICATE));
   assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM outbox_event WHERE aggregate_id=?",Integer.class,id));
  }finally{pool.shutdownNow();}
  UUID id=UUID.randomUUID();var parsed=new ObservationJson().parse(payload(id,"31",Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS)));
  UUID existingEvent=jdbc.queryForObject("SELECT event_id FROM outbox_event LIMIT 1",UUID.class);
  assertThrows(Exception.class,()->store.acceptAtomically(new ObservationPolicy.Recorded(parsed,Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS)),existingEvent,UUID.randomUUID()));
  assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM telemetry_observation WHERE observation_id=?",Integer.class,id));
 }
 @Test @Order(5) void authenticatedMqttCommitsBeforeAckRejectsRetainedAndOtherTopic()throws Exception{
  assertThrows(Exception.class,()->publisher(pubUser,UUID.randomUUID().toString()));
  transport=new MqttTelemetry(channel,ingestion);transport.start();var pub=publisher(pubUser,pubPass);
  try{
   UUID id=UUID.randomUUID();byte[] body=payload(id,"32",Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS));
   assertThrows(ExecutionException.class,()->pub.publishWith().topic(registration.topic()+"/wrong").qos(MqttQos.AT_LEAST_ONCE).payload(body).send().get(10,TimeUnit.SECONDS));
   pub.publishWith().topic(registration.topic()).qos(MqttQos.AT_LEAST_ONCE).retain(true).payload(body).send().get(10,TimeUnit.SECONDS);await(()->transport.rejected.get()>=1);assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM telemetry_observation WHERE observation_id=?",Integer.class,id));
   pub.publishWith().topic(registration.topic()).qos(MqttQos.AT_LEAST_ONCE).payload(body).send().get(10,TimeUnit.SECONDS);await(()->transport.accepted.get()>=1);assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM telemetry_observation WHERE observation_id=?",Integer.class,id));
   pub.publishWith().topic(registration.topic()).qos(MqttQos.AT_LEAST_ONCE).payload(body).send().get(10,TimeUnit.SECONDS);await(()->transport.duplicate.get()>=1);
   assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM outbox_event WHERE aggregate_id=?",Integer.class,id));
  }finally{pub.disconnect().get(10,TimeUnit.SECONDS);transport.close();}
 }
 @Test @Order(6) void confirmsAndReturnsRetainOutboxIdentityForRecovery()throws Exception{
  var outbox=new TelemetryOutbox(jdbc,new TransactionTemplate(new DataSourceTransactionManager(runtimeDS)),template);outbox.dispatch();assertNotNull(template.receive("tlm-sink",5000));
  admin.removeBinding(new Binding("tlm-sink",Binding.DestinationType.QUEUE,"agrosphere.domain","agrosphere.TelemetryObservationRecorded.v1",null));
  UUID id=UUID.randomUUID();ingestion.receive(registration.topic(),payload(id,"33",Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS)),false);outbox.dispatch();assertTrue(outbox.failed.get()>0);
  assertNull(jdbc.queryForObject("SELECT published_at FROM outbox_event WHERE aggregate_id=?",Timestamp.class,id));
  admin.declareBinding(new Binding("tlm-sink",Binding.DestinationType.QUEUE,"agrosphere.domain","agrosphere.TelemetryObservationRecorded.v1",null));owner.update("UPDATE outbox_event SET next_attempt_at=now() WHERE aggregate_id=?",id);outbox.dispatch();assertNotNull(jdbc.queryForObject("SELECT published_at FROM outbox_event WHERE aggregate_id=?",Timestamp.class,id));
 }
 @Test @Order(7) void changedManifestRegistryOrAclFailsClosedWithoutPayloadTrust()throws Exception{
  String original=Files.readString(directory.resolve("acl"));try{Files.writeString(directory.resolve("acl"),original+"topic write #\n");assertThrows(SecurityException.class,channel::verify);}finally{Files.writeString(directory.resolve("acl"),original);}
  owner.update("UPDATE telemetry_device SET active=false WHERE device_id=?",device);try{assertThrows(SecurityException.class,channel::verify);}finally{owner.update("UPDATE telemetry_device SET active=true WHERE device_id=?",device);}channel.verify();
 }
 @Test @Order(8) void exhaustedDependencyFailureLeavesUnackedWorkForRestart()throws Exception{
  transport=new MqttTelemetry(channel,ingestion);transport.start();var pub=publisher(pubUser,pubPass);
  UUID id=UUID.randomUUID();byte[] body=payload(id,"34",Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS));
  try{
   referenceStatus=503;pub.publishWith().topic(registration.topic()).qos(MqttQos.AT_LEAST_ONCE).payload(body).send().get(10,TimeUnit.SECONDS);
   await(()->transport.halted());assertTrue(transport.unknown.get()>0);assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM telemetry_observation WHERE observation_id=?",Integer.class,id));
   transport.close();referenceStatus=200;transport=new MqttTelemetry(channel,ingestion);transport.start();
   await(()->transport.accepted.get()>0);assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM outbox_event WHERE aggregate_id=?",Integer.class,id));
  }finally{referenceStatus=200;pub.disconnect().get(10,TimeUnit.SECONDS);transport.close();}
 }
 @Test @Order(9) void crashAfterCommitBeforeAckRedeliversAsDuplicate()throws Exception{
  var crash=MqttClient.builder().useMqttVersion5().identifier(subUser).serverHost("127.0.0.1").serverPort(mqtt.getMappedPort(1883)).buildAsync();
  UUID id=UUID.randomUUID();byte[] body=payload(id,"35",Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS));
  var committed=new CompletableFuture<Boolean>();
  crash.publishes(com.hivemq.client.mqtt.MqttGlobalPublishFilter.ALL,p->{
   if(p.isRetain()){p.acknowledge();return;}
   try{ingestion.receive(p.getTopic().toString(),p.getPayloadAsBytes(),false);committed.complete(true);}catch(Exception e){committed.completeExceptionally(e);}
   // Intentionally no ACK: model a process exit after commit.
  },true);
  crash.connectWith().cleanStart(false).sessionExpiryInterval(3600).simpleAuth().username(subUser).password(subPass.getBytes(StandardCharsets.UTF_8)).applySimpleAuth().send().get(10,TimeUnit.SECONDS);
  var pub=publisher(pubUser,pubPass);
  try{
   pub.publishWith().topic(registration.topic()).qos(MqttQos.AT_LEAST_ONCE).payload(body).send().get(10,TimeUnit.SECONDS);assertTrue(committed.get(10,TimeUnit.SECONDS));crash.disconnect().get(10,TimeUnit.SECONDS);
   transport=new MqttTelemetry(channel,ingestion);transport.start();await(()->transport.duplicate.get()>0);
   assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM outbox_event WHERE aggregate_id=?",Integer.class,id));
  }finally{crash.disconnect();pub.disconnect().get(10,TimeUnit.SECONDS);if(transport!=null)transport.close();}
 }
 @Test @Order(10) void otherPrincipalCannotHijackClientIdAndBrokerRestartIsVisible()throws Exception{
  transport=new MqttTelemetry(channel,ingestion);transport.start();
  var collision=MqttClient.builder().useMqttVersion5().identifier(subUser).serverHost("127.0.0.1").serverPort(mqtt.getMappedPort(1883)).buildAsync();
  try{
   collision.connectWith().simpleAuth().username(pubUser).password(pubPass.getBytes(StandardCharsets.UTF_8)).applySimpleAuth().send().get(10,TimeUnit.SECONDS);
   Thread.sleep(150);assertFalse(transport.halted());
   var leaked=new CountDownLatch(1);collision.subscribeWith().topicFilter("#").callback(p->leaked.countDown()).send().get(10,TimeUnit.SECONDS);
   UUID id=UUID.randomUUID();collision.publishWith().topic(registration.topic()).qos(MqttQos.AT_LEAST_ONCE).payload(payload(id,"36",Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS))).send().get(10,TimeUnit.SECONDS);
   await(()->jdbc.queryForObject("SELECT count(*) FROM telemetry_observation WHERE observation_id=?",Integer.class,id)==1);
   assertFalse(leaked.await(300,TimeUnit.MILLISECONDS),"Publisher wildcard request must not grant read delivery");
  }finally{collision.disconnect().get(10,TimeUnit.SECONDS);}
  org.testcontainers.DockerClientFactory.instance().client().restartContainerCmd(mqtt.getContainerId()).exec();await(()->transport.halted());assertTrue(transport.unknown.get()>0);transport.close();
  int recoveredPort=Integer.parseInt(org.testcontainers.DockerClientFactory.instance().client().inspectContainerCmd(mqtt.getContainerId()).exec().getNetworkSettings().getPorts().getBindings().get(ExposedPort.tcp(1883))[0].getHostPortSpec());
  if(recoveredPort!=channel.port()){
   // Explicit generated-fixture recovery, while ingestion is halted, never a silent live trust update.
   Path manifest=directory.resolve("manifest.json");Map<String,Object> recovered=json.readValue(Files.readString(manifest),Map.class);recovered.put("port",recoveredPort);Files.writeString(manifest,json.writeValueAsString(recovered));
   channel=new GeneratedChannel(manifest,hash(Files.readAllBytes(manifest)),store);ingestion=new TelemetryIngestion(store,estateClient,channel,Clock.systemUTC(),new ObservationJson());
  }
  await(()->{try(var socket=new java.net.Socket()){socket.connect(new InetSocketAddress("127.0.0.1",recoveredPort),200);return true;}catch(Exception e){return false;}});
  transport=new MqttTelemetry(channel,ingestion);transport.start();transport.close();
 }
 @Test @Order(11) void confirmedPublishThenDatabaseRollbackRedeliversSameEvent()throws Exception{
  var outbox=new TelemetryOutbox(jdbc,new TransactionTemplate(new DataSourceTransactionManager(runtimeDS)),template);
  outbox.dispatch();while(template.receive("tlm-sink",100)!=null){}
  UUID id=UUID.randomUUID();ingestion.receive(registration.topic(),payload(id,"37",Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS)),false);
  var outer=new TransactionTemplate(new DataSourceTransactionManager(runtimeDS));
  assertThrows(IllegalStateException.class,()->outer.execute(s->{outbox.dispatch();throw new IllegalStateException("Post-confirm fixture crash");}));
  assertNull(jdbc.queryForObject("SELECT published_at FROM outbox_event WHERE aggregate_id=?",Timestamp.class,id));
  var first=template.receive("tlm-sink",5000);assertNotNull(first);
  outbox.dispatch();var second=template.receive("tlm-sink",5000);assertNotNull(second);
  assertEquals(json.readTree(first.getBody()).path("eventId").asText(),json.readTree(second.getBody()).path("eventId").asText());
  assertArrayEquals(first.getBody(),second.getBody());
 }
 @Test @Order(12) void actualNackDeadLettersAndScopedReplayRetainsPayload()throws Exception{
  UUID id=UUID.randomUUID();ingestion.receive(registration.topic(),payload(id,"38",Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS)),false);
  new TelemetryOutbox(jdbc,new TransactionTemplate(new DataSourceTransactionManager(runtimeDS)),template).dispatch();
  byte[] rejected=template.execute(c->{var message=c.basicGet("tlm-sink",false);assertNotNull(message);c.basicNack(message.getEnvelope().getDeliveryTag(),false,false);return message.getBody();});
  var dead=template.receive("tlm-dead",5000);assertNotNull(dead);assertArrayEquals(rejected,dead.getBody());
  // This owned fixture operator replays to the same durable binding; no production replay API.
  template.send("agrosphere.domain","agrosphere.TelemetryObservationRecorded.v1",dead);
  var replay=template.receive("tlm-sink",5000);assertNotNull(replay);assertArrayEquals(rejected,replay.getBody());
 }
 @Test @Order(20) void exportActualHttpAndEventShapesForIndependentPinnedValidation()throws Exception{
  List<Map<String,Object>> responses=new ArrayList<>();
  var good=get(fixtureToken);assertEquals(200,good.statusCode());responses.add(Map.of("schema","LatestReadings","value",json.readTree(good.body())));
  for(var response:List.of(get(null),get(token("wrong",issuer,estate,key,"ESTATE_MANAGER")),get(token("agrosphere-api",issuer,UUID.randomUUID(),key,"ESTATE_MANAGER")))){
   assertTrue(response.statusCode()==401||response.statusCode()==403);responses.add(Map.of("schema","Problem","value",json.readTree(response.body())));
  }
  for(int status:new int[]{404,503}){referenceStatus=status;try{var response=get(fixtureToken);assertEquals(status,response.statusCode());responses.add(Map.of("schema","Problem","value",json.readTree(response.body())));}finally{referenceStatus=200;}}
  var expiredClaims=new JWTClaimsSet.Builder(SignedJWT.parse(fixtureToken).getJWTClaimsSet()).expirationTime(java.util.Date.from(Instant.now().minusSeconds(180))).build();
  var expired=new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("fixture").build(),expiredClaims);expired.sign(new RSASSASigner(key));assertEquals(401,get(expired.serialize()).statusCode());
  Path exports=Path.of(System.getProperty("telemetry.test.evidence.directory","target/tlm01-wire"));Files.createDirectories(exports);Files.writeString(exports.resolve("responses.json"),json.writeValueAsString(responses));
  var events=owner.queryForList("SELECT payload::text FROM outbox_event WHERE event_type='TelemetryObservationRecorded'",String.class).stream().map(json::readTree).toList();
  Files.writeString(exports.resolve("events.json"),json.writeValueAsString(events));
 }
 @Test @Order(14) void actualPublisherNackAndBlockedConfirmTimeoutRecover()throws Exception{
  var outbox=new TelemetryOutbox(jdbc,new TransactionTemplate(new DataSourceTransactionManager(runtimeDS)),template);
  admin.declareQueue(new org.springframework.amqp.core.Queue("tlm-overflow",true,false,false,Map.of("x-max-length",1,"x-overflow","reject-publish")));
  template.convertAndSend("","tlm-overflow","fixture-fill");
  admin.declareBinding(new Binding("tlm-overflow",Binding.DestinationType.QUEUE,"agrosphere.domain","agrosphere.TelemetryObservationRecorded.v1",null));
  UUID rejected=UUID.randomUUID();ingestion.receive(registration.topic(),payload(rejected,"39",Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS)),false);outbox.dispatch();
  assertNull(jdbc.queryForObject("SELECT published_at FROM outbox_event WHERE aggregate_id=?",Timestamp.class,rejected));assertTrue(outbox.failed.get()>0);
  admin.deleteQueue("tlm-overflow");owner.update("UPDATE outbox_event SET next_attempt_at=now() WHERE aggregate_id=?",rejected);outbox.dispatch();
  assertNotNull(jdbc.queryForObject("SELECT published_at FROM outbox_event WHERE aggregate_id=?",Timestamp.class,rejected));
  UUID blocked=UUID.randomUUID();ingestion.receive(registration.topic(),payload(blocked,"40",Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS)),false);
  assertEquals(0,rabbit.execInContainer("rabbitmqctl","set_vm_memory_high_watermark","absolute","1").getExitCode());
  try{
   outbox.dispatch();assertNull(jdbc.queryForObject("SELECT published_at FROM outbox_event WHERE aggregate_id=?",Timestamp.class,blocked));
  }finally{assertEquals(0,rabbit.execInContainer("rabbitmqctl","set_vm_memory_high_watermark","0.4").getExitCode());}
  Thread.sleep(200);owner.update("UPDATE outbox_event SET next_attempt_at=now() WHERE aggregate_id=?",blocked);outbox.dispatch();
  assertNotNull(jdbc.queryForObject("SELECT published_at FROM outbox_event WHERE aggregate_id=?",Timestamp.class,blocked));
 }
 @Test @Order(15) void anonymousAndSubscriberWritesDeniedAndSamePrincipalCollisionVisible()throws Exception{
  var anonymous=MqttClient.builder().useMqttVersion5().identifier("anonymous"+UUID.randomUUID()).serverHost("127.0.0.1").serverPort(channel.port()).buildAsync();
  try{assertThrows(ExecutionException.class,()->anonymous.connect().get(10,TimeUnit.SECONDS));}finally{anonymous.disconnect();}
  transport=new MqttTelemetry(channel,ingestion);transport.start();
  var samePrincipal=MqttClient.builder().useMqttVersion5().identifier("collision"+UUID.randomUUID()).serverHost("127.0.0.1").serverPort(channel.port()).buildAsync();
  try{
   samePrincipal.connectWith().simpleAuth().username(subUser).password(subPass.getBytes(StandardCharsets.UTF_8)).applySimpleAuth().send().get(10,TimeUnit.SECONDS);
   await(()->transport.halted());assertTrue(transport.unknown.get()>0);
   byte[] body=payload(UUID.randomUUID(),"41",Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS));
   assertThrows(ExecutionException.class,()->samePrincipal.publishWith().topic(registration.topic()).qos(MqttQos.AT_LEAST_ONCE).payload(body).send().get(10,TimeUnit.SECONDS));
  }finally{samePrincipal.disconnect().get(10,TimeUnit.SECONDS);transport.close();}
 }
 @Test @Order(16) void weatherAndSoilMqttRoundTripPreservesAllKindsAndRainfallInterval()throws Exception{
  Instant at=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
  Map<String,Object> soil=json.readValue(payload(UUID.randomUUID(),"45.125",at),Map.class);
  soil.put("kind","SOIL_MOISTURE");soil.put("unit","PERCENT");
  Map<String,Object> rain=json.readValue(payload(UUID.randomUUID(),"2.125",at),Map.class);
  rain.put("kind","INTERVAL_RAINFALL");rain.put("unit","MILLIMETRE");rain.put("intervalStart",at.minusSeconds(60).toString());rain.put("intervalEnd",at.toString());
  transport=new MqttTelemetry(channel,ingestion);transport.start();var pub=publisher(pubUser,pubPass);
  try{
   for(var body:List.of(soil,rain))pub.publishWith().topic(registration.topic()).qos(MqttQos.AT_LEAST_ONCE).payload(json.writeValueAsBytes(body)).send().get(10,TimeUnit.SECONDS);
   await(()->transport.accepted.get()==2);
   var response=get(fixtureToken);assertEquals(200,response.statusCode());var view=json.readTree(response.body());
   assertEquals("COMPLETE",view.path("coverage").asText());assertEquals(3,view.path("readings").size());
   for(var reading:view.path("readings")){
    assertEquals("SIMULATED",reading.path("dataProvenance").asText());assertFalse(reading.path("receivedAt").asText().isEmpty());
    if(reading.path("kind").asText().equals("SOIL_MOISTURE")){assertEquals("45.125",reading.path("value").asText());assertEquals("PERCENT",reading.path("unit").asText());}
    if(reading.path("kind").asText().equals("INTERVAL_RAINFALL")){assertEquals("2.125",reading.path("value").asText());assertEquals(at.minusSeconds(60).toString(),reading.path("intervalStart").asText());assertEquals(at.toString(),reading.path("intervalEnd").asText());}
   }
  }finally{pub.disconnect().get(10,TimeUnit.SECONDS);transport.close();}
 }
 @AfterAll static void stop()throws Exception{
  if(transport!=null)transport.close();if(app!=null)app.close();if(amqp!=null)amqp.destroy();if(identity!=null)identity.stop(0);
  if(rabbit!=null)rabbit.close();if(mqtt!=null)mqtt.close();if(pg!=null)pg.close();
  // Explicit per-file generated credential cleanup, confined to our resolved temp root.
  if(directory!=null){Path resolved=directory.toRealPath();assertTrue(resolved.getFileName().toString().startsWith("agrosphere-tlm-"+run));try(var files=Files.list(resolved)){for(Path p:files.toList()){assertTrue(p.toRealPath().startsWith(resolved));Files.delete(p);}}Files.delete(resolved);assertFalse(Files.exists(resolved));}
  Path ownedEvidence=System.getProperty("telemetry.test.evidence.directory")==null?Path.of("target/tlm01-owned-containers.json"):Path.of(System.getProperty("telemetry.test.evidence.directory")).resolve("adapter-owned.json");Files.createDirectories(ownedEvidence.getParent());Files.writeString(ownedEvidence,json.writeValueAsString(Map.of("run",run,"containers",ownedContainerIds,"credentialDirectoryRemoved",directory==null||!Files.exists(directory))));
 }
}
