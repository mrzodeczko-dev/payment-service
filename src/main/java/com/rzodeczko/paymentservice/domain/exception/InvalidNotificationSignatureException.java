package com.rzodeczko.paymentservice.domain.exception;

/**
 * Signals that an incoming payment notification failed signature verification.
 *
 * <p>This exception is thrown when the message authenticity cannot be trusted and
 * processing must stop immediately.</p>
 */
public class InvalidNotificationSignatureException extends RuntimeException {
    public InvalidNotificationSignatureException() {
        super("Invalid notification signature");
    }
}
