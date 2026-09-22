package com.p.logistics.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.SessionManagementConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

// Step 06 security foundation: order-service is the externally-facing side
// of this system (unlike inventory-service, which InternalApiKeyFilter
// locks to internal callers only). Every request now has to prove it's
// someone, via HTTP Basic (authentication - application.properties'
// security.demo-users.* backs the two demo accounts below), and prove it's
// allowed to do that specific thing (authorization - the role checks in
// securityFilterChain).
//
// Two roles model the two actors who actually call this API day to day:
// CUSTOMER submits/reads their own orders and shipments; OPERATOR is the
// warehouse/ops side that manually creates a shipment or advances its
// status. Everything not explicitly restricted to OPERATOR just needs to
// be *someone* authenticated, matching the customer-facing routes.
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
                        // Manually creating a shipment or moving its status
                        // forward is an operational action, not something
                        // a customer does - OrderService already creates
                        // the shipment automatically as part of order
                        // submission, so these two routes are the
                        // operator's manual/override path.
                        .requestMatchers(HttpMethod.POST, "/shipments").hasRole("OPERATOR")
                        .requestMatchers(HttpMethod.PUT, "/shipments/*/status").hasRole("OPERATOR")
                        .anyRequest().authenticated()
                )
                .httpBasic(Customizer.withDefaults());

        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService(
            PasswordEncoder passwordEncoder,
            @Value("${security.demo-users.customer.username}") String customerUsername,
            @Value("${security.demo-users.customer.password}") String customerPassword,
            @Value("${security.demo-users.operator.username}") String operatorUsername,
            @Value("${security.demo-users.operator.password}") String operatorPassword
    ) {
        UserDetails customer = User.withUsername(customerUsername)
                .password(passwordEncoder.encode(customerPassword))
                .roles("CUSTOMER")
                .build();
        UserDetails operator = User.withUsername(operatorUsername)
                .password(passwordEncoder.encode(operatorPassword))
                .roles("OPERATOR")
                .build();

        return new InMemoryUserDetailsManager(customer, operator);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
