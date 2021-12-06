package com.nsantos.httpfileserver.exceptions;

import lombok.Getter;

public class WebServerException extends Exception {
    @Getter
    private final int statusCode;

    public WebServerException(String message, int statusCode) {
        this(message, statusCode, null);
    }

    public WebServerException(String message, int statusCode, Throwable exception) {
        super(message, exception);
        this.statusCode = statusCode;
    }
}
