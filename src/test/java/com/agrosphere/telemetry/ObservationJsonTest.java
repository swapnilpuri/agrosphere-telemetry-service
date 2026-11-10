package com.agrosphere.telemetry;
import com.agrosphere.telemetry.adapter.in.web.ObservationJson;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ObservationJsonTest {
 final ObservationJson parser=new ObservationJson();
 final String body="{\"schemaVersion\":1,\"observationId\":\"00000000-0000-4000-8000-000000000001\",\"deviceId\":\"00000000-0000-4000-8000-000000000004\",\"estateId\":\"00000000-0000-4000-8000-000000000005\",\"blockId\":\"00000000-0000-4000-8000-000000000006\",\"kind\":\"AIR_TEMPERATURE\",\"unit\":\"CELSIUS\",\"value\":\"27.5\",\"observedAt\":\"2026-01-01T12:00:00Z\",\"dataProvenance\":\"SIMULATED\"}";
 void denied(String value){assertThrows(IllegalArgumentException.class,()->parser.parse(value.getBytes(StandardCharsets.UTF_8)));}
 @Test void normalizesExactDecimalWithoutUnitConversion(){assertEquals("27.5",parser.parse(body.getBytes(StandardCharsets.UTF_8)).value().toPlainString());}
 @Test void duplicatePropertiesAndTrailingDocumentsRejected(){denied(body.replace("\"value\":\"27.5\"","\"value\":\"27.5\",\"value\":\"28\""));denied(body+" {}");}
 @Test void invalidUtf8AndOversizeRejected(){assertThrows(IllegalArgumentException.class,()->parser.parse(new byte[]{(byte)0xc3,0x28}));denied(" ".repeat(4097));}
 @Test void unrecognizedAndMissingFieldsRejected(){denied(body.replace("\"schemaVersion\":1,",""));denied(body.replace("\"schemaVersion\":1,","\"workerId\":\"secret\",\"schemaVersion\":1,"));}
 @Test void floatExponentAndUnboundedPrecisionRejected(){for(String v:new String[]{"27.5","\"1e2\"","\"0.0000001\"","\"1000000000000\"","\"NaN\""})denied(body.replace("\"27.5\"",v));}
 @Test void wrongUnitProvenanceAndFutureTimestampEncodingRejected(){denied(body.replace("CELSIUS","FAHRENHEIT"));denied(body.replace("SIMULATED","REAL"));for(String t:new String[]{"2026-02-30T12:00:00Z","2026-01-01T12:00:60Z","2026-01-01T12:00:00.0001Z","0000-01-01T12:00:00Z"})denied(body.replace("2026-01-01T12:00:00Z",t));}
 @Test void upperCaseShortAndMalformedUuidRejected(){denied(body.replace("000000000001","00000000000A"));denied(body.replace("00000000-0000-4000-8000-000000000001","1-0-4000-8000-1"));}
}
