package com.nsantos.httpfileserver.exceptions;

import lombok.Getter;

/**
 * An exception designed to be transformed in an HTTP Response, contains a status code to be used in the response
 */
public class WebServerException extends Exception {
    @Getter
    private final int statusCode;

    public WebServerException(String message, int statusCode, Throwable exception) {
        super(message, exception);
        this.statusCode = statusCode;
    }

    public WebServerException(String message, int statusCode) {
        this(message, statusCode, null);
    }
}
