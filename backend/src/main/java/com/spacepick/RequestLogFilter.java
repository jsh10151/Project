package com.spacepick;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.*;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
@Component
public class RequestLogFilter extends OncePerRequestFilter {
 private static final Logger log=LoggerFactory.getLogger(RequestLogFilter.class);
 @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)throws IOException,ServletException{
  String id=UUID.randomUUID().toString();long start=System.nanoTime();MDC.put("requestId",id);res.setHeader("X-Request-ID",id);
  try{chain.doFilter(req,res);}finally{
   // No query, body, credentials, or cookies in access logs.
   log.info("http method={} status={} durationMs={}",req.getMethod(),res.getStatus(),(System.nanoTime()-start)/1000000);MDC.remove("requestId");
  }
 }
}