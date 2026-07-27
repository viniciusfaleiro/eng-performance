package com.engperf.adapter.inbound.web.auth;

import com.engperf.application.port.inbound.AuthorizationUseCase;
import com.engperf.application.port.outbound.TokenService;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Registers the auth token filter ahead of the dispatcher for every request. */
@Configuration
public class WebAuthConfig {

  @Bean
  FilterRegistrationBean<AuthTokenFilter> authTokenFilter(
      TokenService tokens, AuthorizationUseCase authorization) {
    FilterRegistrationBean<AuthTokenFilter> registration = new FilterRegistrationBean<>();
    registration.setFilter(new AuthTokenFilter(tokens, authorization));
    registration.addUrlPatterns("/api/*");
    registration.setOrder(1);
    return registration;
  }
}
