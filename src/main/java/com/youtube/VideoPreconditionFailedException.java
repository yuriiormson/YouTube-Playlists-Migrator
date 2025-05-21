package com.youtube;

public class VideoPreconditionFailedException extends Exception {
    public VideoPreconditionFailedException(String message) {
        super(message);
    }

    public VideoPreconditionFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}