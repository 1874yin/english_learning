package com.xiaojie.englishlearning.user;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.xiaojie.englishlearning.user")
public class UserExceptionHandler {
    @ExceptionHandler(UserException.class)
    public ResponseEntity<Map<String, String>> userError(UserException ex) {
        return ResponseEntity.status(ex.getStatus()).body(Collections.singletonMap("message", ex.getMessage()));
    }

    @ExceptionHandler(IOException.class)
    public ResponseEntity<Map<String, String>> storageError(IOException ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Collections.singletonMap("message", "记录保存失败，请稍后重试。"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> invalidInput(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest().body(Collections.singletonMap("message", "请求格式不正确。"));
    }
}
