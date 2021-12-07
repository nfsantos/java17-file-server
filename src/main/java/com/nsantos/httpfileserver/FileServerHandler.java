package com.nsantos.httpfileserver;

import com.nsantos.httpfileserver.exceptions.ExceptionHandler;
import com.nsantos.httpfileserver.fileserver.FileServer;
import com.typesafe.config.Config;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static com.nsantos.httpfileserver.HttpResponses.sendResponse;

class FileServerHandler {
    private static final Logger logger = LoggerFactory.getLogger(FileServerHandler.class);

    private static final String directoryListingLineTemplate = "<li><a href=\"%s\">%s</a></li>";

    private static final String directoryListingTemplate = """
            <!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN" "http://www.w3.org/TR/html4/strict.dtd">
            <html>
            <head>
            <meta http-equiv="Content-Type" content="text/html; charset=utf-8">
            <title>Directory listing for %s</title>
            </head>
            <body>
            <h1>Directory listing for /</h1>
            <hr>
            <ul>
            %s
            </ul>
            <hr>
            </body>
            </html>
            """;

    private final AtomicBoolean closed = new AtomicBoolean(false);

    private FileServer fileServer;
    private ExceptionHandler exceptionHandler;
    private Config conf;
    private Socket socket;

    public FileServerHandler(FileServer fileServer, ExceptionHandler exceptionHandler, Config conf, Socket socket) {
        this.fileServer = fileServer;
        this.exceptionHandler = exceptionHandler;
        this.conf = conf;
        this.socket = socket;
    }

    public void start() {
        logger.debug("Request from {}", socket.getRemoteSocketAddress());
        try (var reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             var os = socket.getOutputStream()) {
            var endOfInput = false;
            while (!endOfInput) {
                endOfInput = handleOneRequest(reader, os);
            }
        } catch (SocketException ex) {
            if (!closed.get()) {
                logger.warn("Exception reading from socket", ex);
            }
        } catch (IOException ex) {
            logger.warn("Exception reading from socket", ex);
        } finally {
            if (!socket.isClosed()) {
                try {
                    logger.info("Closing socket");
                    socket.close();
                } catch (IOException e) {
                    logger.warn("Suppressing error closing socket: {}", e.toString());
                }
            }
        }
    }

    public void stop() throws IOException {
        if (closed.compareAndSet(false, true)) {
            logger.debug("Closing {}", socket);
            this.socket.close();
        } else {
            logger.warn("Already closed");
        }
    }

    /**
     * @param reader
     * @param os
     * @return true if it reached end of input, false if there may be more requests.
     * @throws IOException
     */
    private boolean handleOneRequest(BufferedReader reader, OutputStream os) throws IOException {
        logger.debug("Waiting for HTTP request");
        var headerLine = reader.readLine();
        if (headerLine == null) {
            return true;
        }
        var requestLine = RequestLine.createHeader(headerLine);
        var headers = new HashMap<String, List<String>>();
        var line = reader.readLine();
        while (true) {
            line = reader.readLine();
            if (line.isBlank()) {
                break;
            }
            var parts = line.split(": ");
            var values = List.of(parts[1].split(";"));
            logger.info(">> {} {}", parts[0], values);
            headers.put(parts[0], values);
        }
        handle(requestLine, headers, os);
        return false;
    }

    public void handle(RequestLine requestLine, Map<String, List<String>> headers, OutputStream os) throws IOException {
        try {
            var method = requestLine.method().toUpperCase(Locale.ROOT);
            logger.info("Request URI: {}, Method: {}", requestLine.uri(), method);
            switch (method) {
                case "GET" -> handleGet(requestLine, headers, os);
                case "HEAD" -> handleHead(requestLine, headers, os);
                default -> throw new UnsupportedOperationException();
            }
        } catch (Throwable t) {
            exceptionHandler.handleException(t, os);
        }
    }

    private void handleHead(RequestLine requestLine, Map<String, List<String>> headers, OutputStream os) {
        throw new UnsupportedOperationException();
    }

    private void handleGet(RequestLine requestLine, Map<String, List<String>> requestHeaders, OutputStream os) throws IOException {
        var requestPath = URLDecoder.decode(requestLine.uri().toString().substring(1), StandardCharsets.UTF_8);
        logger.info("URI: {}, Decoded: {}", requestLine.uri(), requestPath);
//        var requestPath = Path.of(decoded);

        logger.debug("Request uri: {}", requestPath);

        try {
            if (fileServer.isFile(requestPath)) {
                var requestedFile = fileServer.getFile(requestPath);
                // TODO 2: support range requests

                sendResponse(os, HttpStatus.SC_OK, new HashMap<>(), requestedFile);

            } else if (fileServer.isDirectory(requestPath)) {
                var fileList = fileServer.getDirectoryListing(requestPath).map(p -> {
                    var fileName = p.getFileName().toString();
                    if (Files.isDirectory(p)) {
                        fileName += "/";
                    }
                    return directoryListingLineTemplate.formatted(fileName, fileName);
                }).collect(Collectors.joining("\n"));
                var body = directoryListingTemplate.formatted(requestPath, fileList);
                HttpResponses.sendResponse(os, HttpStatus.SC_OK, new HashMap<>(), body, ContentType.TEXT_HTML.withCharset(StandardCharsets.UTF_8));

            } else {
                logger.debug("File not found {}", requestPath);
                HttpResponses.sendResponse(os, HttpStatus.SC_NOT_FOUND, new HashMap<>());
            }
        } catch (AccessDeniedException ex) {
            logger.debug("Access denied: {}", ex.toString());
            HttpResponses.sendResponse(os, HttpStatus.SC_FORBIDDEN, new HashMap<>());
        }
    }

    @Override
    public String toString() {
        return "FileServerHandler{" +
                "socket=" + socket +
                '}';
    }
}
