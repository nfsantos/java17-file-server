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

    private final FileServerHandler fileServerHandler;
    private final Config config;
    private TPCServer tpcServer;

    public int getPort() {
        return this.tpcServer.getPort();
    }

    public HttpFileServerMain(FileServerHandler fileServerHandler, Config config) {
        this.fileServerHandler = fileServerHandler;
        this.config = config;
    }

    private void createServer() throws IOException {
        this.tpcServer = new TPCServer(this.fileServerHandler, config);
        this.tpcServer.start();
    }

    void start() throws IOException {
        logger.info("Starting HTTP Server");
        createServer();
    }

    void join() throws ExecutionException, InterruptedException {
        this.tpcServer.join();
    }

    public void stop() throws IOException {
        logger.info("Stopping HTTP Server");
        tpcServer.stop();
    }

    public static void main(String[] args) throws IOException, ExecutionException, InterruptedException {
        Config conf = ConfigFactory.load();
        FileServer fileServer = new FileServerImpl(conf);
        ExceptionHandler exceptionHandler = new ExceptionHandler();
        FileServerHandler fsc = new FileServerHandler(fileServer, exceptionHandler, conf);
        var server = new HttpFileServerMain(fsc, conf);
        server.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                logger.info("Shutting down");
                server.stop();
            } catch (Throwable t) {
                logger.warn("Error closing TPCServer", t);
            }
        }));
        server.join();
    }
}
