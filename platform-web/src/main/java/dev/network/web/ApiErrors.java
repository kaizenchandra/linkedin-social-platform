package dev.network.web;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiErrors {
  @ExceptionHandler({
    DataIntegrityViolationException.class,
    OptimisticLockingFailureException.class
  })
  ResponseEntity<ProblemDetail> conflict(Exception e) {
    return response(HttpStatus.CONFLICT, "Concurrent or duplicate command; reload and retry.");
  }

  @ExceptionHandler({
    MethodArgumentNotValidException.class,
    HttpMessageNotReadableException.class,
    IllegalArgumentException.class,
    org.springframework.web.bind.ServletRequestBindingException.class,
    org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class
  })
  ResponseEntity<ProblemDetail> invalid(Exception e) {
    return response(HttpStatus.BAD_REQUEST, "Invalid request parameters or body.");
  }

  @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
  ResponseEntity<ProblemDetail> uploadTooLarge(Exception e) {
    return response(HttpStatus.PAYLOAD_TOO_LARGE, "Upload exceeds the configured limit.");
  }

  @ExceptionHandler({
    org.springframework.dao.DataAccessResourceFailureException.class,
    org.springframework.dao.CannotAcquireLockException.class,
    org.springframework.dao.QueryTimeoutException.class,
    org.springframework.transaction.CannotCreateTransactionException.class
  })
  ResponseEntity<ProblemDetail> unavailable(Exception e) {
    return response(
        HttpStatus.SERVICE_UNAVAILABLE,
        "Persistence temporarily unavailable; retry the complete request.");
  }

  @ExceptionHandler(ResponseStatusException.class)
  ResponseEntity<ProblemDetail> status(ResponseStatusException e) {
    return response(e.getStatusCode(), e.getReason());
  }

  private ResponseEntity<ProblemDetail> response(HttpStatusCode s, String detail) {
    return ResponseEntity.status(s).body(ProblemDetail.forStatusAndDetail(s, detail));
  }
}
