package com.spacepick;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.http.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.assertj.core.api.Assertions.*;
class TossTestClientTest {
 @Test void liveKeysAreRejectedAndDisabledClientNeverCallsProvider(){
  assertThatThrownBy(()->new TossTestClient(true,"live_sk_forbidden",RestClient.builder())).isInstanceOf(IllegalArgumentException.class);
  var disabled=new TossTestClient(false,"",RestClient.builder());assertThatThrownBy(()->disabled.confirm("key","order_123",100)).isInstanceOf(ApiException.class);
 }
 @Test void confirmAndRefundUseStableKeysAndVerifyServerAmount(){
  var builder=RestClient.builder();var mock=MockRestServiceServer.bindTo(builder).build();var client=new TossTestClient(true,"test_sk_mock",builder);
  mock.expect(requestTo("https://api.tosspayments.com/v1/payments/confirm")).andExpect(method(HttpMethod.POST)).andExpect(header("Idempotency-Key","confirm-order_123")).andExpect(content().json("{\"paymentKey\":\"key\",\"orderId\":\"order_123\",\"amount\":70000}")).andRespond(withSuccess("{\"paymentKey\":\"key\",\"orderId\":\"order_123\",\"totalAmount\":70000,\"status\":\"DONE\"}",MediaType.APPLICATION_JSON));
  mock.expect(requestTo("https://api.tosspayments.com/v1/payments/key/cancel")).andExpect(header("Idempotency-Key","refund-order_123")).andRespond(withSuccess("{\"paymentKey\":\"key\",\"orderId\":\"order_123\",\"totalAmount\":70000,\"status\":\"CANCELED\"}",MediaType.APPLICATION_JSON));
  client.confirm("key","order_123",70000);client.refund("key","order_123",70000);mock.verify();
 }
 @Test void mismatchedAmountIsNotAccepted(){
  var builder=RestClient.builder();var mock=MockRestServiceServer.bindTo(builder).build();var client=new TossTestClient(true,"test_sk_mock",builder);
  mock.expect(requestTo("https://api.tosspayments.com/v1/payments/confirm")).andRespond(withSuccess("{\"paymentKey\":\"key\",\"orderId\":\"order_123\",\"totalAmount\":1,\"status\":\"DONE\"}",MediaType.APPLICATION_JSON));
  assertThatThrownBy(()->client.confirm("key","order_123",70000)).isInstanceOf(ApiException.class);mock.verify();
 }
}