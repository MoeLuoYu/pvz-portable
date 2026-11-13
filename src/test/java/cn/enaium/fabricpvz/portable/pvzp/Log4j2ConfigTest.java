package cn.enaium.fabricpvz.portable.pvzp;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests the log4j2 configuration's file logging (a port addition, after Minecraft's): the rolling
 * file appender writes {@code logs/latest.log} into the game directory {@code -Dpvz.gameDir} points
 * at, in the plain Minecraft-style line shape, without ANSI styles.
 *
 * <p>The test reconfigures the live {@code LoggerContext} (it re-reads the classpath's
 * {@code log4j2.xml} with the new system property), logs one line, stops the context to drain the
 * async queue and flush the file, and reads it back. Logging is restarted for the rest of the JVM
 * afterwards.
 */
class Log4j2ConfigTest {

    @Test
    void theFileAppenderWritesIntoTheGameDirectory(@TempDir Path theTempDir) throws IOException {
        Path aGameDir = Files.createDirectories(theTempDir.resolve("gamedir"));
        System.setProperty("pvz.gameDir", aGameDir.toString());

        LoggerContext aContext = (LoggerContext) LogManager.getContext(false);
        try {
            aContext.reconfigure();

            org.slf4j.Logger aLogger = org.slf4j.LoggerFactory.getLogger("pvz-portable");
            aLogger.info("log file appender test line");

            aContext.stop(); // drain the async queue and flush the rolling file

            Path aLog = aGameDir.resolve("logs").resolve("latest.log");
            assertTrue(Files.exists(aLog), "logs/latest.log must exist under the game directory");
            String aContent = Files.readString(aLog);
            assertTrue(aContent.contains("log file appender test line"),
                    "the logged line must be in the file, got: " + aContent);
            assertTrue(aContent.matches("(?s).*\\[\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\] \\[.+/INFO\\]: \\[pvz-portable\\] log file appender test line.*"),
                    "the line must carry the Minecraft-style prefix, got: " + aContent);
            assertFalse(aContent.contains("\u001B["), "the file must not carry ANSI styles");
        } finally {
            System.clearProperty("pvz.gameDir");
            aContext.reconfigure(); // point the appender away from the deleted temp dir again
            aContext.start();       // logging back on for the rest of this JVM
        }
    }
}
