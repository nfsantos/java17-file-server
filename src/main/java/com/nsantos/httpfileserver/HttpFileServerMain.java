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

public class HttpFileServerMain {
    private static final Logger logger = LoggerFactory.getLogger(HttpFileServerMain.class);

    private final FileServerHandlerFactory fileServerHandler;
    private final Config config;
    private TCPServer tcpServer;

    public int getPort() {
        return this.tcpServer.getPort();
    }

    public HttpFileServerMain(FileServerHandlerFactory fileServerHandler, Config config) {
        this.fileServerHandler = fileServerHandler;
        this.config = config;
    }

    private void createServer() throws IOException {
        this.tcpServer = new TCPServer(this.fileServerHandler, config);
        this.tcpServer.start();
    }

    void start() throws IOException {
        createServer();
    }

    void join() throws ExecutionException, InterruptedException {
        this.tcpServer.join();
    }

    public void stop() throws IOException, InterruptedException {
        logger.info("Stopping HTTP Server");
        tcpServer.stop();
    }

    public static void main(String[] args) throws IOException, ExecutionException, InterruptedException {
        Config conf = ConfigFactory.load();
        FileServer fileServer = new FileServerImpl(conf);
        ExceptionHandler exceptionHandler = new ExceptionHandler();
        FileServerHandlerFactory fsc = new FileServerHandlerFactory(fileServer, exceptionHandler, conf);
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
