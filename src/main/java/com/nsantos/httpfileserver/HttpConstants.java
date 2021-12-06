package com.nsantos.httpfileserver;


import org.apache.hc.core5.http.HttpStatus;

import java.util.Map;

public interface HttpConstants {
    Map<Integer, String> HTTP_STATUS = Map.of(
            HttpStatus.SC_OK, "OK",
            HttpStatus.SC_BAD_REQUEST, "Bad Request",
            HttpStatus.SC_FORBIDDEN, "Forbidden",
            HttpStatus.SC_NOT_FOUND, "Not Found"
    );
}
