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

class TCPConnectionAcceptor implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(TCPServer.class);

    private final AtomicBoolean closed = new AtomicBoolean(false);

    private final ServerSocket ss;
    private final Set<FileServerHandler> activeHandlers = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Object activeHandlersLock = new Object();
    private final FileServerHandlerFactory connectionHandler;
    private final Config config;
    private final ExecutorService connectionHandlerThreadPool;

    public TCPConnectionAcceptor(FileServerHandlerFactory connectionHandler, Config config) throws IOException {
        this.connectionHandler = connectionHandler;
        this.config = config;
        var threadPoolSize = config.getInt(Constants.WEBSERVER_THREAD_POOL_SIZE);
        this.connectionHandlerThreadPool = newBoundedCachedThreadPool(1, threadPoolSize, "http-handler");
        this.ss = bind();
    }

    /**
     * Create and bind the server socket
     */
    private ServerSocket bind() throws IOException {
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

    @Override
    public void run() {
        ensureOpen();
        logger.info("Starting accepting connections");
        try {
            while (!closed.get()) {
                var socket = ss.accept();
                logger.info("Received new connection from: {}", socket.getRemoteSocketAddress());
                connectionHandlerThreadPool.submit(() -> {
                    var handler = connectionHandler.createHandler(socket);
                    logger.info("Adding handler: {}", handler);
                    synchronized (activeHandlersLock) {
                        activeHandlers.add(handler);
                    }
                    try {
                        handler.start();
                    } catch (Throwable t) {
                        logger.warn("Error", t);
                    } finally {
                        logger.info("Removing handler: {}", handler);
                        synchronized (activeHandlersLock) {
                            activeHandlers.remove(handler);
                        }
                    }
                });
            }
        } catch (SocketException e) {
            if (!closed.get()) {
                logger.warn("Error accepting new connections, shutting down: {}", e.toString());
            }
        } catch (IOException e) {
            logger.warn("Error accepting new connections, shutting down: {}", e.toString());
        }
    }

    private void ensureOpen() {
        assert !closed.get();
    }

    public void stop() throws IOException, InterruptedException {
        if (closed.compareAndSet(false, true)) {
            // This is called from a different thread than the one executing the run method.
            logger.debug("Closing TCP connection acceptor");
            ss.close();
            synchronized (activeHandlersLock) {
                for (FileServerHandler activeHandler : activeHandlers) {
                    activeHandler.stop();
                }
            }
            connectionHandlerThreadPool.shutdown();
            if (!connectionHandlerThreadPool.awaitTermination(2, TimeUnit.SECONDS)) {
                connectionHandlerThreadPool.shutdownNow();
            }
        } else {
            logger.warn("Already closed");
        }
    }

    public int getLocalPort() {
        return ss.getLocalPort();
    }
}
