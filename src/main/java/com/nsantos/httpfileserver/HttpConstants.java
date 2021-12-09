package com.nsantos.httpfileserver;


import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.util.Map;

public interface HttpConstants {
    Map<Integer, String> HTTP_STATUS = Map.of(
            HttpStatus.SC_OK, "OK",
            HttpStatus.SC_BAD_REQUEST, "Bad Request",
            HttpStatus.SC_FORBIDDEN, "Forbidden",
            HttpStatus.SC_NOT_FOUND, "Not Found"
    );

    ContentType TEXT_PLAIN_UTF8 = ContentType.TEXT_PLAIN.withCharset(StandardCharsets.UTF_8);
    ContentType TEXT_HTML_UTF8 = ContentType.TEXT_HTML.withCharset(StandardCharsets.UTF_8);
}
