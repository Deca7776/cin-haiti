package ht.oni.cin.config;

import ht.oni.cin.infrastructure.persistence.repository.CinApiClientRepository;
import ht.oni.cin.security.DualAuthFilter;
import ht.oni.cin.security.JwtValidator;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public DualAuthFilter dualAuthFilter(CinApiClientRepository apiClientRepository, JwtValidator jwtValidator) {
        return new DualAuthFilter(apiClientRepository, jwtValidator);
    }

    // Sans ceci, Spring Boot enregistre automatiquement tout @Bean de type Filter comme filtre
    // servlet global EN PLUS de son usage explicite via addFilterBefore() ci-dessous : le filtre
    // s'execute donc deux fois par requete. Pour les requetes multipart (upload d'image), cette
    // double execution fait perdre le SecurityContext avant d'atteindre le controleur, provoquant
    // un 401 alors que l'authentification avait pourtant reussi. On desactive l'enregistrement
    // global pour ne garder que l'usage dans la chaine Spring Security.
    @Bean
    public FilterRegistrationBean<DualAuthFilter> dualAuthFilterRegistration(DualAuthFilter dualAuthFilter) {
        FilterRegistrationBean<DualAuthFilter> registration = new FilterRegistrationBean<>(dualAuthFilter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, DualAuthFilter dualAuthFilter) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(c -> c.configurationSource(corsConfigurationSource()))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/swagger-ui.html", "/swagger-ui/**", "/api-docs/**", "/v3/api-docs/**",
                                "/api/v1/dev/**", "/h2-console/**", "/", "/index.html", "/assets/**", "/error").permitAll()
                        .requestMatchers("/api/v1/scan/**", "/api/v1/identites/**", "/api/v1/admin/**").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(dualAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:5173", "http://localhost:3000", "http://localhost:8080", "http://localhost:8081"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
