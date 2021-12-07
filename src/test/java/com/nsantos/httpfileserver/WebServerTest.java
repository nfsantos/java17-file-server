package com.nsantos.httpfileserver;

//import static org.mockito.Mockito.*;

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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

// TODO: mocks
// TODO: Split between unit and integration tests. Integration tests have cs_quotes pre, post and verify phases to
//  start and stop the testing environment.
public class WebServerTest {

    private static final Logger logger = LoggerFactory.getLogger(WebServerTest.class);

    private static HttpFileServerMain webServer = null;
    private static HttpClient httpClient = null;

    private static Path getTestPath(String path) throws URISyntaxException {
        var url = WebServerTest.class.getClassLoader().getResource(path);
        if (url == null) {
            throw new IllegalArgumentException("Resource not found in classpath: " + path);
        } else {
            return Path.of(url.toURI());
        }
    }

    private static Config createTestConfig(Map<String, Object> testOverrides) {
        var testConfig = ConfigFactory.parseMap(testOverrides);
        var systemConfig = ConfigFactory.load();
        return testConfig.withFallback(systemConfig);
    }

    private URI fileServerAddress() {
        return URI.create("http://localhost:%d".formatted(webServer.getPort()));
    }

    private URI fileServerPath(String segment) {
        return URI.create("http://localhost:%d/%s".formatted(webServer.getPort(), segment));
    }

    @BeforeAll
    static void init() throws IOException, URISyntaxException {
        System.setProperty("jdk.httpclient.connectionPoolSize", "10");
//        System.setProperty("jdk.internal.httpclient.debug", "false");
        logger.info("Creating web server");
        var basePath = getTestPath("files").toString();
        logger.info("Base uri: {}", basePath);
        var conf = createTestConfig(Map.of(
                Constants.FILE_SERVER_BASE_PATH, basePath,
                Constants.WEBSERVER_PORT, 0
        ));
        FileServer fileServer = new FileServerImpl(conf);
        ExceptionHandler exceptionHandler = new ExceptionHandler();
        FileServerHandlerFactory fsc = new FileServerHandlerFactory(fileServer, exceptionHandler, conf);
        webServer = new HttpFileServerMain(fsc, conf);
        webServer.start();
        // TODO: How to send Connection header
        httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    @AfterAll
    static void shutdown() throws IOException, InterruptedException {
        logger.info("Shutting down web server");
        webServer.stop();
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
        // TODO: Test with empty dir? Maven does not copy empty dirs from test/resources to target/test-classes
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
        assertEquals(ContentType.TEXT_HTML.withCharset(StandardCharsets.UTF_8).toString(), maybeContentType.get());
        var body = httpResponse.body();
        assertEquals(expectedBaseDirectoryListing.trim(), body.trim());
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
        assertEquals(ContentType.TEXT_HTML.withCharset(StandardCharsets.UTF_8).toString(), maybeContentType.get());
        var body = httpResponse.body();
        assertEquals(expectedBaseDirectoryListing.trim(), body.trim());
    }

    private static final String expectedBaseDirectoryListing = """
            <!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN" "http://www.w3.org/TR/html4/strict.dtd">
            <html>
            <head>
            <meta http-equiv="Content-Type" content="text/html; charset=utf-8">
            <title>Directory listing for </title>
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
    void multipleRequests() throws URISyntaxException, IOException, InterruptedException {
        var file = "dir1/a";
        var requestedFile = getTestPath("files/" + file);
        var request = HttpRequest.newBuilder()
                .uri(fileServerPath(file))
                .GET()
                .build();
        logger.info("Connecting to: {}", request);
        for (int i = 1; i < 10; i++) {
            logger.info("Sending req {}", i);
            HttpResponse<byte[]> httpResponse = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            assertEquals(200, httpResponse.statusCode());
            var body = httpResponse.body();
            var headers = httpResponse.headers();
        }
    }
}
