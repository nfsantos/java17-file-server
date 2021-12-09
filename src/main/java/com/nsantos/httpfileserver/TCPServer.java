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

/**
 * Listens for new TCP Connections and dispatches them to a new connection handler.
 */
public class TCPServer {
    // This class only handles the lifecycle of an instance of TCPConnectionAcceptor, that is, it starts it in a
    // background task and stops when required
    private static final Logger logger = LoggerFactory.getLogger(TCPServer.class);
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private final ConnectionHandlerFactory connectionHandlerFactory;
    private final Config config;

    // Thread pool (single threaded) used to run the task that accepts new connections
    private ExecutorService acceptorThread;
    // The task to accept new connections.
    private Future<?> acceptorTask;
    // The object the implements the logic of accepting new connections.
    private TCPConnectionAcceptor tcpConnectionAcceptor;

    /**
     * @param connectionHandlerFactory Handler for received connections
     * @param config                   Global configuration
     */
    public TCPServer(ConnectionHandlerFactory connectionHandlerFactory, Config config) {
        this.connectionHandlerFactory = connectionHandlerFactory;
        this.config = config;
    }

    public int getLocalPort() {
        return tcpConnectionAcceptor.getLocalPort();
    }

    /**
     * Starts a new task in a background thread to accept new TCP connections.
     * This method returns once the task is submitted to a thread pool.
     *
     * @throws IOException
     */
    public void start() throws IOException {
        ensureOpen();
        if (this.acceptorThread != null) {
            throw new IllegalStateException("Already started");
        }
        logger.info("Starting HTTP Server");
        this.acceptorThread = newSingleThreadExecutor("acceptor");
        this.tcpConnectionAcceptor = new TCPConnectionAcceptor(connectionHandlerFactory, config);
        logger.info("Bound HTTP Server to port {}", tcpConnectionAcceptor.getLocalPort());
        this.acceptorTask = acceptorThread.submit(tcpConnectionAcceptor);
    }

    /**
     * Blocks until the terminates
     *
     * @throws ExecutionException
     * @throws InterruptedException
     */
    public void join() throws ExecutionException, InterruptedException {
        ensureOpen();
        acceptorTask.get();
    }

    /**
     * Stops the server, closes the local server socket and all active connections.
     *
     * @throws IOException
     * @throws InterruptedException
     */
    public void stop() throws IOException, InterruptedException {
        if (closed.compareAndSet(false, true)) {
            logger.info("Closing TPCServer");
            tcpConnectionAcceptor.stop();
            acceptorThread.shutdown();
            // The socket acceptor task should terminate gracefully in response to the server socket being closed.
            if (!acceptorThread.awaitTermination(5, TimeUnit.SECONDS)) {
                // It did not terminate gracefully, interrupt it.
                acceptorThread.shutdownNow();
            }
        } else {
            logger.warn("Already closed");
        }
    }

    /**
     * Raise an error if this object is called after being closed.
     */
    private void ensureOpen() {
        if (closed.get()) {
            throw new IllegalStateException("Already closed");
        }
    }
}
