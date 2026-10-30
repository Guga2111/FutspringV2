package com.futspring.backend.shared.config;

import com.futspring.backend.domain.auth.AuthRateLimitFilter;
import com.futspring.backend.domain.auth.JwtAuthFilter;
import tools.jackson.databind.json.JsonMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Public routes authorized by a token in the request (the notification e-mail's unsubscribe link), not by the
     * session: JwtAuthFilter ignores the Authorization header on them and AuthRateLimitFilter limits them.
     */
    public static final Set<String> EMAIL_LINK_PATHS = Set.of(
            "/api/v1/notifications/unsubscribe", "/api/v1/notifications/resubscribe");

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Actuator endpoints. They are served only on management.server.port (9091), which Docker never publishes, so
     * Prometheus scrapes them without a token. Boot applies the security filter to the management port too.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain actuatorFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher(EndpointRequest.toAnyEndpoint())
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           JwtAuthFilter jwtAuthFilter,
                                           AuthRateLimitFilter authRateLimitFilter,
                                           CorsConfigurationSource corsConfigurationSource,
                                           JsonMapper objectMapper,
                                           @Value("${springdoc.api-docs.enabled:false}") boolean apiDocsEnabled)
            throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> {
                auth
                    .requestMatchers("/api/v1/auth/**").permitAll()
                    .requestMatchers("/api/v1/files/**").permitAll()
                    .requestMatchers("/ws/**").permitAll()
                    // liveness / readiness probes (management.endpoint.health.probes.add-additional-paths)
                    .requestMatchers(HttpMethod.GET, "/livez", "/readyz").permitAll()
                    .requestMatchers(HttpMethod.POST, EMAIL_LINK_PATHS.toArray(String[]::new)).permitAll();
                // OpenAPI docs and Swagger UI: public only where they are served (API_DOCS_ENABLED, dev)
                if (apiDocsEnabled) {
                    auth.requestMatchers(HttpMethod.GET, "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                        .permitAll();
                }
                auth.anyRequest().authenticated();
            })
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json");
                    Map<String, Object> body = new HashMap<>();
                    body.put("status", 401);
                    body.put("message", "Autenticação necessária");
                    body.put("timestamp", Instant.now().toString());
                    objectMapper.writeValue(response.getWriter(), body);
                })
            )
            .addFilterBefore(authRateLimitFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
