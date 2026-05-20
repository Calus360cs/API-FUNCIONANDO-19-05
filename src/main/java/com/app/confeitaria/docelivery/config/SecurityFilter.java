package com.app.confeitaria.docelivery.config;

import com.app.confeitaria.docelivery.model.repository.UsuarioRepository;
import com.app.confeitaria.docelivery.service.TokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class SecurityFilter extends OncePerRequestFilter {

    private final TokenService tokenService;
    private final UsuarioRepository userRepository;

    public SecurityFilter(TokenService tokenService, @Lazy UsuarioRepository userRepository) {
        this.tokenService = tokenService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        var token = this.recoverToken(request);

        if (token != null) {
            var login = tokenService.validateToken(token);

            if (login != null && !login.isEmpty()) {
                // Buscamos o usuário no banco
                var userOptional = userRepository.findByEmail(login);

                if (userOptional.isPresent()) {
                    var user = userOptional.get();

                    // CORREÇÃO/CHECK: Verifique se o TipoUsuario ou as Authorities não estão nulas
                    // Se o authorities estiver vazio ou nulo, o Spring Security pode barrar o acesso
                    // Garanta que a lista de permissões tenha um tipo definido explicitamente
                    var authorities = (user.getTipoUsuario() != null)
                            ? user.getTipoUsuario().getGrantedAuthorities()
                            : java.util.Collections.<org.springframework.security.core.GrantedAuthority>emptyList();

                    // Agora o Spring saberá exatamente o que é 'authorities'
                    var authentication = new UsernamePasswordAuthenticationToken(user, null, authorities);

                    SecurityContextHolder.getContext().setAuthentication(authentication);

                    // Log de debug para você confirmar no console se o filtro rodou com sucesso
                    System.out.println("DEBUG: Filtro processado para: " + login);
                }
            }
        }
        // IMPORTANTE: Esta linha deve estar FORA dos blocos 'if' para a requisição seguir viagem
        filterChain.doFilter(request, response);
    }

    private String recoverToken(HttpServletRequest request) {
        var authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) return null;
        return authHeader.substring(7);
    }
}