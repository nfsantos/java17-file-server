package nuno.fileserver;

import java.io.IOException;
import java.nio.file.Path;
import java.util.stream.Stream;

public interface FileServer {
    Path getBasePath();

    boolean isFile(String relativePath);

    boolean isDirectory(String relativePath);

    Path getFile(String relativePath);

    Stream<Path> getDirectoryListing(String relativePath) throws IOException;
}
