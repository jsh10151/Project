package com.spacepick;

import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.*;
import org.springframework.beans.factory.annotation.Value;

@Configuration
public class Security {
    @Bean PasswordEncoder passwords() { return new BCryptPasswordEncoder(); }
    @Bean SecurityFilterChain chain(HttpSecurity http,@Value("${server.servlet.session.cookie.secure}") boolean secure) throws Exception {
        CookieCsrfTokenRepository repository=new CookieCsrfTokenRepository();
        repository.setCookieName("SPACEPICK_CSRF");
        repository.setCookieCustomizer(c->c.path("/api").httpOnly(true).sameSite("Strict").secure(secure));
        return http.authorizeHttpRequests(a->a.anyRequest().permitAll())
            .csrf(c->c.csrfTokenRepository(repository).csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
            .exceptionHandling(e->e.accessDeniedHandler((req,res,error)->{
                res.setStatus(403);res.setContentType("application/json;charset=UTF-8");
                res.getWriter().write("{\"message\":\"요청 인증이 만료되었습니다. 다시 시도해 주세요.\"}");
            })).build();
    }
}
