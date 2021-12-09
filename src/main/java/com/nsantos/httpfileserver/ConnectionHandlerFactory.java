package com.nsantos.httpfileserver;

import com.nsantos.httpfileserver.exceptions.ExceptionHandler;
import com.nsantos.httpfileserver.fileserver.FileServer;
import com.typesafe.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.Socket;

/**
 * Creates new instances of connection handlers. This class holds the global configuration and dependencies required
 * by all connection handlers and given a newly connected Socket, will create a new handler to process requests
 * received from that socket.
 */
class ConnectionHandlerFactory {
    private static final Logger logger = LoggerFactory.getLogger(ConnectionHandlerFactory.class);

    private final FileServer fileServer;
    private final ExceptionHandler exceptionHandler;
    private final HttpResponseWriter httpResponseWriter;
    private final Config config;

    /**
     * @param fileServer         An instance of the underlying file server service, used to process requests for files
     * @param exceptionHandler   Handler for exceptions that occur while processing the requests.
     * @param httpResponseWriter Generates HTTP Responses
     * @param conf               Global configuration
     */
    public ConnectionHandlerFactory(FileServer fileServer, ExceptionHandler exceptionHandler, HttpResponseWriter httpResponseWriter, Config conf) {
        this.fileServer = fileServer;
        this.exceptionHandler = exceptionHandler;
        this.httpResponseWriter = httpResponseWriter;
        this.config = conf;

    }

    public ConnectionHandler createHandler(Socket socket) {
        return new ConnectionHandler(fileServer, exceptionHandler, httpResponseWriter, config, socket);
    }
}
