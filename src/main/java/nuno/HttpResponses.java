package nuno;

import org.apache.hc.core5.http.HttpHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;

import static nuno.Constants.CRLF;
import static nuno.HttpConstants.HTTP_STATUS;

public class HttpResponses {
    private static final Logger logger = LoggerFactory.getLogger(HttpResponses.class);

    private static void sendHeader(Socket socket, int status, HashMap<String, String> headers) throws IOException {
        var os = new BufferedOutputStream(socket.getOutputStream());
        // Write request header
        Writer writer = new PrintWriter(os, false, StandardCharsets.UTF_8);
        var statusString = HTTP_STATUS.get(status);
        if (statusString == null) {
            logger.warn("Missing status string for status {}", status);
            statusString = "";
        }
        var statusLine = "HTTP/1.1 %d %s".formatted(status, statusString);
        writer.write(statusLine);
        writer.write(CRLF);

        // Add generic headers
        headers.put(HttpHeaders.CONNECTION, "keep-alive");

        // Send headers
        var iter = headers.entrySet().iterator();
        while (iter.hasNext()) {
            var entry = iter.next();
            writer.write("%s: %s".formatted(entry.getKey(), entry.getValue()));
            writer.write(CRLF);
        }
        writer.write(CRLF);
        writer.flush();
    }

    public static void sendResponse(Socket socket, int status, HashMap<String, String> headers, Path file) throws IOException {
        var bodySize = Files.size(file);
        headers.put(HttpHeaders.CONTENT_LENGTH, Long.toString(bodySize));
        sendHeader(socket, status, headers);
        Files.copy(file, socket.getOutputStream());
    }

    public static void sendResponse(Socket socket, int status, HashMap<String, String> headers, String body) throws IOException {
        sendHeader(socket, status, headers);
        var bodyArray = body.getBytes(StandardCharsets.UTF_8);
        headers.put(HttpHeaders.CONTENT_LENGTH, Long.toString(bodyArray.length));
        var os = socket.getOutputStream();
        os.write(bodyArray);
        os.flush();
    }

    public static void sendResponse(Socket socket, int status, HashMap<String, String> headers) throws IOException {
        headers.put(HttpHeaders.CONTENT_LENGTH, "0");
        sendHeader(socket, status, headers);
    }

}