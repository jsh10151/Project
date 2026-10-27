package com.spacepick;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;

@Service
public class PaymentService {
 public record Order(String orderId,long bookingId,int amount,String state,String customerKey,String clientKey) {}
 private record Stored(String id,long booking,long member,int amount,String state,String key,String customer,LocalDateTime expires,Instant starts) {}
 private final JdbcTemplate db;private final BookingService bookings;private final SpaceService spaces;private final TossTestClient toss;private final TransactionTemplate tx;private final boolean enabled;private final String clientKey;
 public PaymentService(JdbcTemplate db,BookingService bookings,SpaceService spaces,TossTestClient toss,PlatformTransactionManager manager,@Value("${payments.toss.enabled:false}") boolean enabled,@Value("${payments.toss.test-client-key:}") String clientKey){
  this.db=db;this.bookings=bookings;this.spaces=spaces;this.toss=toss;this.tx=new TransactionTemplate(manager);this.enabled=enabled;this.clientKey=clientKey;
  if(enabled&&!clientKey.startsWith("test_ck_"))throw new IllegalArgumentException("Use matching Toss API individual test_ck_/test_sk_ keys for payment windows");
 }
 public void requireDemoMode(){if(enabled)throw new ApiException(409,"결제 주문 생성 경로를 사용해 주세요.");}
 public Map<String,Object> config(){return Map.of("enabled",enabled);}
 private void available(){if(!enabled)throw new ApiException(503,"토스 테스트 결제 키가 아직 설정되지 않았습니다.");}
 private Stored read(long member,String id,boolean lock){
  return db.query("SELECT p.*,b.starts_at FROM payment_orders p JOIN bookings b ON b.id=p.booking_id WHERE p.order_id=? AND p.member_id=?"+(lock?" FOR UPDATE":""),(r,i)->new Stored(r.getString("order_id"),r.getLong("booking_id"),r.getLong("member_id"),r.getInt("amount"),r.getString("state"),r.getString("payment_key"),r.getString("customer_key"),r.getObject("expires_at",LocalDateTime.class),r.getObject("starts_at",LocalDateTime.class).toInstant(ZoneOffset.UTC)),id,member).stream().findFirst().orElseThrow(()->new ApiException(404,"결제 주문을 찾을 수 없습니다."));
 }
 private Order response(Stored s){return new Order(s.id,s.booking,s.amount,s.state.equals("PENDING")&&s.expires.isBefore(LocalDateTime.now(ZoneOffset.UTC))?"EXPIRED":s.state,s.customer,clientKey);}
 public Order get(long member,String id){return response(read(member,id,false));}
 public Order start(long member,BookingService.Create request){available();return tx.execute(status->{
  var booking=bookings.create(member,request);
  var ids=db.queryForList("SELECT order_id FROM payment_orders WHERE booking_id=?",String.class,booking.id());
  if(!ids.isEmpty())return get(member,ids.getFirst());
  String id="sp_"+UUID.randomUUID().toString().replace("-","");String customer="sp_"+UUID.randomUUID();
  db.update("INSERT INTO payment_orders(order_id,booking_id,member_id,amount,state,customer_key,expires_at) VALUES(?,?,?,?,'PENDING',?,?)",id,booking.id(),member,booking.totalPrice(),customer,LocalDateTime.now(ZoneOffset.UTC).plusMinutes(15));
  return get(member,id);
 });}
 public Order confirm(long member,String id,String key,int amount){available();if(key==null||key.isBlank()||key.length()>200)throw new ApiException(400,"결제 키를 확인해 주세요.");
  Stored s=tx.execute(status->{var before=read(member,id,false);long spaceId=db.queryForObject("SELECT space_id FROM bookings WHERE id=?",Long.class,before.booking);spaces.lock(spaceId);var order=read(member,id,true);
   if(order.amount!=amount)throw new ApiException(400,"서버 주문 금액과 일치하지 않습니다.");
   if(order.state.equals("PAID")){if(!key.equals(order.key))throw new ApiException(409,"다른 결제 키입니다.");return order;}
   if(!(order.state.equals("PENDING")||order.state.equals("CONFIRMING")))throw new ApiException(409,"승인할 수 없는 주문 상태입니다.");
   if(order.state.equals("PENDING")&&(order.expires.isBefore(LocalDateTime.now(ZoneOffset.UTC))||!order.starts.isAfter(Instant.now())))throw new ApiException(409,"결제 가능 시간이 만료되었습니다.");
   if(order.key!=null&&!key.equals(order.key))throw new ApiException(409,"다른 결제 키로 재시도할 수 없습니다.");
   db.update("UPDATE payment_orders SET state='CONFIRMING',payment_key=? WHERE order_id=?",key,id);return read(member,id,false);
  });
  if(s.state.equals("PAID"))return response(s);
  // Network call outside DB transaction. Failures keep CONFIRMING and retain the slot.
  toss.confirm(key,id,s.amount);
  return tx.execute(status->{var current=read(member,id,true);if(current.state.equals("CONFIRMING"))db.update("UPDATE payment_orders SET state='PAID' WHERE order_id=?",id);return get(member,id);});
 }
 public Order reconcile(long member,String id){available();Stored s=read(member,id,false);
  if(s.state.equals("PAID"))return response(s);
  if(s.key==null||!s.state.equals("CONFIRMING"))throw new ApiException(409,"승인 결과 확인 중인 주문이 아닙니다.");
  var result=toss.lookup(s.key,s.id,s.amount);
  if(!result.path("status").asString().equals("DONE"))throw new ApiException(409,"결제가 완료되지 않았습니다. 주문 상태를 다시 확인해 주세요.");
  return tx.execute(status->{var current=read(member,id,true);if(current.state.equals("CONFIRMING"))db.update("UPDATE payment_orders SET state='PAID' WHERE order_id=?",id);return get(member,id);});
 }
 public void cancelBooking(long member,long booking){
  var ids=db.queryForList("SELECT order_id FROM payment_orders WHERE booking_id=? AND member_id=?",String.class,booking,member);
  if(ids.isEmpty()){bookings.cancel(member,booking);return;}
  Stored s=tx.execute(status->{Stored before=read(member,ids.getFirst(),false);long spaceId=db.queryForObject("SELECT space_id FROM bookings WHERE id=?",Long.class,before.booking);spaces.lock(spaceId);Stored order=read(member,ids.getFirst(),true);
   if(order.state.equals("REFUNDED")||order.state.equals("CANCELLED"))return order;
   if(order.state.equals("CONFIRMING"))throw new ApiException(409,"승인 결과 확인 중입니다. 같은 주문의 결제 확인을 다시 시도해 주세요.");
   if(order.state.equals("PENDING")){db.update("UPDATE payment_orders SET state='CANCELLED' WHERE order_id=?",order.id);db.update("UPDATE bookings SET status='CANCELLED' WHERE id=?",booking);return read(member,order.id,false);}
   if(!order.state.equals("REFUNDING")&&!order.starts.isAfter(Instant.now()))throw new ApiException(409,"시작된 예약은 환불할 수 없습니다.");
   db.update("UPDATE payment_orders SET state='REFUNDING' WHERE order_id=?",order.id);return read(member,order.id,false);
  });
  if(s.state.equals("CANCELLED")||s.state.equals("REFUNDED"))return;
  toss.refund(s.key,s.id,s.amount);
  tx.executeWithoutResult(status->{read(member,s.id,true);db.update("UPDATE payment_orders SET state='REFUNDED' WHERE order_id=?",s.id);db.update("UPDATE bookings SET status='CANCELLED' WHERE id=?",booking);});
 }
}