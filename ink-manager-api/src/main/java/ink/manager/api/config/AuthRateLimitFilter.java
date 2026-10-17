package ink.manager.api.config;

import java.io.IOException;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Per-instance protection. Never trusts a caller-supplied forwarding header. */
@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {
    private record Window(long start, int count) {}
    private final Map<String, Window> windows = new LinkedHashMap<>();
    private final Clock clock;
    private final int limit;

    public AuthRateLimitFilter(Clock clock, @Value("${studio.auth-attempts-per-minute:20}") int limit) {
        this.clock = clock;
        this.limit = limit;
    }

    private synchronized boolean allow(String address) {
        long now = clock.millis();
        windows.entrySet().removeIf(entry -> now - entry.getValue().start() >= 60000);
        Window window = windows.get(address);
        if (window == null) {
            if (windows.size() >= 10000) return false;
            window = new Window(now, 0);
        }
        if (window.count() >= limit) return false;
        windows.put(address, new Window(window.start(), window.count() + 1));
        return true;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        boolean authentication = "POST".equals(request.getMethod())
                && request.getServletPath().startsWith("/auth/");
        if (authentication && !allow(request.getRemoteAddr())) {
            response.setStatus(429);
            response.setHeader("Retry-After", "60");
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"erro\":\"Muitas tentativas. Aguarde um minuto e tente novamente.\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
