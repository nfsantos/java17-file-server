package nuno.fileserver;

import com.typesafe.config.Config;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static nuno.Constants.FILE_SERVER_BASE_PATH;

public class FileServerImpl implements FileServer {
    private static final Logger logger = LoggerFactory.getLogger(FileServerImpl.class);

    @Getter
    private final Path basePath;

    public FileServerImpl(Config conf) {
        this.basePath = Path.of(conf.getString(FILE_SERVER_BASE_PATH));
        logger.info("Serving from directory: {}", basePath);
    }

    @Override
    public boolean isFile(String relativePath) {
        return Files.isRegularFile(basePath.resolve(relativePath));
    }

    @Override
    public boolean isDirectory(String relativePath) {
        return Files.isDirectory(basePath.resolve(relativePath));
    }

    @Override
    public Path getFile(String relativePath) {
        return basePath.resolve(relativePath);
    }

    @Override
    public Stream<Path> getDirectoryListing(String relativePath) throws IOException {
        return Files.list(basePath.resolve(relativePath));
    }
}
