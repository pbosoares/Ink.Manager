package ink.manager.api.config;

import java.io.IOException;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * {@code GET /index.html} vira 301 para {@code /}, que e a URL canonica da landing.
 *
 * <p>So atua no pedido original do navegador ({@link DispatcherType#REQUEST}). O {@code GET /} continua
 * servindo o {@code index.html} por forward interno, que nao passa por aqui. O Location e relativo
 * de proposito: o container nao conhece o esquema publico (o TLS termina no Caddy), e um Location
 * absoluto sairia com {@code http://}.
 */
@Component
public class IndexHtmlRedirectFilter extends OncePerRequestFilter {

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String metodo = request.getMethod();
        return request.getDispatcherType() != DispatcherType.REQUEST
            || !("GET".equals(metodo) || "HEAD".equals(metodo))
            || !"/index.html".equals(request.getRequestURI().substring(request.getContextPath().length()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String query = request.getQueryString();
        response.setStatus(HttpServletResponse.SC_MOVED_PERMANENTLY);
        response.setHeader("Location", request.getContextPath() + "/" + (query == null || query.isEmpty() ? "" : "?" + query));
        response.setContentLength(0);
    }
}
