package com.nsantos.httpfileserver;

import com.typesafe.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.BindException;
import java.net.ServerSocket;
import java.net.SocketException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.nsantos.httpfileserver.ThreadUtils.newBoundedCachedThreadPool;

/**
 * A runnable that accepts new connections in a TCP Socket and dispatches them to a thread pool.
 */
class TCPConnectionAcceptor implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(TCPServer.class);

    // Enforces lifecycle, prevents callers from using this instance after being closed.
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private final ConnectionHandlerFactory connectionHandler;
    private final Config config;

    private final ServerSocket ss;
    // Thread pool used to process incoming connections.
    private final ExecutorService connectionHandlerThreadPool;
    // List of active connection handlers. This is used to gracefully close all the connection handlers by calling
    // close on the underlying socket. We could keep only the futures returned when we submit the new handler to the
    // thread pool, but calling Future.cancel() is not very reliable, in particular it will not immediately interrupt
    // a thread that is blocked on socket I/O, while calling socket.close() always aborts the operation immediately.
    private final Set<ConnectionHandler> activeHandlers = Collections.newSetFromMap(new IdentityHashMap<>());
    // Required because activeHandlers is accessed by the thread accepting new connections (when shutting down)
    // and by the threads running the connection handlers.
    private final Object activeHandlersLock = new Object();

    /**
     * @param connectionHandler
     * @param config
     * @throws IOException
     */
    public TCPConnectionAcceptor(ConnectionHandlerFactory connectionHandler, Config config) throws IOException {
        this.connectionHandler = connectionHandler;
        this.config = config;
        // Creates the thread pool for incoming connections
        var threadPoolSize = config.getInt(Constants.WEBSERVER_THREAD_POOL_SIZE);
        this.connectionHandlerThreadPool = newBoundedCachedThreadPool(1, threadPoolSize, "http-handler");
        this.ss = createAndBindServerSocket();
    }

    private ServerSocket createAndBindServerSocket() throws IOException {
        var port = config.getInt(Constants.WEBSERVER_PORT);
        if (port < 0 || port > 65_535) {
            throw new IllegalArgumentException("Invalid port number: %d, must be between 0 and 65535".formatted(port));
        }
        if (port == 0) { // Choose a random port within the pre-defined random port range
            var r = new Random();
            for (int i = 1; i <= Constants.RANDOM_PORT_MAX_ATTEMPTS; i++) {
                var randomPort = Constants.RANDOM_PORT_RANGE_LOWER + r.nextInt(Constants.RANDOM_PORT_RANGE_UPPER - Constants.RANDOM_PORT_RANGE_LOWER + 1);
                try {
                    logger.debug("Trying to bind at port {}, Attempt {}", randomPort, i);
                    return new ServerSocket(randomPort);
                } catch (BindException ex) {
                    logger.debug("Failed to bind at port {}. Error: {}", randomPort, ex.toString());
                }
            }
            throw new BindException("Could not find an available random port to bind to after %d attempts".formatted(Constants.RANDOM_PORT_MAX_ATTEMPTS));
        } else {
            return new ServerSocket(port);
        }
    }

    public int getLocalPort() {
        return ss.getLocalPort();
    }

    @Override
    public void run() {
        ensureOpen();
        logger.info("Accepting new connections at port {}", ss.getLocalPort());
        try {
            while (!closed.get()) {
                var socket = ss.accept();
                logger.info("Received new connection from: {}", socket.getRemoteSocketAddress());
                // Dispatch the new connection to the thread pool
                connectionHandlerThreadPool.submit(() -> {
                    var handler = connectionHandler.createHandler(socket);
                    synchronized (activeHandlersLock) {
                        activeHandlers.add(handler);
                    }
                    logger.trace("Connection handler starting: {}", handler);
                    try {
                        handler.handleRequests();
                    } catch (Throwable t) {
                        logger.warn("Error", t);
                    } finally {
                        logger.trace("Handler terminating: {}", handler);
                        synchronized (activeHandlersLock) {
                            activeHandlers.remove(handler);
                        }
                    }
                });
            }
        } catch (SocketException e) {
            // If someone called stop(), this exception is expected so do not log it
            if (!closed.get()) {
                logger.warn("Error accepting new connections, shutting down: {}", e.toString());
            }
        } catch (IOException e) {
            logger.warn("Error accepting new connections, shutting down: {}", e.toString());
        } finally {
            // Close the socket only if we are terminating because of an error. If someone called stop(), do call it again
            if (!closed.get()) {
                try {
                    stop();
                } catch (Throwable t) {
                    logger.debug("Suppressed error closing socket", t);
                }
            }
        }
    }

    /**
     * Stops the task accepting new connections and all active connection handlers.
     *
     * @throws IOException
     * @throws InterruptedException
     */
    public void stop() throws IOException, InterruptedException {
        if (closed.compareAndSet(false, true)) {
            // This is called from a different thread than the one executing the run method.
            logger.debug("Closing TCP connection acceptor");
            ss.close();
            // Stop all connection handlers
            synchronized (activeHandlersLock) {
                for (ConnectionHandler activeHandler : activeHandlers) {
                    activeHandler.stop();
                }
            }
            connectionHandlerThreadPool.shutdown();
            // All tasks on the thread pool should complete gracefully
            if (!connectionHandlerThreadPool.awaitTermination(2, TimeUnit.SECONDS)) {
                // If they have not completed gracefully, try to stop them forcibly (with interruptions).
                connectionHandlerThreadPool.shutdownNow();
            }
        } else {
            logger.warn("Already closed");
        }
    }

    private void ensureOpen() {
        if (closed.get()) {
            throw new IllegalStateException("Already closed");
        }
    }
}
