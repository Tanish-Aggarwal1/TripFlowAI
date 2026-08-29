package com.tripflow.backend.exception;

public class DuplicateEmailException extends RuntimeException {
    // Message intentionally omits the submitted email - echoing it back turns the 409 into a
    // direct "is this address registered here?" oracle (SCRUM M-6).
    public DuplicateEmailException(String email) {
        super("Email already registered");
    }
}