//package nuno.jdk;
//
//import com.sun.net.httpserver.HttpExchange;
//import com.sun.net.httpserver.HttpHandler;
//import com.sun.net.httpserver.HttpServer;
//import com.typesafe.config.Config;
//import com.typesafe.config.ConfigFactory;
//import lombok.Getter;
//import nuno.ExceptionHandler;
//import nuno.Helpers;
//import com.nsantos.simplehttpserver.FileServer;
//import com.nsantos.simplehttpserver.FileServerImpl;
//import org.apache.commons.io.IOUtils;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//import java.io.IOException;
//import java.net.BindException;
//import java.net.InetSocketAddress;
//import java.nio.charset.StandardCharsets;
//import java.util.Optional;
//import java.util.Random;
//
//import static nuno.Constants.*;
//
//class HelloWorldHandler implements HttpHandler {
//    private static final Logger logger = LoggerFactory.getLogger(HelloWorldHandler.class);
//
//    @Override
//    public void handle(HttpExchange exchange) throws IOException {
//        var is = exchange.getRequestBody();
//        var body = IOUtils.toString(is, StandardCharsets.UTF_8);
//        logger.info("Received request with body: {}", body);
//        var responseBody = "Hello world\n";
//        Helpers.sendResponse(exchange, 200, Optional.of(responseBody));
//    }
//}
//
//public class HelloWorldWebServerMain {
//    private static final Logger logger = LoggerFactory.getLogger(HelloWorldWebServerMain.class);
//
//    private final JavaFileServerHandler fileServerHandler;
//    private final Config config;
//    private HttpServer httpServer = null;
//
//    @Getter
//    private int port;
//
//    public HelloWorldWebServerMain(JavaFileServerHandler fileServerHandler, Config config) throws IOException {
//        this.fileServerHandler = fileServerHandler;
//        this.config = config;
//    }
//
//    private void createServer() throws IOException {
//        var port = config.getInt(WEBSERVER_PORT);
//        if (port == 0) {
//            var r = new Random();
//            var isBound = false;
//            for (int i = 1; i <= RANDOM_PORT_MAX_ATTEMPTS && !isBound; i++) {
//                var randomPort = RANDOM_PORT_RANGE_LOWER + r.nextInt(RANDOM_PORT_RANGE_UPPER - RANDOM_PORT_RANGE_LOWER);
//                try {
//                    logger.debug("Trying to bind at port {}, Attempt {}", randomPort, i);
//                    httpServer = HttpServer.create(new InetSocketAddress(randomPort), 0);
//                    isBound = true;
//                    this.port = randomPort;
//                } catch (BindException ex) {
//                    logger.debug("Failed to bind at port {}. Error: {}", randomPort, ex.toString());
//                }
//            }
//            if (!isBound) {
//                throw new BindException("Could not find a port to bind");
//            }
//        } else {
//            this.port = port;
//            httpServer = HttpServer.create(new InetSocketAddress(port), 0);
//        }
//        logger.info("HTTP Server bound at: {}", this.port);
//    }
//
//    void start() throws IOException {
//        logger.info("Starting HTTP Server");
//        createServer();
//
//        httpServer.createContext("/hello-world", new HelloWorldHandler());
//        httpServer.createContext(fileServerHandler.getRootPath(), fileServerHandler);
////        httpServer.setExecutor(executor);
//        httpServer.setExecutor(null);
//        httpServer.start();
//    }
//
//    public void stop() {
//        logger.info("Stopping HTTP Server");
//        httpServer.stop(2);
//    }
//
//    public static void main(String[] args) throws IOException {
//        Config conf = ConfigFactory.load();
//        FileServer fileServer = new FileServerImpl(conf);
//        ExceptionHandler exceptionHandler = new ExceptionHandler();
//        JavaFileServerHandler fsc = new JavaFileServerHandler(fileServer, exceptionHandler, conf);
//        new HelloWorldWebServerMain(fsc, conf).start();
//    }
//}
