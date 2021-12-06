//package nuno.jdk;
//
//import com.sun.net.httpserver.HttpExchange;
//import com.sun.net.httpserver.HttpHandler;
//import com.typesafe.config.Config;
//import lombok.Getter;
//import nuno.ExceptionHandler;
//import nuno.Helpers;
//import com.nsantos.simplehttpserver.FileServer;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//import java.io.IOException;
//import java.net.URLConnection;
//import java.nio.file.Files;
//import java.nio.file.Path;
//import java.util.Locale;
//import java.util.Optional;
//
//import static nuno.HttpConstants.HTTP_APPLICATION_OCTET_STREAM;
//import static nuno.HttpConstants.HTTP_CONTENT_TYPE;
//
//class JavaFileServerHandler implements HttpHandler {
//    private static final Logger logger = LoggerFactory.getLogger(HelloWorldHandler.class);
//
//    private final FileServer fileServer;
//    private final ExceptionHandler exceptionHandler;
//
//    @Getter
//    private String rootPath = "/file-server";
//
//    public JavaFileServerHandler(FileServer fileServer, ExceptionHandler exceptionHandler, Config conf) {
//        this.fileServer = fileServer;
//        this.exceptionHandler = exceptionHandler;
//    }
//
//    @Override
//    public void handle(HttpExchange exchange) throws IOException {
//        try {
//            var method = exchange.getRequestMethod().toUpperCase(Locale.ROOT);
//            logger.info("Request URI: {}, Method: {}, Headers: {}", exchange.getRequestURI(), method, exchange.getResponseHeaders().entrySet());
//            switch (method) {
//                case "GET" -> handleGet(exchange);
//                case "POST" -> throw new UnsupportedOperationException();
//                case "DELETE" -> handleDelete(exchange);
//                default -> throw new UnsupportedOperationException();
//            }
//        } catch (Throwable t) {
//            exceptionHandler.handleException(t, exchange);
//        }
//    }
//
//    private void handleDelete(HttpExchange exchange) {
//        throw new UnsupportedOperationException();
//    }
//
//    private void handleGet(HttpExchange exchange) throws IOException {
//        var requestPath = Path.of(exchange.getRequestURI().getPath());
//        var rootPath = Path.of(this.rootPath);
//        var relativePath = rootPath.relativize(requestPath).toString();
//
//        logger.info("Request uri: {}, relative: {}", requestPath, relativePath);
//
//        if (fileServer.isFile(relativePath)) {
//            var value = fileServer.getFile(relativePath);
//            // TODO 2: support range requests
//            var contentType = URLConnection.guessContentTypeFromName(value.toString());
//            if (contentType == null) {
//                contentType = HTTP_APPLICATION_OCTET_STREAM;
//            }
//            logger.info("Response: {}, content type: {} ", value, contentType);
//            var bodySize = Files.size(value);
//            var headers = exchange.getResponseHeaders();
//            headers.set(HTTP_CONTENT_TYPE, contentType);
//            logger.info("Response headers: {}", headers.entrySet());
//            exchange.sendResponseHeaders(200, bodySize);
//            try (var os = exchange.getResponseBody()) {
//                Files.copy(value, os);
//            }
//        } else if (fileServer.isDirectory(relativePath)) {
//            throw new UnsupportedOperationException("Directory listings not yet supported");
//        } else {
//            logger.debug("File not found {}", relativePath);
//            Helpers.sendResponse(exchange, 404, Optional.empty());
//        }
//    }
//}
