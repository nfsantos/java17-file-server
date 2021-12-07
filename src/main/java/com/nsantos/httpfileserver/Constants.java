package com.nsantos.httpfileserver;

public interface Constants {
    /**
     * Port where to listen for connections. Set to 0 to choose a random port.
     */
    String WEBSERVER_PORT = "com.nsantos.httpfileserver.port";
    /**
     * Size of the thread pool used to server connections.
     */
    String WEBSERVER_THREAD_POOL_SIZE = "com.nsantos.httpfileserver.thread-pool-size";
    /**
     * Base path from where to server files
     */
    String FILE_SERVER_BASE_PATH = "com.nsantos.httpfileserver.base-path";

    /* Start of port range for random ports (inclusive).  */
    int RANDOM_PORT_RANGE_LOWER = 40000;
    /* End of port range for random ports (inclusive).  */
    int RANDOM_PORT_RANGE_UPPER = 49999;
    /* How many times to try to bind to a random port before giving up */
    int RANDOM_PORT_MAX_ATTEMPTS = 10;

    /**
     * Separator for HTTP header lines
     */
    String CRLF = "\r\n";
}