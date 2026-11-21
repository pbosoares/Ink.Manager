package ink.manager.api.config;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

@Configuration
@org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
public class SecurityConfig {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecretKey secretKey() {
        if (jwtSecret.isBlank() || jwtSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("JWT_SECRET deve conter pelo menos 32 bytes.");
        }
        return new SecretKeySpec(
            jwtSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8),
            "HmacSHA256"
        );
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey secretKey) {
        return NimbusJwtEncoder
            .withSecretKey(secretKey)
            .algorithm(MacAlgorithm.HS256)
            .build();
    }

    @Bean
    public JwtDecoder jwtDecoder(SecretKey secretKey) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
            .withSecretKey(secretKey)
            .macAlgorithm(MacAlgorithm.HS256)
            .build();
        decoder.setJwtValidator(org.springframework.security.oauth2.jwt.JwtValidators.createDefaultWithIssuer("ink-manager"));
        return decoder;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, SessionJwtConverter sessions,
            ink.manager.api.service.AssinaturaService assinatura, RetentionOperatorFilter retentionOperatorFilter,
            ManifestoRestauracaoGateFilter manifestoRestauracaoGateFilter,
            org.springframework.beans.factory.ListableBeanFactory beans,
            org.springframework.core.io.ResourceLoader resourceLoader,
            org.springframework.boot.autoconfigure.web.WebProperties webProperties)
            throws Exception {

        http
            .csrf(csrf -> csrf.disable())

            // O site e a API sao so em portugues; casa com <html lang="pt-BR">.
            .headers(headers -> headers.addHeaderWriter(
                new org.springframework.security.web.header.writers.StaticHeadersWriter("Content-Language", "pt-BR")))

            .sessionManagement(session ->
                session.sessionCreationPolicy(
                    SessionCreationPolicy.STATELESS
                )
            )

            .authorizeHttpRequests(auth -> auth
                // Despacho interno para /error (404, 405...): sem isso, URL inexistente virava 401.
                // O corpo do erro nao traz mensagem nem stack (server.error.include-*=never).
                .dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ERROR).permitAll()
                .requestMatchers("/", "/index.html", "/landing.js", "/landing.css", "/app", "/app/", "/app.html", "/app.js", "/overview.js", "/team.js", "/billing.js", "/simple.js", "/analytics.js", "/agendar.html", "/agendar.js", "/waitlist.js", "/acao-email.html", "/acao-email.js", "/privacidade.html", "/termos.html", "/agenda-para-barbearia", "/agenda-para-barbearia.html", "/agenda-para-manicure", "/agenda-para-manicure.html", "/oferta.html", "/oferta.js", "/styles.css", "/favicon.svg").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/landing/*.png").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/landing/*.svg").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/a/*", "/publico/*", "/publico/*/horarios").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.POST, "/publico/*/reservas", "/publico/*/lista-espera", "/publico/*/oferta/visualizar", "/publico/*/oferta/aceitar", "/publico/acoes/visualizar", "/publico/acoes").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.POST, "/auth/login", "/auth/estudios", "/auth/recuperar-senha", "/auth/redefinir-senha").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/auth/config", "/actuator/health").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/robots.txt", "/sitemap.xml").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.POST, "/assinatura/webhook", "/assinatura/stripe-webhook").permitAll()
                .requestMatchers("/internal/retencoes-financeiras/**").permitAll()
                // Exige autenticacao; so libera (para responder 404) quando nenhum handler atende a URL.
                .anyRequest().access(new RotaInexistenteAuthorizationManager(beans, resourceLoader, webProperties))
            )

            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(sessions))
                .authenticationEntryPoint(entradaBearerSemMetadata())
            );

        http.addFilterBefore(retentionOperatorFilter, BearerTokenAuthenticationFilter.class);
        http.addFilterAfter(manifestoRestauracaoGateFilter, RetentionOperatorFilter.class);
        http.addFilterAfter(new AssinaturaAccessFilter(assinatura), org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter.class);
        return semProtectedResourceMetadata(http.build());
    }

    /**
     * O resource server do Spring Security 7 publica sempre {@code /.well-known/oauth-protected-resource}
     * (RFC 9728), sem opcao de desligar. O Marcae nao tem API OAuth para terceiros e o documento saia com
     * {@code http://} e sem {@code authorization_servers}; removemos o filtro da cadeia e a rota cai no 404.
     */
    static org.springframework.security.web.DefaultSecurityFilterChain semProtectedResourceMetadata(
            org.springframework.security.web.DefaultSecurityFilterChain chain) {
        java.util.List<jakarta.servlet.Filter> filtros = chain.getFilters().stream()
            .filter(f -> !(f instanceof org.springframework.security.oauth2.server.resource.web.OAuth2ProtectedResourceMetadataFilter))
            .toList();
        return new org.springframework.security.web.DefaultSecurityFilterChain(chain.getRequestMatcher(), filtros);
    }

    /** 401 com {@code WWW-Authenticate: Bearer}, sem apontar {@code resource_metadata} para o endpoint removido. */
    static org.springframework.security.web.AuthenticationEntryPoint entradaBearerSemMetadata() {
        var bearer = new org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint();
        return (request, response, falha) -> bearer.commence(request,
            new jakarta.servlet.http.HttpServletResponseWrapper(response) {
                @Override public void setHeader(String nome, String valor) { super.setHeader(nome, semResourceMetadata(nome, valor)); }
                @Override public void addHeader(String nome, String valor) { super.addHeader(nome, semResourceMetadata(nome, valor)); }
            }, falha);
    }

    static String semResourceMetadata(String nome, String valor) {
        if (valor == null || !"WWW-Authenticate".equalsIgnoreCase(nome)) {
            return valor;
        }
        String limpo = valor.replaceAll("resource_metadata=\"[^\"]*\"", "")
            .replaceAll(",\\s*,", ",")
            .replaceAll("^Bearer[\\s,]*", "Bearer ")
            .replaceAll("[\\s,]+$", "");
        return limpo.isBlank() ? "Bearer" : limpo;
    }

    @Bean
    public FilterRegistrationBean<RetentionOperatorFilter> retentionOperatorFilterRegistration(
            RetentionOperatorFilter filter) {
        FilterRegistrationBean<RetentionOperatorFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<ManifestoRestauracaoGateFilter> manifestoRestauracaoGateFilterRegistration(
            ManifestoRestauracaoGateFilter filter) {
        FilterRegistrationBean<ManifestoRestauracaoGateFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
