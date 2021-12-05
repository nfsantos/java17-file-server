package nuno;

//import static org.mockito.Mockito.*;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import nuno.exceptions.ExceptionHandler;
import nuno.fileserver.FileServer;
import nuno.fileserver.FileServerImpl;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpStatus;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

// TODO: mocks
// TODO: Split in fast/slow unit tests
// TODO: Split between unit and integration tests. Integration tests have cs_quotes pre, post and verify phases to
//  start and stop the testing environment.
//@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class WebServerTest {

    private static final Logger logger = LoggerFactory.getLogger(WebServerTest.class);

    private static HttpFileServerMain webServer = null;
    private static HttpClient httpClient = null;

    private static Path getTestPath(String path) throws URISyntaxException {
        return Path.of(WebServerTest.class.getClassLoader().getResource(path).toURI());
    }

    private static Config createTestConfig(Map<String, Object> testOverrides) {
        var testConfig = ConfigFactory.parseMap(testOverrides);
        var systemConfig = ConfigFactory.load();
        return testConfig.withFallback(systemConfig);
    }

    private URI webServerAddress() {
        return URI.create("http://localhost:%d".formatted(webServer.getPort()));
    }

    private URI fileServerBasePath() {
        return URI.create("http://localhost:%d/file-server".formatted(webServer.getPort()));
    }

    private URI fileServerPath(String segment) {
        return URI.create("http://localhost:%d/file-server/%s".formatted(webServer.getPort(), segment));
    }

    @BeforeAll
    static void init() throws IOException, URISyntaxException {
        logger.info("Creating web server");
        var basePath = getTestPath("files").toString();
        logger.info("Base uri: {}", basePath);
        var conf = createTestConfig(Map.of(
                Constants.FILE_SERVER_BASE_PATH, basePath,
                Constants.WEBSERVER_PORT, 0
        ));
        FileServer fileServer = new FileServerImpl(conf);
        ExceptionHandler exceptionHandler = new ExceptionHandler();
        FileServerHandler fsc = new FileServerHandler(fileServer, exceptionHandler, conf);
        webServer = new HttpFileServerMain(fsc, conf);
        webServer.start();
        // TODO: How to send Connection header
        httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    @AfterAll
    static void shutdown() throws IOException {
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
    void emptyPath() throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder()
                .uri(fileServerBasePath())
                .GET()
                .build();
        logger.info("Connecting to: {}", request);
        HttpResponse<String> httpResponse = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        // Listing of root directory
        assertEquals(200, httpResponse.statusCode());
        var body = httpResponse.body();
        assertEquals("", body);
        var headers = httpResponse.headers().map();
        logger.info("Headers {}", headers);
        logger.info("File: {}", body);
    }

    @Test
    void wrongTopPath() throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder()
                .uri(webServerAddress())
                .GET()
                .build();
        logger.info("Connecting to: {}", request);
        HttpResponse<String> httpResponse = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(HttpStatus.SC_NOT_FOUND, httpResponse.statusCode());
        var body = httpResponse.body();
        assertEquals("", body);
    }

    @ParameterizedTest
    @MethodSource("provideArgsForGetFile")
    void simpleFile(String filePath, String expectedContentType) throws IOException, InterruptedException, URISyntaxException {
        testFile(filePath, expectedContentType);
    }

    private static Stream<Arguments> provideArgsForGetFile() {
        return Stream.of(
                Arguments.of("cs_quotes", HttpConstants.HTTP_APPLICATION_OCTET_STREAM),
                Arguments.of("image.png", HttpConstants.HTTP_IMAGE_PNG),
                Arguments.of("dir1/a", HttpConstants.HTTP_APPLICATION_OCTET_STREAM)
        );
    }

    private void testFile(String filePath, String expectedContentType) throws IOException, InterruptedException, URISyntaxException {
        var requestedFile = getTestPath("files/" + filePath);
        var request = HttpRequest.newBuilder()
                .uri(fileServerPath(filePath))
                .GET()
                .build();
        logger.info("Connecting to: {}", request);
        HttpResponse<byte[]> httpResponse = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, httpResponse.statusCode());

        // Headers
        var headers = httpResponse.headers();
        logger.info("Headers {}", headers.map());
        var maybeHttpContent = headers.firstValue(HttpHeaders.CONTENT_TYPE);
        assertTrue(maybeHttpContent.isPresent());
        assertEquals(expectedContentType, maybeHttpContent.get());

        var maybeHttpLength = headers.firstValue(HttpHeaders.CONTENT_LENGTH);
        assertTrue(maybeHttpLength.isPresent());
        assertEquals(Files.size(requestedFile), Long.parseLong(maybeHttpLength.get()));

        // Content
        var expectedContent = Files.readAllBytes(requestedFile);
        assertArrayEquals(expectedContent, httpResponse.body());
    }
}
