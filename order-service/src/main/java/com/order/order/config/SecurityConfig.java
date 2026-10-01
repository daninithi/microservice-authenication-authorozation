package com.order.order.config;

import java.io.IOException;
import java.time.LocalDateTime;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.order.order.security.GatewayUserFilter;

import jakarta.servlet.http.HttpServletResponse;

@Configuration
public class SecurityConfig {

    private final GatewayUserFilter gatewayUserFilter;

    public SecurityConfig(GatewayUserFilter gatewayUserFilter) {
        this.gatewayUserFilter = gatewayUserFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // No sessions and no login forms: the gateway already checked the JWT
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.disable())
                .httpBasic(httpBasic -> httpBasic.disable())
                .formLogin(formLogin -> formLogin.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // Authorization: who can access what (first matching rule wins)
                .authorizeHttpRequests(auth -> auth
                        // Spring's error page (e.g. a 400 for bad JSON) must stay reachable
                        .requestMatchers("/error").permitAll()
                        // Only admins see every order and move orders through their statuses
                        .requestMatchers(HttpMethod.GET, "/api/v1/orders").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/orders/*/status").hasRole("ADMIN")
                        // Any logged-in user: place an order, see own orders, cancel own order.
                        // "Is this order yours?" is checked in OrderService.
                        .requestMatchers("/api/v1/orders/**").hasAnyRole("REGULAR_USER", "ADMIN")
                        .anyRequest().denyAll())

                .exceptionHandling(exceptions -> exceptions
                        // 401: no user (normally the gateway already stops these)
                        .authenticationEntryPoint((request, response, ex) ->
                                writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized",
                                        "Authentication is required"))
                        // 403: logged in, but the role is not allowed
                        .accessDeniedHandler((request, response, ex) ->
                                writeError(response, HttpServletResponse.SC_FORBIDDEN, "Forbidden",
                                        "You do not have permission to access this resource")))

                .addFilterBefore(gatewayUserFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // Same JSON shape as ApiErrorResponse
    private void writeError(HttpServletResponse response, int status, String error, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write("{\"timestamp\":\"" + LocalDateTime.now()
                + "\",\"status\":" + status
                + ",\"error\":\"" + error
                + "\",\"messages\":[\"" + message + "\"]}");
    }
}
