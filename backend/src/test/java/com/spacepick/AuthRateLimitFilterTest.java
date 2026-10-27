package com.spacepick;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import static org.assertj.core.api.Assertions.*;
class AuthRateLimitFilterTest {
 static class MutableClock extends Clock {
  Instant instant=Instant.parse("2026-10-28T00:00:00Z");
  public ZoneId getZone(){return ZoneOffset.UTC;}
  public Clock withZone(ZoneId zone){return this;}
  public Instant instant(){return instant;}
 }
 MockHttpServletResponse send(AuthRateLimitFilter filter,String path,String ip) throws Exception {
  var req=new MockHttpServletRequest("POST",path);req.setServletPath(path);req.setRemoteAddr(ip);
  var res=new MockHttpServletResponse();filter.doFilter(req,res,(r,s)->((jakarta.servlet.http.HttpServletResponse)s).setStatus(204));return res;
 }
 @Test void loginLimitResetsAndDoesNotBlockOtherClients() throws Exception {
  var clock=new MutableClock();var filter=new AuthRateLimitFilter(clock);
  for(int i=0;i<20;i++) assertThat(send(filter,"/api/auth/login","127.0.0.1").getStatus()).isEqualTo(204);
  var blocked=send(filter,"/api/auth/login","127.0.0.1");assertThat(blocked.getStatus()).isEqualTo(429);assertThat(blocked.getHeader("Retry-After")).isEqualTo("60");
  assertThat(send(filter,"/api/auth/login","127.0.0.2").getStatus()).isEqualTo(204);
  clock.instant=clock.instant.plusSeconds(60);assertThat(send(filter,"/api/auth/login","127.0.0.1").getStatus()).isEqualTo(204);
 }
 @Test void signupHasSeparateLimitAndBookingsAreUnaffected() throws Exception {
  var filter=new AuthRateLimitFilter();
  for(int i=0;i<5;i++)assertThat(send(filter,"/api/auth/signup","127.0.0.1").getStatus()).isEqualTo(204);
  assertThat(send(filter,"/api/auth/signup","127.0.0.1").getStatus()).isEqualTo(429);
  assertThat(send(filter,"/api/auth/login","127.0.0.1").getStatus()).isEqualTo(204);
  assertThat(send(filter,"/api/bookings","127.0.0.1").getStatus()).isEqualTo(204);
 }
}