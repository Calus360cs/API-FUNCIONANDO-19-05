package com.app.confeitaria.docelivery.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final SecurityFilter securityFilter;
    private final CorsConfigurationSource corsConfigurationSource;

    public SecurityConfig(SecurityFilter securityFilter, CorsConfigurationSource corsConfigurationSource) {
        this.securityFilter = securityFilter;
        this.corsConfigurationSource = corsConfigurationSource;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // REMOVIDO: O webSecurityCustomizer ignorando /uploads/** pode conflitar com o FilterChain.
    // É mais seguro permitir o acesso dentro do filterChain abaixo.

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Permite explicitamente as rotas de OPTIONS para evitar erro de CORS no pre-flight
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // 🚨 ADICIONE ESTA LINHA AQUI PARA LIBERAR O WEBSOCKET
                        .requestMatchers("/ws-docelivery/**").permitAll()

                        // CORREÇÃO: Registre as rotas de autenticação de forma mais explícita
                        // para garantir que o Spring não tente validar token nelas.
                        .requestMatchers("/api/auth/login").permitAll()
                        .requestMatchers("/api/auth/register").permitAll()
                        .requestMatchers("/api/auth/confeiteiro/login").permitAll() // Rota específica do erro 500
                        .requestMatchers("/api/auth/cliente/login").permitAll()
                        .requestMatchers("/api/auth/**").permitAll()

                        .requestMatchers("/uploads/**").permitAll()

                        // 1. ROTAS PÚBLICAS (GET)
                        .requestMatchers(HttpMethod.GET, "/api/stores/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/produtos/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/categories/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/combos/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/confeiteiro/public/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/confeiteiro/profile").permitAll() // 🟢 COLE ESSA LINHA EXATAMENTE AQUI!

                        // 2. GERENCIAMENTO DE PRODUTOS
                        .requestMatchers(HttpMethod.POST, "/api/produtos/**").hasAnyRole("CONFEITEIRO", "MASTER")
                        .requestMatchers(HttpMethod.PUT, "/api/produtos/**").hasAnyRole("CONFEITEIRO", "MASTER")
                        .requestMatchers(HttpMethod.DELETE, "/api/produtos/**").hasAnyRole("CONFEITEIRO", "MASTER")

                        // 3. PERFIL DO CONFEITEIRO E PEDIDOS (Ajuste de ordem)
                        .requestMatchers(HttpMethod.PUT, "/api/confeiteiro/atualizar/**").authenticated()
                        .requestMatchers("/api/pedidos/confeiteiro/**", "/api/pedidos/confeiteiros/**").hasAnyRole("CONFEITEIRO", "MASTER")
                        .requestMatchers(HttpMethod.GET, "/api/confeite/iro/**").authenticated()

                        .requestMatchers(HttpMethod.POST, "/api/pedidos/**").hasAnyRole("CLIENTE", "MASTER")
                        .requestMatchers(HttpMethod.PATCH, "/api/pedidos/**").hasAnyRole("CONFEITEIRO", "MASTER")
                        .requestMatchers(HttpMethod.GET, "/api/pedidos/**").authenticated()

                        // 4. ÁREAS ESPECÍFICAS
                        .requestMatchers("/api/cliente/**", "/api/clientes/**").hasAnyRole("CLIENTE", "MASTER")
                        .requestMatchers("/api/entregador/**").hasAnyRole("ENTREGADOR", "MASTER")
                        .requestMatchers("/api/admin/**").hasAnyRole("MASTER", "SUPORTE")

                        // 5. BLOQUEIO PADRÃO
                        .anyRequest().authenticated()
                )
                .addFilterBefore(securityFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }
}