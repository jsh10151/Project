package com.spacepick;

import java.time.*;
import java.sql.Timestamp;
import java.util.*;
import jakarta.validation.constraints.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingService {
    public record Create(@Positive long spaceId,@NotNull OffsetDateTime startsAt,@NotNull OffsetDateTime endsAt,
        @Min(1) @Max(100) int guests,@NotBlank @Size(max=64) String requestKey) {}
    public record Booking(long id,long spaceId,String spaceName,String imageUrl,String district,String memberName,Instant startsAt,Instant endsAt,int guests,int totalPrice,String status,String paymentOrderId) {}
    public record Busy(Instant startsAt,Instant endsAt) {}
    private final JdbcTemplate db;private final SpaceService spaces;
    public BookingService(JdbcTemplate db,SpaceService spaces){this.db=db;this.spaces=spaces;}
    private org.springframework.jdbc.core.RowMapper<Booking> mapper(){return (r,i)->new Booking(r.getLong("id"),r.getLong("space_id"),r.getString("name"),r.getString("image_url"),r.getString("district"),r.getString("member_name"),r.getTimestamp("starts_at",Calendar.getInstance(TimeZone.getTimeZone("UTC"))).toInstant(),r.getTimestamp("ends_at",Calendar.getInstance(TimeZone.getTimeZone("UTC"))).toInstant(),r.getInt("guests"),r.getInt("total_price"),r.getString("booking_status"),r.getString("order_id"));}
    private String select(){return "SELECT b.*,p.order_id,s.name,s.image_url,s.district,m.name AS member_name,CASE WHEN b.status='CANCELLED' THEN 'CANCELLED' WHEN p.state='PENDING' AND p.expires_at<=UTC_TIMESTAMP(6) THEN 'EXPIRED' WHEN p.state='PENDING' THEN 'PENDING_PAYMENT' WHEN p.state='CONFIRMING' THEN 'PAYMENT_CHECKING' WHEN p.state='REFUNDING' THEN 'REFUND_PENDING' ELSE b.status END AS booking_status FROM bookings b LEFT JOIN payment_orders p ON p.booking_id=b.id JOIN spaces s ON s.id=b.space_id JOIN members m ON m.id=b.member_id";}
    public List<Booking> mine(long member){return db.query(select()+" WHERE b.member_id=? ORDER BY b.created_at DESC",mapper(),member);}
    public List<Booking> all(){return db.query(select()+" ORDER BY b.starts_at DESC",mapper());}
    public List<Busy> busy(long space,LocalDate date){
        spaces.get(space); Instant from=date.atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant();
        return db.query("SELECT starts_at,ends_at FROM bookings WHERE space_id=? AND status='CONFIRMED' AND NOT EXISTS (SELECT 1 FROM payment_orders p WHERE p.booking_id=bookings.id AND p.state='PENDING' AND p.expires_at<=UTC_TIMESTAMP(6)) AND starts_at<? AND ends_at>?",(r,i)->new Busy(r.getTimestamp(1,Calendar.getInstance(TimeZone.getTimeZone("UTC"))).toInstant(),r.getTimestamp(2,Calendar.getInstance(TimeZone.getTimeZone("UTC"))).toInstant()),space,LocalDateTime.ofInstant(from.plusSeconds(86400),ZoneOffset.UTC),LocalDateTime.ofInstant(from,ZoneOffset.UTC));
    }
    @Transactional public Booking create(long member,Create r){
        var space=spaces.lock(r.spaceId());
        var existing=db.query(select()+" WHERE b.member_id=? AND b.request_key=?",mapper(),member,r.requestKey());
        if(!existing.isEmpty()){
            Booking b=existing.getFirst();
            if(b.spaceId()!=r.spaceId()||!b.startsAt().equals(r.startsAt().toInstant())||!b.endsAt().equals(r.endsAt().toInstant())||b.guests()!=r.guests())throw new ApiException(409,"같은 요청 키의 예약 정보가 다릅니다.");
            if(!(b.status().equals("CONFIRMED")||b.status().equals("PENDING_PAYMENT")||b.status().equals("PAYMENT_CHECKING")))throw new ApiException(409,"취소된 예약 요청입니다. 날짜와 시간을 다시 선택해 주세요.");
            return b;
        }
        var start=r.startsAt().atZoneSameInstant(ZoneId.of("Asia/Seoul"));var end=r.endsAt().atZoneSameInstant(ZoneId.of("Asia/Seoul"));
        long seconds=Duration.between(start,end).getSeconds();
        if(!start.toInstant().isAfter(Instant.now())||!start.toLocalDate().equals(end.toLocalDate())||seconds<3600||seconds>43200||seconds%3600!=0||start.getMinute()!=0||start.getSecond()!=0||start.getNano()!=0||end.getMinute()!=0||end.getSecond()!=0||end.getNano()!=0||start.getHour()<space.openHour()||end.getHour()>space.closeHour()||r.guests()<1||r.guests()>space.capacity())throw new ApiException(400,"미래의 영업시간 내에서 정각 기준 1~12시간을 선택하고 수용 인원을 확인해 주세요.");
        Long count=db.queryForObject("SELECT COUNT(*) FROM bookings WHERE space_id=? AND status='CONFIRMED' AND NOT EXISTS (SELECT 1 FROM payment_orders p WHERE p.booking_id=bookings.id AND p.state='PENDING' AND p.expires_at<=UTC_TIMESTAMP(6)) AND starts_at<? AND ends_at>?",Long.class,r.spaceId(),LocalDateTime.ofInstant(end.toInstant(),ZoneOffset.UTC),LocalDateTime.ofInstant(start.toInstant(),ZoneOffset.UTC));
        if(count>0)throw new ApiException(409,"선택한 시간에 이미 예약이 있습니다. 다른 시간을 선택해 주세요.");
        int total=Math.toIntExact(seconds/3600*space.hourlyPrice());
        db.update("INSERT INTO bookings(member_id,space_id,starts_at,ends_at,guests,total_price,request_key) VALUES(?,?,?,?,?,?,?)",member,r.spaceId(),LocalDateTime.ofInstant(start.toInstant(),ZoneOffset.UTC),LocalDateTime.ofInstant(end.toInstant(),ZoneOffset.UTC),r.guests(),total,r.requestKey());
        return db.queryForObject(select()+" WHERE b.id=LAST_INSERT_ID()",mapper());
    }
    @Transactional public void cancel(long member,long id){
        var rows=db.query(select()+" WHERE b.id=? AND b.member_id=?",mapper(),id,member);
        if(rows.isEmpty())throw new ApiException(404,"예약을 찾을 수 없습니다.");
        Booking b=rows.getFirst();spaces.lock(b.spaceId());
        String status=db.queryForObject("SELECT status FROM bookings WHERE id=? FOR UPDATE",String.class,id);
        if("CANCELLED".equals(status))return;
        if(!b.startsAt().isAfter(Instant.now()))throw new ApiException(409,"시작된 예약은 취소할 수 없습니다.");
        db.update("UPDATE bookings SET status='CANCELLED' WHERE id=? AND member_id=?",id,member);
    }
}
