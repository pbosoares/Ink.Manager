package ink.manager.api.config;

import ink.manager.api.service.ManifestoRestauracaoGate;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Blocks application traffic while a GitHub-backed restore manifest is unavailable or unreconciled. */
@Component
public class ManifestoRestauracaoGateFilter extends OncePerRequestFilter {
    private static final String RECONCILIAR = "/internal/retencoes-financeiras/manifesto/reconciliar";
    private static final String INICIALIZAR = "/internal/retencoes-financeiras/manifesto/inicializar";
    private final ManifestoRestauracaoGate gate;

    public ManifestoRestauracaoGateFilter(ManifestoRestauracaoGate gate) {
        this.gate = gate;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!gate.bloqueado() || healthProbe(request)
                || ("POST".equals(request.getMethod())
                && (RECONCILIAR.equals(request.getServletPath()) || INICIALIZAR.equals(request.getServletPath())))) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Retry-After", "60");
    }

    private static boolean healthProbe(HttpServletRequest request) {
        return "GET".equals(request.getMethod()) && "/actuator/health".equals(request.getServletPath());
    }
}
