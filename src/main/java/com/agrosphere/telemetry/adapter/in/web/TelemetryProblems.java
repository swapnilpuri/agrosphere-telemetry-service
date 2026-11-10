package com.agrosphere.telemetry.adapter.in.web;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.access.AccessDeniedException;
@RestControllerAdvice(assignableTypes=TelemetryController.class)
public class TelemetryProblems {
 @ExceptionHandler({IllegalArgumentException.class,ResponseStatusException.class,AccessDeniedException.class,org.springframework.dao.DataAccessException.class})
 public ResponseEntity<Map<String,Object>> problem(Exception e){
  int status=e instanceof ResponseStatusException r?r.getStatusCode().value():e instanceof AccessDeniedException?403:e instanceof IllegalArgumentException?400:503;
  return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(Map.of("type","about:blank","title","Telemetry request failed","status",status,"detail","Request could not be completed"));
 }
}
