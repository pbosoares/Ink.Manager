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
            ManifestoRestauracaoGateFilter manifestoRestauracaoGateFilter)
            throws Exception {

        http
            .csrf(csrf -> csrf.disable())

            .sessionManagement(session ->
                session.sessionCreationPolicy(
                    SessionCreationPolicy.STATELESS
                )
            )

            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/index.html", "/app.js", "/team.js", "/billing.js", "/simple.js", "/analytics.js", "/agendar.html", "/agendar.js", "/waitlist.js", "/acao-email.html", "/acao-email.js", "/oferta.html", "/oferta.js", "/styles.css", "/favicon.svg").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/publico/*", "/publico/*/horarios").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.POST, "/publico/*/reservas", "/publico/*/lista-espera", "/publico/*/oferta/visualizar", "/publico/*/oferta/aceitar", "/publico/acoes/visualizar", "/publico/acoes").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.POST, "/auth/login", "/auth/estudios", "/auth/recuperar-senha", "/auth/redefinir-senha").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/auth/config", "/actuator/health").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/robots.txt", "/sitemap.xml").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.POST, "/assinatura/webhook", "/assinatura/stripe-webhook").permitAll()
                .requestMatchers("/internal/retencoes-financeiras/**").permitAll()
                .anyRequest().authenticated()
            )

            .oauth2ResourceServer(oauth2 ->
                oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(sessions))
            );

        http.addFilterBefore(retentionOperatorFilter, BearerTokenAuthenticationFilter.class);
        http.addFilterAfter(manifestoRestauracaoGateFilter, RetentionOperatorFilter.class);
        http.addFilterAfter(new AssinaturaAccessFilter(assinatura), org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter.class);
        return http.build();
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
