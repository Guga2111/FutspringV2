package com.futspring.backend.shared.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.Map;

@Getter
public class AppException extends RuntimeException {

    private final HttpStatus status;
    // Field errors answered like a validation 400 ({errors: {field: message}}), so the client shows them on the field
    private final Map<String, String> fieldErrors;

    public AppException(HttpStatus status, String message) {
        super(message);
        this.status = status;
        this.fieldErrors = Map.of();
    }

    private AppException(String field, String message) {
        super(message);
        this.status = HttpStatus.BAD_REQUEST;
        this.fieldErrors = Map.of(field, message);
    }

    /** A 400 on one field of the request body, e.g. a wrong current password. */
    public static AppException field(String field, String message) {
        return new AppException(field, message);
    }
}
