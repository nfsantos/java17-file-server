package com.nsantos.httpfileserver;

import com.nsantos.httpfileserver.exceptions.ExceptionHandler;
import com.nsantos.httpfileserver.fileserver.FileServer;
import com.nsantos.httpfileserver.fileserver.FileServerImpl;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

/**
 * Main entry point for the HTTP File Server. This class manages the life cycle of the server.
 */
public class HttpFileServerMain {
    private static final Logger logger = LoggerFactory.getLogger(HttpFileServerMain.class);

    private final ConnectionHandlerFactory fileServerHandler;
    private final Config config;
    private TCPServer tcpServer;

    public int getPort() {
        return this.tcpServer.getLocalPort();
    }

    /**
     * @param connectionHandlerFactory A factory of connection handlers
     * @param config                   The global configuration
     */
    public HttpFileServerMain(ConnectionHandlerFactory connectionHandlerFactory, Config config) {
        this.fileServerHandler = connectionHandlerFactory;
        this.config = config;
    }

    /**
     * Starts the server in a background thread. This method returns once the server is up and running.
     * Call join() to block until the server shutdowns (gracefully or with error)
     *
     * @throws IOException Thrown if startup fails.
     */
    void start() throws IOException {
        this.tcpServer = new TCPServer(this.fileServerHandler, config);
        this.tcpServer.start();
    }

    /**
     * Waits until this server terminate
     *
     * @throws ExecutionException   Thrown if the server failed with an exception.
     * @throws InterruptedException
     */
    void join() throws ExecutionException, InterruptedException {
        this.tcpServer.join();
    }

    /**
     * Stops this server.
     *
     * @throws IOException
     * @throws InterruptedException
     */
    public void stop() throws IOException, InterruptedException {
        logger.info("Stopping HTTP Server");
        tcpServer.stop();
    }

    public static void main(String[] args) throws IOException, ExecutionException, InterruptedException {
        // We should use a proper DI framework like Guice, which would facilitate management of dependencies and testing.
        // But for this simple example, we simulate DI by passing the dependencies explicitly in the constructors.
        Config conf = ConfigFactory.load();
        FileServer fileServer = new FileServerImpl(conf);
        HttpResponseWriter httpResponseWriter = new HttpResponseWriter(conf);
        ExceptionHandler exceptionHandler = new ExceptionHandler(httpResponseWriter);
        ConnectionHandlerFactory fsc = new ConnectionHandlerFactory(fileServer, exceptionHandler, httpResponseWriter, conf);
        var server = new HttpFileServerMain(fsc, conf);
        server.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                logger.info("Shutting down");
                server.stop();
            } catch (Throwable t) {
                logger.warn("Error closing TCPServer", t);
            }
        }));
        server.join();
    }
}
