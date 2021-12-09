package com.nsantos.httpfileserver;

import java.net.URI;
import java.util.Map;

record HttpRequest(String method, URI uri, String httpVersion, Map<String, String> headers) {
}

