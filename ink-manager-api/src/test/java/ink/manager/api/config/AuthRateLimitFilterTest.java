package ink.manager.api.config;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import static org.junit.jupiter.api.Assertions.*;
class AuthRateLimitFilterTest {
 @Test void limitaTentativasERetornaRetryAfter() throws Exception {
  var filter=new AuthRateLimitFilter(Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"),ZoneOffset.UTC),2);
  for(int i=0;i<3;i++) {
   var request=new MockHttpServletRequest("POST","/auth/login"); request.setServletPath("/auth/login");
   var response=new MockHttpServletResponse(); filter.doFilter(request,response,new MockFilterChain());
   assertEquals(i==2?429:200,response.getStatus());
   if(i==2) assertEquals("60",response.getHeader("Retry-After"));
  }
 }
}
