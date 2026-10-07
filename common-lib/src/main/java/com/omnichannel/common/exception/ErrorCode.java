package com.omnichannel.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    BAD_REQUEST(1000, "Bad request", HttpStatus.BAD_REQUEST),
    UNAUTHORIZED(1001, "Unauthorized", HttpStatus.UNAUTHORIZED),
    FORBIDDEN(1002, "Forbidden", HttpStatus.FORBIDDEN),
    NOT_FOUND(1003, "Resource not found", HttpStatus.NOT_FOUND),
    CONFLICT(1004, "Conflict", HttpStatus.CONFLICT),
    UPSTREAM_UNAVAILABLE(1998, "Dependent service unavailable", HttpStatus.SERVICE_UNAVAILABLE),
    INTERNAL_ERROR(1999, "Internal server error", HttpStatus.INTERNAL_SERVER_ERROR),

    USER_ALREADY_EXISTS(2001, "User already exists", HttpStatus.CONFLICT),
    INVALID_CREDENTIALS(2002, "Invalid credentials", HttpStatus.UNAUTHORIZED),
    INVALID_REFRESH_TOKEN(2003, "Invalid refresh token", HttpStatus.UNAUTHORIZED),

    PRODUCT_NOT_FOUND(3001, "Product not found", HttpStatus.NOT_FOUND),

    OUT_OF_STOCK(4001, "Insufficient stock", HttpStatus.CONFLICT),
    ORDER_NOT_FOUND(4002, "Order not found", HttpStatus.NOT_FOUND),
    INVALID_ORDER_STATE(4003, "Invalid order state transition", HttpStatus.CONFLICT),
    DUPLICATE_REQUEST(4004, "Request already being processed", HttpStatus.CONFLICT),

    PAYMENT_NOT_FOUND(5001, "Payment not found", HttpStatus.NOT_FOUND),
    INVALID_SIGNATURE(5002, "Invalid payment signature", HttpStatus.BAD_REQUEST);

    private final int code;
    private final String message;
    private final HttpStatus status;

    ErrorCode(int code, String message, HttpStatus status) {
        this.code = code;
        this.message = message;
        this.status = status;
    }

    public int code() {
        return code;
    }

    public String message() {
        return message;
    }

    public HttpStatus status() {
        return status;
    }
}
