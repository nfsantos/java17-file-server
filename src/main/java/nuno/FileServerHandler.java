package nuno;

import com.typesafe.config.Config;
import lombok.Getter;
import nuno.exceptions.ExceptionHandler;
import nuno.fileserver.FileServer;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.net.URLConnection;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static nuno.HttpResponses.sendResponse;
import static nuno.HttpConstants.HTTP_APPLICATION_OCTET_STREAM;


class FileServerHandler implements Consumer<Socket> {
    private static final Logger logger = LoggerFactory.getLogger(FileServerHandler.class);

    private final FileServer fileServer;
    private final ExceptionHandler exceptionHandler;

    @Getter
    private String rootPath = "/file-server";

    public FileServerHandler(FileServer fileServer, ExceptionHandler exceptionHandler, Config conf) {
        this.fileServer = fileServer;
        this.exceptionHandler = exceptionHandler;
    }

    @Override
    public void accept(Socket socket) {
        logger.debug("Request from {}", socket.getRemoteSocketAddress());
        try {
            handleOneRequest(socket);
        } catch (Throwable t) {
            logger.warn("Failed with exception", t);
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                logger.warn("Suppressing error closing socket: {}", e.toString());
            }
        }
    }

    private void handleOneRequest(Socket socket) throws IOException {
        try (var reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
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
            handle(requestLine, headers, socket);
        }
    }

    public void handle(RequestLine requestLine, Map<String, List<String>> headers, Socket socket) throws IOException {
        try {
            var method = requestLine.method().toUpperCase(Locale.ROOT);
            logger.info("Request URI: {}, Method: {}, Headers: {}", requestLine.uri(), method);
            if (!requestLine.uri().getPath().startsWith(rootPath)) {
                HttpResponses.sendResponse(socket, HttpStatus.SC_NOT_FOUND, new HashMap<>());
            } else {
                switch (method) {
                    case "GET" -> handleGet(requestLine, headers, socket);
                    case "HEAD" -> new UnsupportedOperationException();
                    default -> throw new UnsupportedOperationException();
                }
            }
        } catch (Throwable t) {
            exceptionHandler.handleException(t, socket);
        }
    }

    private void handleGet(RequestLine requestLine, Map<String, List<String>> requestHeaders, Socket socket) throws IOException {
        var requestPath = Path.of(requestLine.uri().toString());
        var rootPath = Path.of(this.rootPath);
        var relativePath = rootPath.relativize(requestPath).toString();

        logger.debug("Request uri: {}, relative: {}", requestPath, relativePath);

        try {
            if (fileServer.isFile(relativePath)) {
                var requestedFile = fileServer.getFile(relativePath);
                // TODO 2: support range requests
                var contentType = URLConnection.guessContentTypeFromName(requestedFile.toString());
                if (contentType == null) {
                    contentType = HTTP_APPLICATION_OCTET_STREAM;
                }
                logger.info("Response: {}, content type: {} ", requestedFile, contentType);
                var headers = new HashMap<String, String>();
                headers.put(HttpHeaders.CONTENT_TYPE, contentType);
                logger.info("Response headers: {}", headers.entrySet());

                sendResponse(socket, HttpStatus.SC_OK, headers, requestedFile);

            } else if (fileServer.isDirectory(relativePath)) {
                var fileList = fileServer.getDirectoryListing(relativePath).map(p -> {
                    var fileName = p.getFileName().toString();
                    if (Files.isDirectory(p)) {
                        fileName += "/";
                    }
                    return directoryListingLineTemplate.formatted(fileName, fileName);
                }).collect(Collectors.joining());
                var body = directoryListingTemplate.formatted(relativePath, fileList);
                HttpResponses.sendResponse(socket, HttpStatus.SC_OK, new HashMap<>(), body);

            } else {
                logger.debug("File not found {}", relativePath);
                HttpResponses.sendResponse(socket, HttpStatus.SC_NOT_FOUND, new HashMap<>());
            }
        } catch (AccessDeniedException ex) {
            logger.debug("Access denied: {}", ex.toString());
            HttpResponses.sendResponse(socket, HttpStatus.SC_FORBIDDEN, new HashMap<>());
        }
    }

    private static String directoryListingLineTemplate = "<li><a href=\"%s\">%s</a></li>";

    private static String directoryListingTemplate = """
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
