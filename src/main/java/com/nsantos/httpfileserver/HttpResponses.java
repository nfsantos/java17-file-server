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
        // Write request header
        var writer = new OutputStreamWriter(os);
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
    }

    public static void sendResponse(OutputStream os, int status, HashMap<String, String> headers, Path file) throws IOException {
        var bodySize = Files.size(file);
        var contentType = URLConnection.guessContentTypeFromName(file.toString());
        if (contentType == null) {
            contentType = ContentType.APPLICATION_OCTET_STREAM.toString();
        }
        logger.info("Response: {}, content type: {} ", file, contentType);
        headers.put(HttpHeaders.CONTENT_LENGTH, Long.toString(bodySize));
        headers.put(HttpHeaders.CONTENT_TYPE, contentType);
        sendHeader(os, status, headers);
        Files.copy(file, os);
    }

    public static void sendResponse(OutputStream os, int status, HashMap<String, String> headers, String body, ContentType contentType) throws IOException {
        var bodyArray = body.getBytes(StandardCharsets.UTF_8);
        headers.put(HttpHeaders.CONTENT_LENGTH, Long.toString(bodyArray.length));
        headers.put(HttpHeaders.CONTENT_TYPE, contentType.toString());
        sendHeader(os, status, headers);
        os.write(bodyArray);
        os.flush();
    }

    public static void sendResponse(OutputStream os, int status, HashMap<String, String> headers) throws IOException {
        headers.put(HttpHeaders.CONTENT_LENGTH, "0");
        sendHeader(os, status, headers);
    }

}