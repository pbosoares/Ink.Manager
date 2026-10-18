package ink.manager.api.config;
import java.io.IOException;
import java.time.Clock;
import java.util.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.filter.OncePerRequestFilter;

/** Bounded per-instance limiter. Does not trust caller-supplied forwarding headers. */
@Component
public class PublicRateLimitFilter extends OncePerRequestFilter {
    private record Window(long inicio,int quantidade){}
    private final Map<String,Window> windows=new HashMap<>();
    private final Clock clock;
    private final int reservasPorMinuto;
    private final int consultasPorMinuto;
    public PublicRateLimitFilter(Clock clock,
        @Value("${studio.public-bookings-per-minute:10}") int reservasPorMinuto,
        @Value("${studio.public-queries-per-minute:120}") int consultasPorMinuto){
        this.clock=clock;this.reservasPorMinuto=reservasPorMinuto;this.consultasPorMinuto=consultasPorMinuto;
        if(reservasPorMinuto<1||consultasPorMinuto<1)throw new IllegalArgumentException("Limites publicos devem ser positivos.");
    }
    private synchronized boolean permitir(String chave,int limite){
        long agora=clock.millis();windows.entrySet().removeIf(e->agora-e.getValue().inicio()>=60000);
        var w=windows.get(chave);if(w==null){if(windows.size()>=10000)return false;w=new Window(agora,0);}
        if(w.quantidade()>=limite)return false;
        windows.put(chave,new Window(w.inicio(),w.quantidade()+1));return true;
    }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException{
        boolean publico=request.getServletPath().startsWith("/publico/");
        boolean escrita="POST".equals(request.getMethod());
        if(publico)response.setHeader("Cache-Control","no-store");
        if(publico&&!permitir(request.getRemoteAddr()+":"+(escrita?"reserva":"consulta"),escrita?reservasPorMinuto:consultasPorMinuto)){
            response.setStatus(429);response.setHeader("Retry-After","60");response.setContentType("application/json;charset=UTF-8");response.getWriter().write("{\"erro\":\"Muitas tentativas. Aguarde um minuto.\"}");return;
        }
        chain.doFilter(request,response);
    }
}
