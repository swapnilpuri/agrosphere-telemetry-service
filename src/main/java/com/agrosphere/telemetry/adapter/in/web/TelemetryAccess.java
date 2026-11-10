package com.agrosphere.telemetry.adapter.in.web;
import java.util.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.access.AccessDeniedException;
public final class TelemetryAccess {
 public String read(UUID estate){
  var authentication=SecurityContextHolder.getContext().getAuthentication();
  if(!(authentication instanceof JwtAuthenticationToken jwt)||!jwt.isAuthenticated())throw new AccessDeniedException("JWT required");
  var roles=jwt.getAuthorities().stream().map(a->a.getAuthority()).toList();
  if(!roles.contains("ROLE_PLATFORM_ADMIN")&&!roles.contains("ROLE_PLANTATION_ADMIN")){
   if(Collections.disjoint(roles,Set.of("ROLE_ESTATE_ADMIN","ROLE_ESTATE_MANAGER","ROLE_VIEWER","ROLE_FIELD_SUPERVISOR","ROLE_FIELD_WORKER","ROLE_AUDITOR")))throw new AccessDeniedException("Role required");
   Object claim=jwt.getToken().getClaim("estate_ids");if(!(claim instanceof Collection<?> ids)||ids.size()>1000)throw new AccessDeniedException("Estate scope required");
   Set<UUID> allowed=new HashSet<>();
   try{for(Object id:ids){if(!(id instanceof String s))throw new IllegalArgumentException();allowed.add(ObservationJson.id(s));}}catch(IllegalArgumentException e){throw new AccessDeniedException("Invalid estate scope");}
   if(!allowed.contains(estate))throw new AccessDeniedException("Estate denied");
  }
  return jwt.getToken().getTokenValue();
 }
}
