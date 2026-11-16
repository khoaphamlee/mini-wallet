package com.khoaphamlee.wallet.common.error;

import com.khoaphamlee.wallet.common.web.TraceIdFilter;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns exceptions thrown by any controller into RFC 9457 ProblemDetail responses.
 *
 * <p>Every response gets two extra fields: {@code code} (from {@link ErrorCode}) and {@code traceId}
 * (from {@link TraceIdFilter}). Extending {@link ResponseEntityExceptionHandler} lets Spring keep
 * handling its own HTTP errors (wrong method, unreadable JSON, ...) with the right status.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** One invalid field in a request body, returned in the {@code errors} list. */
    record FieldViolation(String field, String message) {}

    /** Expected business errors: insufficient balance, wrong PIN, ... */
    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ProblemDetail> handleBusiness(BusinessException ex) {
        return ResponseEntity.status(ex.errorCode().status()).body(toProblem(ex.errorCode(), ex.getMessage()));
    }

    /** Anything we did not expect. Log the details, but never send them to the client. */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        ErrorCode errorCode = ErrorCode.INTERNAL_ERROR;
        return ResponseEntity.status(errorCode.status()).body(toProblem(errorCode, errorCode.title()));
    }

    /** A request body annotated with {@code @Valid} failed validation: list every invalid field. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldViolation> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldViolation(error.getField(), error.getDefaultMessage()))
                .toList();

        ErrorCode errorCode = ErrorCode.VALIDATION_ERROR;
        ProblemDetail body = toProblem(errorCode, "One or more fields are invalid");
        body.setProperty("errors", errors);
        return ResponseEntity.status(errorCode.status()).body(body);
    }

    /** Spring's own HTTP errors (405, 415, unreadable JSON, ...): add code and traceId like ours. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
        if (response != null && response.getBody() instanceof ProblemDetail problem) {
            problem.setProperty("code", codeFor(statusCode));
            problem.setProperty("traceId", TraceIdFilter.currentTraceId());
        }
        return response;
    }

    private static ProblemDetail toProblem(ErrorCode errorCode, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(errorCode.status(), detail);
        problem.setTitle(errorCode.title());
        problem.setProperty("code", errorCode.name());
        problem.setProperty("traceId", TraceIdFilter.currentTraceId());
        return problem;
    }

    /** 400 from Spring means bad input; other statuses use the HTTP status name, e.g. METHOD_NOT_ALLOWED. */
    private static String codeFor(HttpStatusCode statusCode) {
        if (statusCode.value() == HttpStatus.BAD_REQUEST.value()) {
            return ErrorCode.VALIDATION_ERROR.name();
        }
        HttpStatus status = HttpStatus.resolve(statusCode.value());
        return status != null ? status.name() : "HTTP_ERROR";
    }
}
