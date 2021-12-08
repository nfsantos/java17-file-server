package com.nsantos.httpfileserver;

import com.nsantos.httpfileserver.exceptions.ExceptionHandler;
import com.nsantos.httpfileserver.fileserver.FileServer;
import com.typesafe.config.Config;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Locale;
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
        logger.debug("Processing requests from {}", socket.getRemoteSocketAddress());
        var originalName = Thread.currentThread().getName();
        Thread.currentThread().setName(originalName + "-" + socket.getRemoteSocketAddress());
        /*  We need to handle multiple requests in this connection, so we need to handle the input and output streams carefully.
         * 1) We should not close them while processing a request. So if we wrap them in another stream for convenience,
         *   do not close that stream
         * 2) We must be carefully in
         *
         * Because this server does not support requests with bodies, I'm making some simplifications in this implementation.
         * I'm wrapping the input stream in a BufferedReader, so we can use the readline methods to easily parse the header
         * section using the BufferedReader.readLine(). This would not work if the requests had a body, because the body
         * could potentially be binary, so we cannot read it with a reader, it would have to be read directly with the
         * input stream.
         */
        try (var reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             var bos = new BufferedOutputStream(socket.getOutputStream())) {
            var endOfInput = false;
            while (!endOfInput) {
                endOfInput = handleOneRequest(reader, bos);
            }
        } catch (SocketException ex) {
            if (!closed.get()) {
                logger.warn("Exception reading from socket", ex);
            }
        } catch (IOException ex) {
            logger.warn("Exception reading from socket", ex);
        } finally {
            Thread.currentThread().setName(originalName);
            if (!socket.isClosed()) {
                try {
                    logger.info("Closing socket {}", socket);
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
        // https://datatracker.ietf.org/doc/html/rfc2616#section-4.1
        // https://datatracker.ietf.org/doc/html/rfc7230#section-3
        logger.debug("Waiting for HTTP request");
        var headerLine = reader.readLine();
        // Ignore empty lines, as per spec
        while (headerLine != null && headerLine.isBlank()) {
            headerLine = reader.readLine();
        }
        if (headerLine == null) {
            // Reached end of input, client closed connection
            return true;
        }
        var parts = headerLine.split(" ");
        var method = parts[0].trim();
        var uri = URI.create(parts[1].trim());
        var httpVersion = parts[2].trim();

        // Read the headers
        // https://datatracker.ietf.org/doc/html/rfc2616#section-4.2
        var headers = new HashMap<String, String>();
        var line = reader.readLine();
        while (true) {
            line = reader.readLine();
            if (line == null) {
                // The request was partially read, throw an exception
                throw new IOException("Error reading request, unexpected end of input");
            }
            // The headers section end with the first blank line
            if (line.isBlank()) {
                break;
            }
            // Parse an HTTP header line
            var colonIndex = line.indexOf(':');
            if (colonIndex < 0) {
                throw new IOException("Malformed HTTP Request, invalid header line: %s".formatted(line));
            }
            var headerName = line.substring(0, colonIndex);
            /* TODO: The value of an header can contain a series of values, separated by a comma, so we should break
             *   them apart in individual elements and return a <String, List<String>> to represent the header.
             *   Question: can we simply split the header value using comma as separator or are there quoting rules to
             *   allow including a , as part of a value? e.g. is "MyHeader: "1, 2", "one, two" " parsed to two values,
             *   '1, 2' and 'one, two' or parsed as 4 values:  '"1' , '2"', '"one', 'two"' ?
             */
            var headerValue = line.substring(colonIndex + 1).trim();
            headers.put(headerName, headerValue);
        }
        var request = new HttpRequest(method, uri, httpVersion, headers);
        logger.debug("Received request: {}", request);
        handle(request, os);
        return false;
    }

    public void handle(HttpRequest request, OutputStream os) throws IOException {
        try {
            var method = request.method().toUpperCase(Locale.ROOT);
            logger.info("Request URI: {}, Method: {}", request.uri(), method);
            switch (method) {
                case "GET" -> handleGet(request, os);
                case "HEAD" -> handleHead(request, os);
                default -> throw new UnsupportedOperationException();
            }
        } catch (Throwable t) {
            exceptionHandler.handleException(t, os);
        }
    }

    private void handleHead(HttpRequest request, OutputStream os) {
        throw new UnsupportedOperationException();
    }

    private void handleGet(HttpRequest request, OutputStream os) throws IOException {
        // Remove the starting /
        var uri = request.uri().toString().substring(1);
        // Support UTF_8 characters
        var requestPath = URLDecoder.decode(uri, StandardCharsets.UTF_8);
        try {
            if (fileServer.isFile(requestPath)) {
                // Send the file
                var requestedFile = fileServer.getFile(requestPath);
                sendResponse(os, HttpStatus.SC_OK, new HashMap<>(), requestedFile);

            } else if (fileServer.isDirectory(requestPath)) {
                // Send a directory listing
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
