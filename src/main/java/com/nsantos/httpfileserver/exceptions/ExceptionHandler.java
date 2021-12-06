package com.nsantos.httpfileserver.exceptions;

import com.nsantos.httpfileserver.HttpResponses;
import org.apache.hc.core5.http.ContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;

public class ExceptionHandler {
    private static final Logger logger = LoggerFactory.getLogger(ExceptionHandler.class);

    public void handleException(Throwable t, OutputStream os) throws IOException {
        logger.warn("Request failed", t);
        if (t instanceof WebServerException ex) {
            HttpResponses.sendResponse(os, ex.getStatusCode(), new HashMap<>(), ex.getMessage(), ContentType.TEXT_PLAIN);
        } else if (t instanceof UnsupportedOperationException ex) {
            HttpResponses.sendResponse(os, 404, new HashMap<>(), "Operation not supported", ContentType.TEXT_PLAIN);
        } else {
            HttpResponses.sendResponse(os, 500, new HashMap<>());
        }
    }
}
