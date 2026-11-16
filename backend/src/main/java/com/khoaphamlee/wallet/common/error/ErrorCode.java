package com.khoaphamlee.wallet.common.error;

import org.springframework.http.HttpStatus;

/**
 * Every error the API can return, with its HTTP status and a short human-readable title.
 *
 * <p>The constant name is the stable {@code code} that clients rely on, so never rename an existing
 * constant. The full list is documented in {@code docs/architecture.md}, section "Xử lý lỗi".
 */
public enum ErrorCode {

    // 400 Bad Request
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Validation failed"),
    IDEMPOTENCY_KEY_REQUIRED(HttpStatus.BAD_REQUEST, "Idempotency-Key header is required"),

    // 401 Unauthorized
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Invalid email or password"),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "Invalid or expired token"),

    // 403 Forbidden
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "Access denied"),
    INVALID_PIN(HttpStatus.FORBIDDEN, "Invalid PIN"),
    PIN_LOCKED(HttpStatus.FORBIDDEN, "PIN is temporarily locked"),
    PIN_NOT_SET(HttpStatus.FORBIDDEN, "PIN has not been set"),
    WALLET_LOCKED(HttpStatus.FORBIDDEN, "Wallet is locked"),

    // 404 Not Found
    RECIPIENT_NOT_FOUND(HttpStatus.NOT_FOUND, "Recipient not found"),
    TRANSACTION_NOT_FOUND(HttpStatus.NOT_FOUND, "Transaction not found"),
    WALLET_NOT_FOUND(HttpStatus.NOT_FOUND, "Wallet not found"),

    // 409 Conflict
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "Email is already registered"),
    PHONE_ALREADY_EXISTS(HttpStatus.CONFLICT, "Phone number is already registered"),
    DUPLICATE_REQUEST(HttpStatus.CONFLICT, "A request with this Idempotency-Key is still in progress"),

    // 422 Unprocessable Content
    INSUFFICIENT_BALANCE(HttpStatus.UNPROCESSABLE_CONTENT, "Insufficient balance"),
    DAILY_LIMIT_EXCEEDED(HttpStatus.UNPROCESSABLE_CONTENT, "Daily transfer limit exceeded"),
    AMOUNT_OUT_OF_RANGE(HttpStatus.UNPROCESSABLE_CONTENT, "Amount is out of the allowed range"),
    SELF_TRANSFER_NOT_ALLOWED(HttpStatus.UNPROCESSABLE_CONTENT, "Cannot transfer to your own wallet"),
    IDEMPOTENCY_KEY_REUSED(
            HttpStatus.UNPROCESSABLE_CONTENT, "Idempotency-Key was already used with a different request"),

    // 429 Too Many Requests
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Too many requests"),

    // 5xx
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error"),
    SERVICE_BUSY(HttpStatus.SERVICE_UNAVAILABLE, "Service is busy, please retry");

    private final HttpStatus status;
    private final String title;

    ErrorCode(HttpStatus status, String title) {
        this.status = status;
        this.title = title;
    }

    public HttpStatus status() {
        return status;
    }

    public String title() {
        return title;
    }
}
