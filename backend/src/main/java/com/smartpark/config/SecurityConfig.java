package com.smartpark.config;

import com.smartpark.security.JwtAuthenticationFilter;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${smartpark.cors.allowed-origins}") String origins) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(
            Arrays.stream(origins.split(",")).map(String::trim).toList()
        );
        configuration.setAllowedMethods(
            List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
        );
        configuration.setAllowedHeaders(
            List.of("Authorization", "Content-Type", "Accept")
        );
        configuration.setExposedHeaders(List.of("Authorization"));
        configuration.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter) throws Exception {
        return http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> { })
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authorize -> authorize
                .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                .requestMatchers(
                    "/", "/index.html", "/assets/**", "/favicon.ico",
                    "/login", "/register", "/forgot-password", "/admin/login",
                    "/app/**", "/admin/**", "/owner/**"
                ).permitAll()
                .requestMatchers(
                    "/api/payments/webhook",
                    "/api/auth/**",
                    "/api/settings/vehicle-types",
                    "/api/users/register",
                    "/api/users/login",
                    "/api/users/forgot-password",
                    "/api/users/reset-password"
                ).permitAll()
                .requestMatchers(HttpMethod.GET, "/api/parking-slots/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/parking-slots/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/parking-slots/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/parking-slots/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/bookings/availability").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/bookings/estimate").permitAll()
                .anyRequest().authenticated())
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable())
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint((request, response, exception) -> {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json");
                    response.getWriter().write(
                        "{\"message\":\"Authentication is required\"}"
                    );
                })
                .accessDeniedHandler((request, response, exception) -> {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json");
                    response.getWriter().write(
                        "{\"message\":\"You are not allowed to perform this action\"}"
                    );
                }))
            .addFilterBefore(
                jwtAuthenticationFilter,
                UsernamePasswordAuthenticationFilter.class
            )
            .build();
    }
}
