package com.nsantos.httpfileserver;

import com.typesafe.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.nsantos.httpfileserver.ThreadUtils.newSingleThreadExecutor;

public class TCPServer {
    private static final Logger logger = LoggerFactory.getLogger(TCPServer.class);

    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final FileServerHandlerFactory connectionHandler;
    private final Config config;
    private ExecutorService acceptorThread;
    private Future<?> acceptorTask;
    private TCPConnectionAcceptor tcpConnectionAcceptor;

    /**
     * @param fshFactory Handler for received connections
     * @param config     Global configuration
     */
    public TCPServer(FileServerHandlerFactory fshFactory, Config config) {
        this.config = config;
        this.connectionHandler = fshFactory;
    }

    public int getPort() {
        return tcpConnectionAcceptor.getLocalPort();
    }

    public void start() throws IOException {
        logger.info("Starting HTTP Server");
        ensureNotClosed();
        this.acceptorThread = newSingleThreadExecutor("acceptor");
        this.tcpConnectionAcceptor = new TCPConnectionAcceptor(connectionHandler, config);
        logger.info("Bound HTTP Server to port {}", tcpConnectionAcceptor.getLocalPort());
        this.acceptorTask = acceptorThread.submit(tcpConnectionAcceptor);
    }

    public void join() throws ExecutionException, InterruptedException {
        ensureNotClosed();
        acceptorTask.get();
    }

    public void stop() throws IOException, InterruptedException {
        if (closed.compareAndSet(false, true)) {
            logger.info("Closing TPCServer");
            tcpConnectionAcceptor.stop();
            acceptorThread.shutdown();
            // The socket acceptor task should terminate gracefully in response to the server socket being closed.
            if (!acceptorThread.awaitTermination(5, TimeUnit.SECONDS)) {
                // It did not terminate gracefully, kill it.
                acceptorThread.shutdownNow();
            }
        } else {
            logger.warn("Already closed");
        }
    }

    /**
     * Raise an error if this object is called after being closed.
     */
    private void ensureNotClosed() {
        assert !closed.get();
    }
}

