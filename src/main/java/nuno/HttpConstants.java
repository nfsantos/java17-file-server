package nuno;


import org.apache.hc.core5.http.HttpStatus;

import java.util.Map;

public interface HttpConstants {
    String HTTP_APPLICATION_OCTET_STREAM = "application/octet-stream";
    String HTTP_IMAGE_PNG = "image/png";

    Map<Integer, String> HTTP_STATUS = Map.of(
            HttpStatus.SC_OK, "OK",
            HttpStatus.SC_BAD_REQUEST, "Bad Request",
            HttpStatus.SC_FORBIDDEN, "Forbidden",
            HttpStatus.SC_NOT_FOUND, "Not Found"
    );
}
