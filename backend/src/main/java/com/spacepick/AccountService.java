package com.spacepick;

import java.util.*;
import java.nio.charset.StandardCharsets;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.constraints.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Value;

@Service
public class AccountService {
    public record Signup(@NotBlank @Email @Size(max=255) String email,@NotBlank @Size(min=8,max=72) String password,@NotBlank @Size(max=50) String name) {}
    public record Login(@NotBlank @Email String email,@NotBlank @Size(max=72) String password) {}
    public record Profile(long id,String email,String name,String role) {}
    private final JdbcTemplate db; private final PasswordEncoder passwords; private final String dummy;
    public AccountService(JdbcTemplate db,PasswordEncoder passwords){this.db=db;this.passwords=passwords;dummy=passwords.encode(UUID.randomUUID().toString());}
    @Bean ApplicationRunner bootstrap(@Value("${app.admin-email}") String email,@Value("${app.admin-password}") String password){return args->{
        if(password.getBytes(StandardCharsets.UTF_8).length<12||password.getBytes(StandardCharsets.UTF_8).length>72) throw new IllegalArgumentException("Admin password must be 12 to 72 bytes");
        if(db.queryForObject("SELECT COUNT(*) FROM members WHERE email=?",Long.class,email.toLowerCase(Locale.ROOT))==0)
            db.update("INSERT INTO members(email,password_hash,name,role) VALUES (?,?,?,'ADMIN')",email.toLowerCase(Locale.ROOT),passwords.encode(password),"SPACE PICK 운영자");
    };}
    @Transactional public Profile signup(Signup request){
        if(request.password().getBytes(StandardCharsets.UTF_8).length>72)throw new ApiException(400,"비밀번호는 최대 72바이트입니다.");
        String email=request.email().trim().toLowerCase(Locale.ROOT);
        db.update("INSERT INTO members(email,password_hash,name) VALUES (?,?,?)",email,passwords.encode(request.password()),request.name().trim());
        return byEmail(email);
    }
    public Profile login(Login request){
        String email=request.email().trim().toLowerCase(Locale.ROOT);
        List<Map<String,Object>> rows=db.queryForList("SELECT * FROM members WHERE email=?",email);
        String hash=rows.isEmpty()?dummy:(String)rows.getFirst().get("password_hash");
        if(request.password().getBytes(StandardCharsets.UTF_8).length>72||!passwords.matches(request.password(),hash)||rows.isEmpty())throw new ApiException(401,"이메일 또는 비밀번호를 확인해 주세요.");
        return byEmail(email);
    }
    private Profile byEmail(String email){return db.queryForObject("SELECT id,email,name,role FROM members WHERE email=?",(r,i)->new Profile(r.getLong(1),r.getString(2),r.getString(3),r.getString(4)),email);}
    public Profile current(HttpSession session){
        Object id=session.getAttribute("member");if(id==null)throw new ApiException(401,"로그인이 필요합니다.");
        return db.queryForObject("SELECT id,email,name,role FROM members WHERE id=?",(r,i)->new Profile(r.getLong(1),r.getString(2),r.getString(3),r.getString(4)),id);
    }
    public Profile admin(HttpSession session){Profile p=current(session);if(!p.role().equals("ADMIN"))throw new ApiException(403,"운영자만 이용할 수 있습니다.");return p;}
}
