package nuno;


import java.net.URI;

record RequestLine(String method, URI uri, String httpVersion) {
    static RequestLine createHeader(String line) {
        var parts = line.split(" ");
        return new RequestLine(parts[0], URI.create(parts[1]), parts[2]);
    }
}

public class HttpRequest {
}
