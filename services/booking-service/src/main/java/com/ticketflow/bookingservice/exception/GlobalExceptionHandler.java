package com.ticketflow.bookingservice.exception;

import feign.RetryableException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Maps exceptions to structured JSON errors (status, error, message, path, timestamp). */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler({BookingNotFoundException.class, CustomerNotFoundException.class,
            EventNotFoundException.class})
    ResponseEntity<ErrorResponse> notFound(RuntimeException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ErrorResponse> noResource(HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "resource not found", request);
    }

    @ExceptionHandler({EventNotBookableException.class, NotEnoughSeatsException.class})
    ResponseEntity<ErrorResponse> conflict(RuntimeException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(ServiceUnavailableException.class)
    ResponseEntity<ErrorResponse> unavailable(ServiceUnavailableException ex, HttpServletRequest request) {
        log.warn("{} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage(),
                ex.getCause());
        return build(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), request);
    }

    /**
     * Safety net: the service layer already turns these into
     * ServiceUnavailableException, but a remote call that escapes it must
     * still give a 503, never a 500.
     */
    @ExceptionHandler(CallNotPermittedException.class)
    ResponseEntity<ErrorResponse> circuitOpen(CallNotPermittedException ex, HttpServletRequest request) {
        log.warn("{} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.SERVICE_UNAVAILABLE, ex.getCausingCircuitBreakerName()
                + " is unavailable (circuit breaker open), try again later", request);
    }

    @ExceptionHandler(RetryableException.class)
    ResponseEntity<ErrorResponse> connectionError(RetryableException ex, HttpServletRequest request) {
        log.warn("{} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.SERVICE_UNAVAILABLE, "a remote service is unavailable, try again later", request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> invalidBody(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .sorted()
                .collect(Collectors.joining("; "));
        return build(HttpStatus.BAD_REQUEST, message, request);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ErrorResponse> invalidParameter(HandlerMethodValidationException ex, HttpServletRequest request) {
        String message = ex.getParameterValidationResults().stream()
                .flatMap(r -> r.getResolvableErrors().stream()
                        .map(e -> r.getMethodParameter().getParameterName() + ": " + e.getDefaultMessage()))
                .sorted()
                .collect(Collectors.joining("; "));
        return build(HttpStatus.BAD_REQUEST, message, request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ErrorResponse> missingParameter(MissingServletRequestParameterException ex,
                                                   HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getParameterName() + ": is required", request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ErrorResponse> typeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getName() + ": invalid value", request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorResponse> unreadable(HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "malformed JSON request body", request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ErrorResponse> methodNotAllowed(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, ex.getMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> unexpected(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "unexpected error", request);
    }

    private static ResponseEntity<ErrorResponse> build(HttpStatus status, String message, HttpServletRequest request) {
        return ResponseEntity.status(status).body(new ErrorResponse(status.value(),
                status.getReasonPhrase(), message, request.getRequestURI(), Instant.now()));
    }
}
