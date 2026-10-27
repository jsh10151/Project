package com.spacepick;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
@RestController @RequestMapping("/api/payments")
public class PaymentController {
 private final PaymentService payments;private final AccountService accounts;
 public PaymentController(PaymentService payments,AccountService accounts){this.payments=payments;this.accounts=accounts;}
 public record Confirm(@NotBlank String orderId,@NotBlank @Size(max=200) String paymentKey,@Positive int amount){}
 @GetMapping("/config") public Object config(){return payments.config();}
 @PostMapping("/orders") @ResponseStatus(HttpStatus.CREATED) public PaymentService.Order start(HttpSession session,@Valid @RequestBody BookingService.Create body){return payments.start(accounts.current(session).id(),body);}
 @GetMapping("/orders/{id}") public PaymentService.Order get(HttpSession session,@PathVariable String id){return payments.get(accounts.current(session).id(),id);}
 @PostMapping("/orders/{id}/reconcile") public PaymentService.Order reconcile(HttpSession session,@PathVariable String id){return payments.reconcile(accounts.current(session).id(),id);}
 @PostMapping("/confirm") public PaymentService.Order confirm(HttpSession session,@Valid @RequestBody Confirm r){return payments.confirm(accounts.current(session).id(),r.orderId(),r.paymentKey(),r.amount());}
}