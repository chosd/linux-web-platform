package com.example.linuxterminal.global.exception;

import java.io.IOException;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(IOException.class)
    public ResponseEntity<ApiErrorResponse> handleIOException(IOException exception) {
        return ResponseEntity.badRequest().body(new ApiErrorResponse(
                Instant.now(),
                HttpStatus.BAD_REQUEST.value(),
                exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationException(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fieldError -> fieldError.getField() + " " + fieldError.getDefaultMessage())
                .orElse("Invalid request.");
        return ResponseEntity.badRequest().body(new ApiErrorResponse(
                Instant.now(),
                HttpStatus.BAD_REQUEST.value(),
                message));
    }

    public record ApiErrorResponse(
            Instant timestamp,
            int status,
            String message
    ) {
    }
}
