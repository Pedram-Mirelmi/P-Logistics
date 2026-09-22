package com.p.logistics.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.SessionManagementConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

// Step 06 security foundation, item 2 (authentication): order-service is the
// externally-facing side of this system (unlike inventory-service, which
// InternalApiKeyFilter locks to internal callers only). Every request now
// has to prove it's someone, via HTTP Basic - see application.properties'
// spring.security.user.* for the one demo user this checks against.
//
// No authorization split yet (that's the next item) - this only answers
// "who is this", not "what are they allowed to do".
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Plain REST API, no browser session/form login - CSRF
                // protection exists to stop a browser being tricked into
                // submitting a cookie-authenticated form, which doesn't
                // apply here, and each request re-authenticates via the
                // Basic header rather than a server-side session.
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement((SessionManagementConfigurer<HttpSecurity> session) ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Infrastructure signal, not a business operation -
                        // the Compose healthcheck calls this with no
                        // credentials of any kind.
                        .requestMatchers("/actuator/health").permitAll()
                        .anyRequest().authenticated()
                )
                .httpBasic(Customizer.withDefaults());

        return http.build();
    }
}
