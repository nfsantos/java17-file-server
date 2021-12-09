package com.nsantos.httpfileserver;

import com.nsantos.httpfileserver.exceptions.ExceptionHandler;
import com.nsantos.httpfileserver.fileserver.FileServer;
import com.nsantos.httpfileserver.fileserver.FileServerImpl;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHeaders;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.*;
import java.util.stream.Stream;

import static com.nsantos.httpfileserver.HttpConstants.TEXT_HTML_UTF8;
import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class WebServerTest {
    /* TODO: Tests for keep-alive support. It is not immediately obvious of how to test keep alive using the Java HTTP Client,
     *   because it does not expose information on which connection is being used to perform a request, so we cannot
     *   easily assert if it used an existing connection or a new one. Maybe there is some way of obtaining this information
     *   from this library. Otherwise, tests for keep-alive would have to use a RAW TCP Socket.
     */
    private static final Logger logger = LoggerFactory.getLogger(WebServerTest.class);

    private HttpFileServerMain webServer = null;
    private HttpClient httpClient = null;

    private static Config createTestConfig(Map<String, Object> testOverrides) {
        var testConfig = ConfigFactory.parseMap(testOverrides);
        var systemConfig = ConfigFactory.load();
        return testConfig.withFallback(systemConfig);
    }

    private HttpFileServerMain createTestServer() throws URISyntaxException, IOException {
        var basePath = getTestPath("files").toString();
        logger.info("Creating web server serving from {}", basePath);
        // Set the configuration properties for the test File Server instance
        var conf = createTestConfig(Map.of(
                Constants.FILE_SERVER_BASE_PATH, basePath,
                Constants.WEBSERVER_PORT, 0, // Select a random port
                Constants.WEBSERVER_THREAD_POOL_SIZE, 8,
                Constants.KEEP_ALIVE_TIMEOUT, "5 seconds"

        ));
        FileServer fileServer = new FileServerImpl(conf);
        HttpResponseWriter httpResponseWriter = new HttpResponseWriter(conf);
        ExceptionHandler exceptionHandler = new ExceptionHandler(httpResponseWriter);
        ConnectionHandlerFactory fsc = new ConnectionHandlerFactory(fileServer, exceptionHandler, httpResponseWriter, conf);
        webServer = new HttpFileServerMain(fsc, conf);
        webServer.start();
        return webServer;
    }

    @BeforeAll
    void init() throws IOException, URISyntaxException {
        // Create the HTTP Client used as a test client
        System.setProperty("jdk.httpclient.connectionPoolSize", "10");
//        System.setProperty("jdk.internal.httpclient.debug", "false");
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build();

        // Create the web server under test
        this.webServer = createTestServer();
    }

    @AfterAll
    void shutdown() throws IOException, InterruptedException {
        logger.info("Shutting down web server");
        this.webServer.stop();
    }

    @Test
    void notFound() throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder()
                .uri(fileServerPath("foo"))
                .GET()
                .build();
        logger.info("Connecting to: {}", request);
        HttpResponse<String> httpResponse = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(404, httpResponse.statusCode());
        var body = httpResponse.body();
        assertEquals("", body);
        var headers = httpResponse.headers().map();
        logger.info("Headers {}", headers);
        logger.info("File: {}", body);
    }

    @Test
    void emptyDir() throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder()
                .uri(fileServerPath("dir2"))
                .GET()
                .build();
        logger.info("Connecting to: {}", request);
        HttpResponse<String> httpResponse = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        // Listing of root directory
        assertEquals(200, httpResponse.statusCode());
        var headers = httpResponse.headers();
        var maybeContentType = headers.firstValue(HttpHeaders.CONTENT_TYPE);
        assert (maybeContentType.isPresent());
        assertEquals(TEXT_HTML_UTF8.toString(), maybeContentType.get());
        var body = httpResponse.body();
        assertEquals(expectedEmptyDirectoryListing.trim(), body.trim());
    }

    @Test
    void rootListing() throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder()
                .uri(fileServerAddress())
                .GET()
                .build();
        logger.info("Connecting to: {}", request);
        HttpResponse<String> httpResponse = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        // Listing of root directory
        assertEquals(200, httpResponse.statusCode());
        var headers = httpResponse.headers();
        var maybeContentType = headers.firstValue(HttpHeaders.CONTENT_TYPE);
        assert (maybeContentType.isPresent());
        assertEquals(TEXT_HTML_UTF8.toString(), maybeContentType.get());
        var body = httpResponse.body();
        assertEquals(expectedBaseDirectoryListing.trim(), body.trim());
    }

    @ParameterizedTest
    @MethodSource("provideArgsForGetFile")
    void simpleFile(String filePath, ContentType expectedContentType) throws IOException, InterruptedException, URISyntaxException {
        testFile(filePath, expectedContentType);
    }

    private void testFile(String filePath, ContentType expectedContentType) throws IOException, InterruptedException, URISyntaxException {
        var requestedFile = getTestPath("files/" + filePath);
        var request = HttpRequest.newBuilder()
                .uri(fileServerPath(filePath))
                .GET()
                .build();
        logger.info("Sending request: {}", request);
        HttpResponse<byte[]> httpResponse = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, httpResponse.statusCode());

        // Headers
        var headers = httpResponse.headers();
        var maybeHttpContent = headers.firstValue(HttpHeaders.CONTENT_TYPE);
        assertTrue(maybeHttpContent.isPresent());
        assertEquals(expectedContentType.toString(), maybeHttpContent.get());

        var maybeHttpLength = headers.firstValue(HttpHeaders.CONTENT_LENGTH);
        assertTrue(maybeHttpLength.isPresent());
        assertEquals(Files.size(requestedFile), Long.parseLong(maybeHttpLength.get()));

        // Content
        var expectedContent = Files.readAllBytes(requestedFile);
        assertArrayEquals(expectedContent, httpResponse.body());
    }

    @Test
    void consecutiveRequests() throws URISyntaxException, IOException, InterruptedException {
        var a = provideArgsForGetFile().toArray(Arguments[]::new);
        for (int i = 1; i < 10; i++) {
            var arg = a[i % a.length];
            var fileName = (String) arg.get()[0];
            var contentType = (ContentType) arg.get()[1];
            testFile(fileName, contentType);
        }
    }

    @Test
    void concurrentRequests() throws Throwable {
        var a = provideArgsForGetFile().toArray(Arguments[]::new);
        var totalRequests = 100;
        var pool = Executors.newFixedThreadPool(8, ThreadUtils.newThreadFactory("test-client", true));
        var cs = new ExecutorCompletionService(pool);
        for (int i = 0; i < totalRequests; i++) {
            final var taskIndex = i;
            cs.submit((Callable<Integer>) () -> {
                var arg = a[taskIndex % a.length];
                var fileName = (String) arg.get()[0];
                var contentType = (ContentType) arg.get()[1];
                testFile(fileName, contentType);
                return taskIndex;
            });
        }
        for (int i = 0; i < totalRequests; i++) {
            try {
                var taskIndex = cs.poll(10, TimeUnit.SECONDS).get();
                logger.info("Task completed {}. Number of tasks: {}", taskIndex, i);
            } catch (ExecutionException e) {
                throw e.getCause();
            }
        }
    }

    private URI fileServerAddress() {
        return URI.create("http://localhost:%d".formatted(webServer.getPort()));
    }

    private URI fileServerPath(String segment) {
        return URI.create("http://localhost:%d/%s".formatted(webServer.getPort(), segment));
    }

    /**
     *
     */
    private static Path getTestPath(String path) throws URISyntaxException {
        var url = WebServerTest.class.getClassLoader().getResource(path);
        if (url == null) {
            throw new IllegalArgumentException("Resource not found in classpath: " + path);
        } else {
            return Path.of(url.toURI());
        }
    }

    private static final String expectedEmptyDirectoryListing = """
            <!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN" "http://www.w3.org/TR/html4/strict.dtd">
            <html>
            <head>
            <meta http-equiv="Content-Type" content="text/html; charset=utf-8">
            <title>Directory listing for /dir2</title>
            </head>
            <body>
            <h1>Directory listing for /dir2</h1>
            <hr>
            <ul>
                
            </ul>
            <hr>
            </body>
            </html>
            """;

    private static final String expectedBaseDirectoryListing = """
            <!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN" "http://www.w3.org/TR/html4/strict.dtd">
            <html>
            <head>
            <meta http-equiv="Content-Type" content="text/html; charset=utf-8">
            <title>Directory listing for /</title>
            </head>
            <body>
            <h1>Directory listing for /</h1>
            <hr>
            <ul>
            <li><a href="dir1/">dir1/</a></li>
            <li><a href="cs_quotes">cs_quotes</a></li>
            <li><a href="image.png">image.png</a></li>
            <li><a href="carl_sagan_quotes">carl_sagan_quotes</a></li>
            <li><a href="unicode_⛷_☀_chars">unicode_⛷_☀_chars</a></li>
            <li><a href="dir2/">dir2/</a></li>
            </ul>
            <hr>
            </body>
            </html>""";

    private static Stream<Arguments> provideArgsForGetFile() {
        return Stream.of(
                Arguments.of("cs_quotes", ContentType.APPLICATION_OCTET_STREAM),
                Arguments.of("image.png", ContentType.IMAGE_PNG),
                Arguments.of("dir1/a", ContentType.APPLICATION_OCTET_STREAM),
                Arguments.of("unicode_⛷_☀_chars", ContentType.APPLICATION_OCTET_STREAM)
        );
    }
}
