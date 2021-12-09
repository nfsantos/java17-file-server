package com.nsantos.httpfileserver.exceptions;

import com.nsantos.httpfileserver.HttpResponseWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

import static com.nsantos.httpfileserver.HttpConstants.TEXT_PLAIN_UTF8;

/**
 * Generates HTTP Responses from exceptions (inspired in the JAX-RS ExceptionMapper).
 */
public class ExceptionHandler {
    private static final Logger logger = LoggerFactory.getLogger(ExceptionHandler.class);
    private final HttpResponseWriter httpResponseWriter;

    public ExceptionHandler(HttpResponseWriter httpResponseWriter) {
        this.httpResponseWriter = httpResponseWriter;
    }

    /**
     * Produces an HTTP Response from an exception.
     *
     * @param t
     * @param os The OutputStream where to write the response
     * @throws IOException
     */
    public void handleException(Throwable t, OutputStream os) throws IOException {
        logger.warn("Request failed", t);
        if (t instanceof WebServerException ex) {
            httpResponseWriter.sendResponse(os, ex.getStatusCode(), new HashMap<>(),
                    ex.getMessage().getBytes(StandardCharsets.UTF_8), TEXT_PLAIN_UTF8);

        } else if (t instanceof UnsupportedOperationException) {
            httpResponseWriter.sendResponse(os, 404, new HashMap<>(),
                    "Operation not supported".getBytes(StandardCharsets.UTF_8), TEXT_PLAIN_UTF8);

        } else {
            httpResponseWriter.sendResponse(os, 500, new HashMap<>());
        }
    }
}
