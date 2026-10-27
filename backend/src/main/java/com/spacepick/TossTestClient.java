package com.spacepick;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.*;
import tools.jackson.databind.*;

/** Prepared test-only gateway. Not exposed as an HTTP endpoint until order-state integration is complete. */
@Service
public class TossTestClient {
 private final RestClient client;private final boolean enabled;
 @org.springframework.beans.factory.annotation.Autowired
 public TossTestClient(@Value("${payments.toss.enabled:false}") boolean enabled,@Value("${payments.toss.test-secret-key:}") String secret){
  this(enabled,secret,RestClient.builder().requestFactory(factory()));
 }
 TossTestClient(boolean enabled,String secret,RestClient.Builder builder){
  this.enabled=enabled;
  if(enabled&&!(secret.startsWith("test_sk_")||secret.startsWith("test_gsk_")))throw new IllegalArgumentException("Only Toss test secret keys are accepted");
  client=builder.baseUrl("https://api.tosspayments.com").defaultHeader("Authorization","Basic "+Base64.getEncoder().encodeToString((secret+":").getBytes(StandardCharsets.UTF_8))).build();
 }
 private static JdkClientHttpRequestFactory factory(){var factory=new JdkClientHttpRequestFactory(java.net.http.HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(5)).build());factory.setReadTimeout(java.time.Duration.ofSeconds(15));return factory;}
 public JsonNode confirm(String paymentKey,String serverOrderId,int serverAmount){
  validate(paymentKey,serverOrderId,serverAmount);
  JsonNode result=post("/v1/payments/confirm",Map.of("paymentKey",paymentKey,"orderId",serverOrderId,"amount",serverAmount),"confirm-"+serverOrderId);
  verify(result,paymentKey,serverOrderId,serverAmount,"DONE");return result;
 }
 public JsonNode refund(String paymentKey,String serverOrderId,int serverAmount){
  validate(paymentKey,serverOrderId,serverAmount);
  JsonNode result=post("/v1/payments/{key}/cancel",Map.of("cancelReason","사용자 예약 취소"),"refund-"+serverOrderId,paymentKey);
  verify(result,paymentKey,serverOrderId,serverAmount,"CANCELED");return result;
 }
 public JsonNode lookup(String paymentKey,String serverOrderId,int serverAmount){
  validate(paymentKey,serverOrderId,serverAmount);
  try{
   JsonNode result=new ObjectMapper().readTree(client.get().uri("/v1/payments/{key}",paymentKey).retrieve().body(String.class));
   verify(result,paymentKey,serverOrderId,serverAmount,result.path("status").asString());return result;
  }catch(RestClientException ex){throw new ApiException(502,"결제 결과 조회에 실패했습니다. 같은 주문으로 다시 확인해 주세요.");}
 }
 private void validate(String key,String order,int amount){
  if(!enabled)throw new ApiException(503,"테스트 결제 키가 설정되지 않았습니다.");
  if(key==null||key.isBlank()||key.length()>200||order==null||!order.matches("[A-Za-z0-9_-]{6,64}")||amount<=0)throw new ApiException(400,"결제 요청 정보를 확인해 주세요.");
 }
 private JsonNode post(String path,Map<String,Object> body,String idempotency,Object... variables){
  try{return new ObjectMapper().readTree(client.post().uri(path,variables).header("Idempotency-Key",idempotency).contentType(org.springframework.http.MediaType.APPLICATION_JSON).body(body).retrieve().body(String.class));}
  catch(RestClientException ex){throw new ApiException(502,"결제 처리 결과를 확인해야 합니다. 동일 주문을 새로 결제하지 마세요.");}
 }
 private void verify(JsonNode result,String key,String order,int amount,String status){
  if(result==null||!result.path("paymentKey").asString().equals(key)||!result.path("orderId").asString().equals(order)||result.path("totalAmount").asInt()!=amount||!result.path("status").asString().equals(status))throw new ApiException(502,"결제사 응답과 서버 주문이 일치하지 않습니다. 결과 재확인이 필요합니다.");
 }
}