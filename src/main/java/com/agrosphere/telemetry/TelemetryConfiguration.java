package com.agrosphere.telemetry;
import com.agrosphere.telemetry.adapter.out.persistence.JdbcTelemetry;
import com.agrosphere.telemetry.adapter.out.web.EstateClient;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
@Configuration
@ConditionalOnProperty(name="telemetry.demo.enabled",havingValue="true")
public class TelemetryConfiguration {
 @Bean Clock telemetryClock(){return Clock.systemUTC();}
 @Bean JdbcTelemetry telemetryStore(JdbcTemplate jdbc,PlatformTransactionManager manager){return new JdbcTelemetry(jdbc,new TransactionTemplate(manager));}
 @Bean EstateClient estateClient(@Value("${telemetry.estate-url:http://localhost:8081}")String url){return new EstateClient(url);}
}
