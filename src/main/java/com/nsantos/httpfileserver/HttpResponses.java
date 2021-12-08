package com.nsantos.httpfileserver;

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

public class HttpResponses {
    private static final Logger logger = LoggerFactory.getLogger(HttpResponses.class);

    private static void sendHeader(OutputStream os, int status, HashMap<String, String> headers) throws IOException {
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

        // Send headers
        for (java.util.Map.Entry<String, String> entry : headers.entrySet()) {
            writer.write("%s: %s".formatted(entry.getKey(), entry.getValue()));
            writer.write(Constants.CRLF);
        }
        writer.write(Constants.CRLF);
        writer.flush();
        // Do not close the output stream, this is the stream of the socket, we must keep it open to process further requests.
    }

    /**
     * Writes an HTTP response with a given file as the to an OutputStream
     *
     * @param os      The output stream where to write the response
     * @param status  The status to send in the response
     * @param headers The headers of the response
     * @param file    The file to send as a body
     * @throws IOException
     */
    public static void sendResponse(OutputStream os, int status, HashMap<String, String> headers, Path file) throws IOException {
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
    }

    /**
     * Writes an HTTP response with a body to an OutputStream.
     *
     * @param os          The output stream where to write the response
     * @param status      The status to send in the response
     * @param headers     The headers of the response
     * @param body        The body of the response. Must be encoded in US_ASCII
     * @param contentType The content type
     * @throws IOException
     */
    public static void sendResponse(OutputStream os, int status, HashMap<String, String> headers, String body, ContentType contentType) throws IOException {
        var bodyArray = body.getBytes(StandardCharsets.US_ASCII);
        headers.put(HttpHeaders.CONTENT_LENGTH, Long.toString(bodyArray.length));
        headers.put(HttpHeaders.CONTENT_TYPE, contentType.toString());
        sendHeader(os, status, headers);
        os.write(bodyArray);
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
    public static void sendResponse(OutputStream os, int status, HashMap<String, String> headers) throws IOException {
        headers.put(HttpHeaders.CONTENT_LENGTH, "0");
        sendHeader(os, status, headers);
    }

}