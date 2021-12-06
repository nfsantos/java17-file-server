package com.nsantos.httpfileserver;

public interface Constants {
    String WEBSERVER_PORT = "com.nsantos.httpfileserver.port";
    String WEBSERVER_THREAD_POOL_SIZE = "com.nsantos.httpfileserver.thread-pool-size";
    String FILE_SERVER_BASE_PATH = "com.nsantos.httpfileserver.base-path";

    int RANDOM_PORT_RANGE_LOWER = 40000;
    int RANDOM_PORT_RANGE_UPPER = 50000;
    int RANDOM_PORT_MAX_ATTEMPTS = 10;

    String CRLF = "\r\n";
}