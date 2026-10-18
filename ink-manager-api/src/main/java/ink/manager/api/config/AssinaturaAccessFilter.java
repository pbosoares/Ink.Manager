package ink.manager.api.config;
import java.io.IOException;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;
import ink.manager.api.service.AssinaturaService;
public class AssinaturaAccessFilter extends OncePerRequestFilter {
    private final AssinaturaService assinatura;
    public AssinaturaAccessFilter(AssinaturaService assinatura){this.assinatura=assinatura;}
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException{
        String path=request.getServletPath();boolean business=path.startsWith("/clientes")||path.startsWith("/marcacoes")||path.startsWith("/profissionais")||path.startsWith("/servicos")||path.startsWith("/recebimentos")||path.startsWith("/configuracao/agendamento");
        var auth=SecurityContextHolder.getContext().getAuthentication();
        if(business&&!"GET".equals(request.getMethod())&&auth instanceof JwtAuthenticationToken jwt){
            long id=((Number)jwt.getToken().getClaim("estudioId")).longValue();
            if(!assinatura.podeGravar(id)){response.setStatus(402);response.setContentType("application/json;charset=UTF-8");response.getWriter().write("{\"erro\":\"Assinatura vencida. O responsavel pode renovar em Meu estudio. Seus dados continuam disponiveis para consulta.\"}");return;}
        }
        chain.doFilter(request,response);
    }
}
