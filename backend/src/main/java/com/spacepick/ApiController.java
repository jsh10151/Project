package com.spacepick;

import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api")
public class ApiController {
    private final PaymentService payments;private final AccountService accounts;private final SpaceService spaces;private final BookingService bookings;
    public ApiController(AccountService accounts,SpaceService spaces,BookingService bookings,PaymentService payments){this.payments=payments;this.accounts=accounts;this.spaces=spaces;this.bookings=bookings;}
    @GetMapping("/csrf") public ResponseEntity<?> csrf(CsrfToken token){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("token",token.getToken()));}
    @PostMapping("/auth/signup") @ResponseStatus(HttpStatus.CREATED) public AccountService.Profile signup(@Valid @RequestBody AccountService.Signup r){return accounts.signup(r);}
    @PostMapping("/auth/login") public AccountService.Profile login(@Valid @RequestBody AccountService.Login r,HttpServletRequest request){var p=accounts.login(r);request.getSession();request.changeSessionId();request.getSession().setAttribute("member",p.id());return p;}
    @GetMapping("/auth/me") public AccountService.Profile me(HttpSession session){return accounts.current(session);}
    @PostMapping("/auth/logout") @ResponseStatus(HttpStatus.NO_CONTENT) public void logout(HttpSession session){session.invalidate();}
    @GetMapping("/spaces") public List<SpaceService.Space> list(@RequestParam(required=false) String district,@RequestParam(required=false) String kind,@RequestParam(required=false) Integer guests,@RequestParam(required=false) Integer maxPrice){return spaces.list(district,kind,guests,maxPrice);}
    @GetMapping("/spaces/{id}") public SpaceService.Space detail(@PathVariable long id){return spaces.get(id);}
    @GetMapping("/spaces/{id}/availability") public List<BookingService.Busy> availability(@PathVariable long id,@RequestParam LocalDate date){return bookings.busy(id,date);}
    @GetMapping("/bookings") public List<BookingService.Booking> mine(HttpSession session){return bookings.mine(accounts.current(session).id());}
    @PostMapping("/bookings") @ResponseStatus(HttpStatus.CREATED) public BookingService.Booking book(@Valid @RequestBody BookingService.Create r,HttpSession session){payments.requireDemoMode();return bookings.create(accounts.current(session).id(),r);}
    @PostMapping("/bookings/{id}/cancel") @ResponseStatus(HttpStatus.NO_CONTENT) public void cancel(@PathVariable long id,HttpSession session){payments.cancelBooking(accounts.current(session).id(),id);}
    @PostMapping("/admin/spaces") @ResponseStatus(HttpStatus.CREATED) public SpaceService.Space add(@Valid @RequestBody SpaceService.Create r,HttpSession session){accounts.admin(session);return spaces.create(r);}
    @GetMapping("/admin/bookings") public List<BookingService.Booking> all(HttpSession session){accounts.admin(session);return bookings.all();}
}
