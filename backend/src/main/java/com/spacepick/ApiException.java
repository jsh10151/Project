package com.spacepick;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.dao.DataIntegrityViolationException;

public class ApiException extends RuntimeException {
    final int status;
    public ApiException(int status,String message){super(message);this.status=status;}
    @RestControllerAdvice
    public static class Handler {
        @ExceptionHandler(ApiException.class) ResponseEntity<?> business(ApiException e){return error(e.status,e.getMessage());}
        @ExceptionHandler({MethodArgumentNotValidException.class,HttpMessageNotReadableException.class,MethodArgumentTypeMismatchException.class})
        ResponseEntity<?> invalid(Exception e){return error(400,"입력값과 날짜 형식을 확인해 주세요.");}
        @ExceptionHandler(DataIntegrityViolationException.class) ResponseEntity<?> conflict(Exception e){return error(409,"이미 등록된 정보이거나 데이터 조건에 맞지 않습니다.");}
        private ResponseEntity<?> error(int status,String message){return ResponseEntity.status(status).body(Map.of("message",message,"timestamp",Instant.now().toString()));}
    }
}
