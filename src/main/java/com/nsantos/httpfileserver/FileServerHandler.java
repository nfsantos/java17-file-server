package com.nsantos.httpfileserver;

import com.nsantos.httpfileserver.exceptions.ExceptionHandler;
import com.nsantos.httpfileserver.fileserver.FileServer;
import com.typesafe.config.Config;
import lombok.Getter;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.nsantos.httpfileserver.HttpResponses.sendResponse;


class FileServerHandler implements Consumer<Socket> {
    private static final Logger logger = LoggerFactory.getLogger(FileServerHandler.class);

    private final FileServer fileServer;
    private final ExceptionHandler exceptionHandler;

    @Getter
    private final String rootPath = "/file-server";

    public FileServerHandler(FileServer fileServer, ExceptionHandler exceptionHandler, Config conf) {
        this.fileServer = fileServer;
        this.exceptionHandler = exceptionHandler;
    }

    @Override
    public void accept(Socket socket) {
        logger.debug("Request from {}", socket.getRemoteSocketAddress());
        try (var reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             var os = socket.getOutputStream()) {
            while (true) {
                // TODO: Graceful return at end of stream?
                handleOneRequest(reader, os);
            }
        } catch (Throwable t) {
            logger.warn("Failed with exception", t);
        } finally {
            try {
                logger.info("Closing socket");
                socket.close();
            } catch (IOException e) {
                logger.warn("Suppressing error closing socket: {}", e.toString());
            }
        }
    }

    private void handleOneRequest(BufferedReader reader, OutputStream os) throws IOException {
        logger.debug("Waiting for HTTP request");
        var requestLine = RequestLine.createHeader(reader.readLine());
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
    }

    public void handle(RequestLine requestLine, Map<String, List<String>> headers, OutputStream os) throws IOException {
        try {
            var method = requestLine.method().toUpperCase(Locale.ROOT);
            logger.info("Request URI: {}, Method: {}", requestLine.uri(), method);
            if (!requestLine.uri().getPath().startsWith(rootPath)) {
                HttpResponses.sendResponse(os, HttpStatus.SC_NOT_FOUND, new HashMap<>());
            } else {
                switch (method) {
                    case "GET" -> handleGet(requestLine, headers, os);
                    case "HEAD" -> handleHead(requestLine, headers, os);
                    default -> throw new UnsupportedOperationException();
                }
            }
        } catch (Throwable t) {
            exceptionHandler.handleException(t, os);
        }
    }

    private void handleHead(RequestLine requestLine, Map<String, List<String>> headers, OutputStream os) {
        throw new UnsupportedOperationException();
    }

    private void handleGet(RequestLine requestLine, Map<String, List<String>> requestHeaders, OutputStream os) throws IOException {
        var decoded = URLDecoder.decode(requestLine.uri().toString(), StandardCharsets.UTF_8);
        logger.info("URI: {}, Decoded: {}", requestLine.uri(), decoded);
        var requestPath = Path.of(decoded);
        var rootPath = Path.of(this.rootPath);
        var relativePath = rootPath.relativize(requestPath).toString();

        logger.debug("Request uri: {}, relative: {}", requestPath, relativePath);

        try {
            if (fileServer.isFile(relativePath)) {
                var requestedFile = fileServer.getFile(relativePath);
                // TODO 2: support range requests

                sendResponse(os, HttpStatus.SC_OK, new HashMap<>(), requestedFile);

            } else if (fileServer.isDirectory(relativePath)) {
                var fileList = fileServer.getDirectoryListing(relativePath).map(p -> {
                    var fileName = p.getFileName().toString();
                    if (Files.isDirectory(p)) {
                        fileName += "/";
                    }
                    return directoryListingLineTemplate.formatted(fileName, fileName);
                }).collect(Collectors.joining("\n"));
                var body = directoryListingTemplate.formatted(relativePath, fileList);
                HttpResponses.sendResponse(os, HttpStatus.SC_OK, new HashMap<>(), body, ContentType.TEXT_HTML.withCharset(StandardCharsets.UTF_8));

            } else {
                logger.debug("File not found {}", relativePath);
                HttpResponses.sendResponse(os, HttpStatus.SC_NOT_FOUND, new HashMap<>());
            }
        } catch (AccessDeniedException ex) {
            logger.debug("Access denied: {}", ex.toString());
            HttpResponses.sendResponse(os, HttpStatus.SC_FORBIDDEN, new HashMap<>());
        }
    }

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
}
