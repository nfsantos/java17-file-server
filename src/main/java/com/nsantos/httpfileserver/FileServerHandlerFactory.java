package com.nsantos.httpfileserver;

import com.nsantos.httpfileserver.exceptions.ExceptionHandler;
import com.nsantos.httpfileserver.fileserver.FileServer;
import com.typesafe.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.Socket;

class FileServerHandlerFactory {
    private static final Logger logger = LoggerFactory.getLogger(FileServerHandlerFactory.class);

    private final FileServer fileServer;
    private final ExceptionHandler exceptionHandler;
    private final Config config;

    public FileServerHandlerFactory(FileServer fileServer, ExceptionHandler exceptionHandler, Config conf) {
        this.fileServer = fileServer;
        this.exceptionHandler = exceptionHandler;
        this.config = conf;
    }

    public FileServerHandler createHandler(Socket socket) {
        return new FileServerHandler(fileServer, exceptionHandler, config, socket);
    }
}
