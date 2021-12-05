package nuno;

public interface Constants {
    String WEBSERVER_PORT = "nuno.fileserver.port";
    String WEBSERVER_THREAD_POOL_SIZE = "nuno.fileserver.thread-pool-size";
    String FILE_SERVER_BASE_PATH = "nuno.fileserver.base-path";

    int RANDOM_PORT_RANGE_LOWER = 40000;
    int RANDOM_PORT_RANGE_UPPER = 50000;
    int RANDOM_PORT_MAX_ATTEMPTS = 10;

    String CRLF = "\r\n";
}