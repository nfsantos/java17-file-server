package nuno;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;

public class ThreadUtils {
    private static final Logger logger = LoggerFactory.getLogger(TPCServer.class);

    public static ExecutorService newSingleThreadExecutor(String name) {
        return Executors.newSingleThreadExecutor(r -> {
            var t = new Thread(r, name);
            t.setDaemon(true);
            return t;
        });
    }

    public static ExecutorService newBoundedCachedThreadPool(int minSize, int maxSize, String name) {
        return new ThreadPoolExecutor(
                minSize,
                maxSize,
                60L,
                TimeUnit.SECONDS,
                new SynchronousQueue<Runnable>(),
                newThreadFactory(name, true)
        );
    }

    public static ThreadFactory newThreadFactory(String name, Boolean daemon) {
        return new ThreadFactoryBuilder()
                .setNameFormat(name + "-%d")
                .setUncaughtExceptionHandler((Thread thread, Throwable t) -> {
                    logger.warn("Uncaught exception on thread: {}", thread, t);
                })
                .setDaemon(daemon)
                .build();
    }
}
