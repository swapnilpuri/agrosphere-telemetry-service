package com.agrosphere.telemetry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;
@Configuration
public class JwtValidationConfiguration {
 @Bean JwtDecoder jwtDecoder(@Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")String issuer,
  @Value("${telemetry.security.audience}")String audience,@Value("${OIDC_JWK_SET_URI:}")String jwks){
  if(audience.isBlank())throw new IllegalArgumentException("Audience required");
  var decoder=(jwks.isBlank()?NimbusJwtDecoder.withIssuerLocation(issuer):NimbusJwtDecoder.withJwkSetUri(jwks)).build();
  decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer),jwt->jwt.getAudience().contains(audience)&&jwt.getSubject()!=null&&!jwt.getSubject().isBlank()?OAuth2TokenValidatorResult.success():OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token","Audience denied",null))));return decoder;
 }
}
