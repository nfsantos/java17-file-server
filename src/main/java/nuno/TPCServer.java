package nuno;

import com.typesafe.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.BindException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static nuno.Constants.*;
import static nuno.ThreadUtils.newBoundedCachedThreadPool;
import static nuno.ThreadUtils.newSingleThreadExecutor;

class TPCServer {
    private static final Logger logger = LoggerFactory.getLogger(TPCServer.class);

    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final Config config;
    private final ExecutorService threadPool;
    private final ExecutorService acceptorThread;
    private final Consumer<Socket> connectionHandler;
    private Future<?> acceptorTask;
    private ServerSocket ss;

    public TPCServer(Consumer<Socket> connectionHandler, Config config) {
        this.config = config;
        this.connectionHandler = connectionHandler;
        var threadPoolSize = config.getInt(WEBSERVER_THREAD_POOL_SIZE);
        this.threadPool = newBoundedCachedThreadPool(1, threadPoolSize, "http-handler");
        this.acceptorThread = newSingleThreadExecutor("acceptor");
    }

    public int getPort() {
        return ss.getLocalPort();
    }

    private void bind() throws IOException {
        var port = config.getInt(Constants.WEBSERVER_PORT);
        if (port == 0) {
            var r = new Random();
            var isBound = false;
            for (int i = 1; i <= RANDOM_PORT_MAX_ATTEMPTS && !isBound; i++) {
                var randomPort = RANDOM_PORT_RANGE_LOWER + r.nextInt(RANDOM_PORT_RANGE_UPPER - RANDOM_PORT_RANGE_LOWER);
                try {
                    logger.debug("Trying to bind at port {}, Attempt {}", randomPort, i);
                    ss = new ServerSocket(randomPort, 0);
                    isBound = true;
                } catch (BindException ex) {
                    logger.debug("Failed to bind at port {}. Error: {}", randomPort, ex.toString());
                }
            }
            if (!isBound) {
                throw new BindException("Could not find a port to bind");
            }
        } else {
            ss = new ServerSocket(port, 0);
        }
        logger.info("HTTP Server bound at: {}", ss.getLocalPort());
    }

    class ConnectionAcceptor implements Runnable {
        private final ServerSocket ss;

        public ConnectionAcceptor(ServerSocket ss) {
            this.ss = ss;
        }

        @Override
        public void run() {
            logger.info("Starting accepting connections");
            try {
                while (true) {
                    var socket = ss.accept();
                    logger.debug("Received new connection from: {}", socket.getRemoteSocketAddress());
                    threadPool.submit(() -> {
                        logger.info("Processing connection {}}", socket);
                        connectionHandler.accept(socket);
                    });
                }
            } catch (IOException e) {
                logger.debug("Shutting down socket: {}", e.toString());
            }
        }
    }

    public void start() throws IOException {
        bind();
        this.acceptorTask = acceptorThread.submit(new ConnectionAcceptor(ss));
    }

    public void join() throws ExecutionException, InterruptedException {
        acceptorTask.get();
    }

    public void stop() throws IOException {
        if (closed.compareAndSet(false, true)) {
            logger.debug("Closing TPCServer");
            ss.close();
            threadPool.shutdown();
        } else {
            logger.warn("Already closed");
        }
    }
}
