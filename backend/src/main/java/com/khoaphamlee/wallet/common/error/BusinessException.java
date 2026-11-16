package com.khoaphamlee.wallet.common.error;

/**
 * Base class for expected business errors, such as an insufficient balance or a wrong PIN.
 *
 * <p>Service code throws it with an {@link ErrorCode}. The global exception handler (step 4) turns
 * it into a ProblemDetail response using the status and code of that {@link ErrorCode}.
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    /** Uses the default title of the error code as the message. */
    public BusinessException(ErrorCode errorCode) {
        this(errorCode, errorCode.title());
    }

    /** Adds a specific detail, for example "Balance 20000 is less than 30000". */
    public BusinessException(ErrorCode errorCode, String detail) {
        super(detail);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
