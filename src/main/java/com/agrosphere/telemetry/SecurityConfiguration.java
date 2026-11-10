package com.agrosphere.telemetry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
@Configuration
class SecurityConfiguration {
    private static void unauthenticated(jakarta.servlet.http.HttpServletRequest request,
            jakarta.servlet.http.HttpServletResponse response,
            org.springframework.security.core.AuthenticationException error) throws java.io.IOException {
        response.setStatus(401);response.setContentType("application/problem+json");
        response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"Unauthenticated\",\"status\":401,\"detail\":\"Authentication required\"}");
    }
    @Bean SecurityFilterChain security(HttpSecurity http) throws Exception {
        return http.authorizeHttpRequests(auth -> auth
            .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
            .anyRequest().authenticated())
            .exceptionHandling(errors -> errors.authenticationEntryPoint((request,response,error)->{
                response.setStatus(401);response.setContentType("application/problem+json");
                response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"Unauthenticated\",\"status\":401,\"detail\":\"Authentication required\"}");
            }).accessDeniedHandler((request,response,error)->{
                response.setStatus(403);response.setContentType("application/problem+json");
                response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"Forbidden\",\"status\":403,\"detail\":\"Access denied\"}");
            }))
            .oauth2ResourceServer(oauth -> oauth.authenticationEntryPoint(SecurityConfiguration::unauthenticated).jwt(jwt -> jwt.jwtAuthenticationConverter(token -> {
                java.util.List<org.springframework.security.core.GrantedAuthority> roles=new java.util.ArrayList<>();
                Object realm=token.getClaim("realm_access");
                if(realm instanceof java.util.Map<?,?> map && map.get("roles") instanceof java.util.Collection<?> values)
                    for(Object role:values)if(role instanceof String text && java.util.Set.of("PLATFORM_ADMIN","PLANTATION_ADMIN","ESTATE_ADMIN","ESTATE_MANAGER","VIEWER","FIELD_SUPERVISOR","FIELD_WORKER","AUDITOR").contains(text))roles.add(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_"+text));
                return new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken(token,roles);
            }))).build();
    }
}
