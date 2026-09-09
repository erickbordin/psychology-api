package com.psychologyapi.psychologyapi.authsecurity;

import java.time.Duration;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfiguration {
    
    @Bean
    public PasswordEncoder passwordEncoder(){
        return new BCryptPasswordEncoder();
    }

    @Bean
    AuthenticationManager authenticationManager(CustomAuthenticationProvider customAuthenticationProvider){
        return new ProviderManager(List.of(customAuthenticationProvider));
    }

    /**
     * Origens vem de variavel de ambiente e nunca sao "*": o proprio spec de CORS
     * proibe curinga junto de credenciais, e o cookie de refresh depende delas.
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${app.cors.origins}") List<String> origens) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(origens);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setAllowCredentials(true);
        config.setMaxAge(Duration.ofHours(1));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtCustomtAuthenticationFilter jwtCustomtAuthenticationFilter,
            ErroRespostaAuthenticationEntryPoint erroRespostaAuthenticationEntryPoint,
            CorsConfigurationSource corsConfigurationSource) throws Exception{
        return http
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            .csrf(csfr -> csfr.disable())
            .sessionManagement(session -> 
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/auth/**").permitAll()
                    // contrato OpenAPI: o front gera os tipos a partir dele, e
                    // tipo gerado e o que impede front e API divergirem em
                    // silencio. Nao expoe dado — so caminhos e formatos.
                    // DESLIGAR EM PRODUCAO pela propriedade abaixo: num produto
                    // com prontuario, a superficie da API e informacao que
                    // ajuda quem ataca (portao da Fatia 5, spec 9)
                    .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                    .anyRequest().authenticated())
            .exceptionHandling(ex -> ex.authenticationEntryPoint(erroRespostaAuthenticationEntryPoint))
            .addFilterBefore(jwtCustomtAuthenticationFilter, org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class)
            .build();
    }
    

}
