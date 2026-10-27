package com.spacepick;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.junit.jupiter.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Testcontainers
class BookingIntegrationTest {
 @Container static final MySQLContainer mysql=new MySQLContainer("mysql:8.4");
 @DynamicPropertySource static void properties(DynamicPropertyRegistry p){
  p.add("spring.datasource.url",mysql::getJdbcUrl);p.add("spring.datasource.username",mysql::getUsername);p.add("spring.datasource.password",mysql::getPassword);p.add("app.admin-password",()->"TestAdminPassword!123");
 }
 @Autowired BookingService bookings;
 @Autowired JdbcTemplate db;
 long member;
 OffsetDateTime start;
 @BeforeEach void setup(){db.update("DELETE FROM payment_orders");db.update("DELETE FROM bookings");db.update("INSERT INTO members(email,password_hash,name) VALUES (?, 'unused', 'test')",UUID.randomUUID()+"@example.test");member=db.queryForObject("SELECT MAX(id) FROM members",Long.class);start=LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(3).atTime(10,0).atOffset(ZoneOffset.ofHours(9));}
 BookingService.Create request(String key){return new BookingService.Create(1,start,start.plusHours(2),2,key);}
 @Test void priceIdempotencyOwnershipAndCancelledReplay(){
  var b=bookings.create(member,request("same"));assertThat(b.totalPrice()).isEqualTo(70000);assertThat(b.startsAt()).isEqualTo(start.toInstant());assertThat(bookings.create(member,request("same")).id()).isEqualTo(b.id());
  assertThatThrownBy(()->bookings.cancel(member+10000,b.id())).isInstanceOf(ApiException.class).hasMessageContaining("찾을 수");
  bookings.cancel(member,b.id());bookings.cancel(member,b.id());assertThat(bookings.mine(member).getFirst().status()).isEqualTo("CANCELLED");
  assertThatThrownBy(()->bookings.create(member,request("same"))).isInstanceOf(ApiException.class).hasMessageContaining("취소된");
  assertThat(bookings.create(member,request("new")).status()).isEqualTo("CONFIRMED");
 }
 @Test void concurrentOverlapHasExactlyOneWinner() throws Exception {
  try(var pool=Executors.newFixedThreadPool(2)){
   CountDownLatch gate=new CountDownLatch(1);
   Callable<String> run=()->{gate.await();try{bookings.create(member,request(UUID.randomUUID().toString()));return "OK";}catch(ApiException e){return "CONFLICT";}};
   var a=pool.submit(run);var b=pool.submit(run);gate.countDown();assertThat(List.of(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder("OK","CONFLICT");
  }
 }
 @Test void adjacentSlotsAreAllowedAndInvalidRangesRejected(){
  bookings.create(member,request("first"));
  assertThat(bookings.create(member,new BookingService.Create(1,start.plusHours(2),start.plusHours(3),1,"adjacent")).totalPrice()).isEqualTo(35000);
  assertThatThrownBy(()->bookings.create(member,new BookingService.Create(1,start.minusDays(10),start.minusDays(10).plusHours(1),1,"past"))).isInstanceOf(ApiException.class);
  assertThatThrownBy(()->bookings.create(member,new BookingService.Create(1,start,start.plusHours(1),99,"capacity"))).isInstanceOf(ApiException.class);
  assertThatThrownBy(()->bookings.create(member,new BookingService.Create(1,start.withHour(21),start.withHour(23),1,"closed"))).isInstanceOf(ApiException.class);
 }
 @Autowired org.springframework.transaction.PlatformTransactionManager manager;
 @Autowired SpaceService spaces;
 PaymentService payment(TossTestClient gateway){return new PaymentService(db,bookings,spaces,gateway,manager,true,"test_ck_mock");}
 @Test void paymentApprovalRefundAndOwnershipAreLinked(){
  var gateway=org.mockito.Mockito.mock(TossTestClient.class);var service=payment(gateway);var order=service.start(member,request("pay"));
  assertThat(bookings.mine(member).getFirst().status()).isEqualTo("PENDING_PAYMENT");
  assertThatThrownBy(()->service.confirm(member,order.orderId(),"key",1)).isInstanceOf(ApiException.class);
  org.mockito.Mockito.verifyNoInteractions(gateway);
  assertThatThrownBy(()->service.get(member+10000,order.orderId())).isInstanceOf(ApiException.class);
  assertThat(service.confirm(member,order.orderId(),"key",70000).state()).isEqualTo("PAID");
  assertThat(bookings.mine(member).getFirst().status()).isEqualTo("CONFIRMED");
  service.confirm(member,order.orderId(),"key",70000);org.mockito.Mockito.verify(gateway,org.mockito.Mockito.times(1)).confirm("key",order.orderId(),70000);
  service.cancelBooking(member,order.bookingId());service.cancelBooking(member,order.bookingId());
  assertThat(service.get(member,order.orderId()).state()).isEqualTo("REFUNDED");assertThat(bookings.mine(member).getFirst().status()).isEqualTo("CANCELLED");
  org.mockito.Mockito.verify(gateway,org.mockito.Mockito.times(1)).refund("key",order.orderId(),70000);
 }
 @Test void uncertainApprovalKeepsSlotUntilSameOrderReconciliation(){
  var gateway=org.mockito.Mockito.mock(TossTestClient.class);var service=payment(gateway);var order=service.start(member,request("uncertain"));
  org.mockito.Mockito.when(gateway.confirm("key",order.orderId(),70000)).thenThrow(new ApiException(502,"timeout")).thenReturn(null);
  assertThatThrownBy(()->service.confirm(member,order.orderId(),"key",70000)).isInstanceOf(ApiException.class);
  assertThat(bookings.mine(member).getFirst().status()).isEqualTo("PAYMENT_CHECKING");
  assertThatThrownBy(()->bookings.create(member,request("another"))).isInstanceOf(ApiException.class);
  assertThatThrownBy(()->service.cancelBooking(member,order.bookingId())).isInstanceOf(ApiException.class);
  org.mockito.Mockito.when(gateway.lookup("key",order.orderId(),70000)).thenReturn(new tools.jackson.databind.ObjectMapper().readTree("{\"status\":\"DONE\"}"));
  assertThat(service.reconcile(member,order.orderId()).state()).isEqualTo("PAID");
 }
 @Test void expiredPendingHoldIsReleasedAndCannotBeApproved(){
  var gateway=org.mockito.Mockito.mock(TossTestClient.class);var service=payment(gateway);var order=service.start(member,request("expiry"));
  db.update("UPDATE payment_orders SET expires_at=UTC_TIMESTAMP(6)-INTERVAL 1 MINUTE WHERE order_id=?",order.orderId());
  assertThat(service.get(member,order.orderId()).state()).isEqualTo("EXPIRED");
  assertThatThrownBy(()->service.confirm(member,order.orderId(),"key",70000)).isInstanceOf(ApiException.class);
  assertThat(bookings.create(member,request("replacement")).status()).isEqualTo("CONFIRMED");
  org.mockito.Mockito.verifyNoInteractions(gateway);
 }
 @Test void refundFailureDoesNotReleaseBookingAndCanBeRetried(){
  var gateway=org.mockito.Mockito.mock(TossTestClient.class);var service=payment(gateway);var order=service.start(member,request("refund-retry"));service.confirm(member,order.orderId(),"key",70000);
  org.mockito.Mockito.when(gateway.refund("key",order.orderId(),70000)).thenThrow(new ApiException(502,"timeout")).thenReturn(null);
  assertThatThrownBy(()->service.cancelBooking(member,order.bookingId())).isInstanceOf(ApiException.class);
  assertThat(bookings.mine(member).getFirst().status()).isEqualTo("REFUND_PENDING");
  service.cancelBooking(member,order.bookingId());assertThat(service.get(member,order.orderId()).state()).isEqualTo("REFUNDED");
 }}