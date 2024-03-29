package com.nsantos.httpfileserver.fileserver;

import java.io.IOException;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Definition of a file server service.
 */
public interface FileServer {
    Path getBasePath();

    boolean isFile(String relativePath);

    boolean isDirectory(String relativePath);

    Path getFile(String relativePath);

    Stream<Path> getDirectoryListing(String relativePath) throws IOException;
}
