package com.umc.pediupatrao.config;

import com.umc.pediupatrao.security.ContextoUsuario;
import com.umc.pediupatrao.service.UsuarioDetailsService;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.DelegatingAuthenticationEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * Configuração de segurança.
 *
 * Defesa em duas camadas, ambas no backend:
 * 1) regras por URL aqui (deny by default: toda rota exige autenticação);
 * 2) {@code @PreAuthorize} nos controladores e serviços (regras finas, por operação).
 * Esconder botões nas telas é só conveniência: o que vale é o que está aqui.
 *
 * CSRF está HABILITADO. Formulários Thymeleaf recebem o token automaticamente e os
 * scripts (fetch) o enviam no cabeçalho lido das meta tags do layout.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    private final UsuarioDetailsService usuarioDetailsService;

    public SecurityConfig(UsuarioDetailsService usuarioDetailsService) {
        this.usuarioDetailsService = usuarioDetailsService;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(usuarioDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // CSRF habilitado (padrão do Spring Security): POST/PUT/DELETE exigem token válido.
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/error", "/css/**", "/dist/**", "/plugins/**", "/js/**", "/images/**").permitAll()
                        // Usuários e perfis: somente ADMIN (inclui o antigo /api/usuarios, que era público)
                        .requestMatchers("/usuarios/**", "/api/usuarios/**", "/admin/**", "/admin").hasRole("ADMIN")
                        // Auditoria: somente consulta, para ADMIN e GERENTE
                        .requestMatchers("/auditoria/**", "/api/auditoria/**").hasAnyRole("ADMIN", "GERENTE")
                        // Clientes: ADMIN não acessa; as permissões por operação ficam nos @PreAuthorize
                        .requestMatchers(HttpMethod.DELETE, "/api/clientes/**").hasRole("GERENTE")
                        .requestMatchers("/clientes/**", "/api/clientes/**").hasAnyRole("GERENTE", "ATENDENTE")
                        // Pedidos e produtos: os três perfis consultam; as escritas são restritas por @PreAuthorize
                        .requestMatchers("/pedidos/**", "/api/pedidos/**", "/produtos/**", "/api/produtos/**")
                                .hasAnyRole("ADMIN", "GERENTE", "ATENDENTE")
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        // Sem login: APIs respondem 401; telas redirecionam para /login
                        .authenticationEntryPoint(pontoDeEntrada())
                        .accessDeniedHandler(accessDeniedHandler())
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .permitAll()
                        .successHandler(authenticationSuccessHandler())
                )
                .logout(logout -> logout
                        .logoutUrl("/logout") // POST com token CSRF
                        .logoutSuccessUrl("/login?logout")
                        .permitAll()
                );

        return http.build();
    }

    /** Decide o que acontece com quem não está logado: 401 nas APIs, redirecionamento ao login nas telas. */
    private AuthenticationEntryPoint pontoDeEntrada() {
        LinkedHashMap<RequestMatcher, AuthenticationEntryPoint> pontos = new LinkedHashMap<>();
        pontos.put(SecurityConfig::ehRequisicaoDeApi, new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED));
        DelegatingAuthenticationEntryPoint entrada = new DelegatingAuthenticationEntryPoint(pontos);
        entrada.setDefaultEntryPoint(new LoginUrlAuthenticationEntryPoint("/login"));
        return entrada;
    }

    @Bean
    public AuthenticationSuccessHandler authenticationSuccessHandler() {
        return (request, response, authentication) -> {
            response.sendRedirect("/"); // Redireciona para a página inicial após login bem-sucedido
        };
    }

    /** Registra a tentativa de acesso indevido e responde 403 (JSON nas APIs, página de erro nas telas). */
    @Bean
    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, ex) -> {
            String motivo = ex instanceof CsrfException ? "token CSRF ausente/inválido" : "perfil sem permissão";
            log.warn("ACESSO NEGADO usuario={} perfil={} metodo={} uri={} motivo={}",
                    request.getUserPrincipal() == null ? "anonimo" : ContextoUsuario.username(),
                    ContextoUsuario.perfil(),
                    request.getMethod(), request.getRequestURI(), motivo);
            if (ehRequisicaoDeApi(request)) {
                response.setStatus(HttpStatus.FORBIDDEN.value());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                response.getWriter().write("{\"status\":403,\"erro\":\"Forbidden\","
                        + "\"mensagem\":\"Acesso negado: seu perfil não tem permissão para esta operação.\"}");
            } else {
                response.sendError(HttpStatus.FORBIDDEN.value());
            }
        };
    }

    private static boolean ehRequisicaoDeApi(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/api/");
    }
}
