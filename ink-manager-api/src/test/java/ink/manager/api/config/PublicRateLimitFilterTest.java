package ink.manager.api.config;

import java.time.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PublicRateLimitFilterTest {
    private final Clock fixed=Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"),ZoneOffset.UTC);

    private MockHttpServletResponse request(PublicRateLimitFilter filter,String method,String path,String ip,String forwarded) throws Exception {
        var request=new MockHttpServletRequest(method,path);
        request.setServletPath(path);request.setRemoteAddr(ip);
        if(forwarded!=null)request.addHeader("X-Forwarded-For",forwarded);
        var response=new MockHttpServletResponse();
        filter.doFilter(request,response,new MockFilterChain());
        return response;
    }
    @Test void limitaReservasComRetryAfterEConsultasPossuemCotaPropria() throws Exception {
        var filter=new PublicRateLimitFilter(fixed,2,3);
        for(int i=0;i<2;i++)assertEquals(200,request(filter,"POST","/publico/local/reservas","127.0.0.1",null).getStatus());
        var blocked=request(filter,"POST","/publico/local/reservas","127.0.0.1",null);
        assertEquals(429,blocked.getStatus());assertEquals("60",blocked.getHeader("Retry-After"));
        assertTrue(blocked.getContentAsString().contains("erro"));
        for(int i=0;i<3;i++){
            var response=request(filter,"GET","/publico/local/horarios","127.0.0.1",null);
            assertEquals(200,response.getStatus());assertEquals("no-store",response.getHeader("Cache-Control"));
        }
        assertEquals(429,request(filter,"GET","/publico/local","127.0.0.1",null).getStatus());
    }
    @Test void cabecalhoForjadoNaoReiniciaCotaMasOutroIpTemCotaIndependente() throws Exception {
        var filter=new PublicRateLimitFilter(fixed,1,1);
        assertEquals(200,request(filter,"POST","/publico/a/reservas","127.0.0.1","192.0.2.1").getStatus());
        assertEquals(429,request(filter,"POST","/publico/b/reservas","127.0.0.1","192.0.2.2").getStatus());
        assertEquals(200,request(filter,"POST","/publico/a/reservas","127.0.0.2",null).getStatus());
    }
    @Test void janelaReabreExatamenteAposSessentaSegundos() throws Exception {
        var clock=mock(Clock.class);
        when(clock.millis()).thenReturn(fixed.millis());
        var filter=new PublicRateLimitFilter(clock,1,1);
        assertEquals(200,request(filter,"POST","/publico/local/reservas","127.0.0.1",null).getStatus());
        when(clock.millis()).thenReturn(Clock.offset(fixed,Duration.ofMillis(59999)).millis());
        assertEquals(429,request(filter,"POST","/publico/local/reservas","127.0.0.1",null).getStatus());
        when(clock.millis()).thenReturn(Clock.offset(fixed,Duration.ofSeconds(60)).millis());
        assertEquals(200,request(filter,"POST","/publico/local/reservas","127.0.0.1",null).getStatus());
    }
    @Test void rotasInternasNaoConsomemCotaPublica() throws Exception {
        var filter=new PublicRateLimitFilter(fixed,1,1);
        for(int i=0;i<3;i++)assertEquals(200,request(filter,"POST","/marcacoes","127.0.0.1",null).getStatus());
        assertEquals(200,request(filter,"POST","/publico/local/reservas","127.0.0.1",null).getStatus());
    }
    @Test void rejeitaLimitesNaoPositivos() {
        assertThrows(IllegalArgumentException.class,()->new PublicRateLimitFilter(fixed,0,1));
        assertThrows(IllegalArgumentException.class,()->new PublicRateLimitFilter(fixed,1,0));
    }
}