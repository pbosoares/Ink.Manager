package ink.manager.api.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Protects the internal, non-customer-facing retention workflow with a separate secret. */
@Component
public class RetentionOperatorFilter extends OncePerRequestFilter {
    static final String HEADER = "X-Marcae-Retention-Operator";

    private final byte[] token;
    private final String operatorId;

    public RetentionOperatorFilter(
            @Value("${MARCAE_RETENTION_OPERATOR_TOKEN:}") String token,
            @Value("${MARCAE_RETENTION_OPERATOR_ID:}") String operatorId) {
        this.token = token == null ? new byte[0] : token.getBytes(StandardCharsets.UTF_8);
        this.operatorId = operatorId == null ? "" : operatorId.trim();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/internal/retencoes-financeiras/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Vary", "X-Marcae-Retention-Operator");
        String supplied = request.getHeader(HEADER);
        byte[] candidate = supplied == null ? new byte[0] : supplied.getBytes(StandardCharsets.UTF_8);
        boolean configured = token.length >= 32 && !operatorId.isBlank() && operatorId.length() <= 80;
        if (!configured) {
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            return;
        }
        if (!MessageDigest.isEqual(token, candidate)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        var authentication = new UsernamePasswordAuthenticationToken(
                operatorId, null, List.of(new SimpleGrantedAuthority("ROLE_RETENTION_OPERATOR")));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        try {
            chain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
