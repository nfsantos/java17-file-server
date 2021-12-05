package nuno.exceptions;

import nuno.HttpResponses;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.Socket;
import java.util.HashMap;

public class ExceptionHandler {
    private static final Logger logger = LoggerFactory.getLogger(ExceptionHandler.class);

    public void handleException(Throwable t, Socket socket) throws IOException {
        logger.warn("Request failed", t);
        if (t instanceof WebServerException ex) {
            HttpResponses.sendResponse(socket, ex.getStatusCode(), new HashMap<>(), ex.getMessage());
        } else if (t instanceof UnsupportedOperationException ex) {
            HttpResponses.sendResponse(socket, 404, new HashMap<>(), "Operation not supported");
        } else {
            HttpResponses.sendResponse(socket, 500, new HashMap<>(), "");
        }
    }
}
