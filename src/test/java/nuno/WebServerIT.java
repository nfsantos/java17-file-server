package nuno;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertTrue;

// Integration test
public class WebServerIT {

    private static final Logger logger = LoggerFactory.getLogger(WebServerIT.class);

    @BeforeAll
    static void init() {
        logger.info("Creating web server");
    }

    @AfterAll
    static void shutdown() {
        logger.info("Shutting down web server");
    }

    @Test
    void myTest() {
        assertTrue(1 == 1);
    }
}
