package com.nsantos.httpfileserver;

import com.typesafe.config.Config;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HeaderElements;
import org.apache.hc.core5.http.HttpHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

/**
 * Static utility methods to generate HTTP Responses
 */
public class HttpResponseWriter {
    private static final Logger logger = LoggerFactory.getLogger(HttpResponseWriter.class);
    private final long keepAliveTimeoutSeconds;

    public HttpResponseWriter(Config config) {
        this.keepAliveTimeoutSeconds = config.getDuration("com.nsantos.httpfileserver.keep-alive-timeout", TimeUnit.SECONDS);
    }
    /**
     * Writes an HTTP response with a given file as contents to an OutputStream
     *
     * @param os      The output stream where to write the response
     * @param status  The status to send in the response
     * @param headers The headers of the response
     * @param file    The file to send as a body
     * @throws IOException
     */
    public void sendResponse(OutputStream os, int status, HashMap<String, String> headers, Path file) throws IOException {
        var contentType = URLConnection.guessContentTypeFromName(file.toString());
        if (contentType == null) {
            // Could not guess, default to a generic content type, just a series of bytes
            contentType = ContentType.APPLICATION_OCTET_STREAM.toString();
        }
        var bodySize = Files.size(file);
        logger.debug("Sending file in HTTP response. File {}, Content Type: {}, Size: {}", file, contentType, bodySize);
        headers.put(HttpHeaders.CONTENT_LENGTH, Long.toString(bodySize));
        headers.put(HttpHeaders.CONTENT_TYPE, contentType);
        sendHeader(os, status, headers);
        Files.copy(file, os);
        os.flush();
    }

    /**
     * Writes an HTTP response with a body to an OutputStream.
     *
     * @param os          The output stream where to write the response
     * @param status      The status to send in the response
     * @param headers     The headers of the response
     * @param body        The body of the response.
     * @param contentType The content type
     * @throws IOException
     */
    public void sendResponse(OutputStream os, int status, HashMap<String, String> headers, byte[] body, ContentType contentType) throws IOException {
        headers.put(HttpHeaders.CONTENT_LENGTH, Integer.toString(body.length));
        headers.put(HttpHeaders.CONTENT_TYPE, contentType.toString());
        sendHeader(os, status, headers);
        os.write(body);
        os.flush();
    }

    /**
     * Writes an HTTP response without a body to an OutputStream
     *
     * @param os      The output stream where to write the response
     * @param status  The status to send in the response
     * @param headers The headers of the response
     * @throws IOException
     */
    public  void sendResponse(OutputStream os, int status, HashMap<String, String> headers) throws IOException {
        headers.put(HttpHeaders.CONTENT_LENGTH, "0");
        sendHeader(os, status, headers);
    }

    private void sendHeader(OutputStream os, int status, HashMap<String, String> headers) throws IOException {
        // Use only US_ASCII characters: https://datatracker.ietf.org/doc/html/rfc7230#section-3
        var writer = new OutputStreamWriter(os, StandardCharsets.US_ASCII);
        // Write request header
        var statusString = HttpConstants.HTTP_STATUS.get(status);
        if (statusString == null) {
            logger.warn("Missing status string for status {}", status);
            statusString = "";
        }
        var statusLine = "HTTP/1.1 %d %s".formatted(status, statusString);
        writer.write(statusLine);
        writer.write(Constants.CRLF);

        // Add generic headers
        headers.put(HttpHeaders.CONNECTION, HeaderElements.KEEP_ALIVE);
        headers.put(HttpHeaders.KEEP_ALIVE, "timeout=%d, max=10".formatted(keepAliveTimeoutSeconds));

        // Send headers
        for (java.util.Map.Entry<String, String> entry : headers.entrySet()) {
            writer.write("%s: %s".formatted(entry.getKey(), entry.getValue()));
            writer.write(Constants.CRLF);
        }
        writer.write(Constants.CRLF);
        writer.flush();
        // Do not close the output stream, this is the stream of the socket, we must keep it open to process further requests.
    }

}