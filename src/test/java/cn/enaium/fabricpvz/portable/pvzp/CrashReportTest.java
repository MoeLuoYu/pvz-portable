package cn.enaium.fabricpvz.portable.pvzp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Random;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests the crash report writer (a port addition, after Minecraft's): the report's shape (header,
 * the random second-line quip, time, description, trace, system details), the crash file's name and
 * its placement under the game directory's {@code crash-reports/}.
 */
class CrashReportTest {

    private static final IllegalStateException TEST_CRASH =
            new IllegalStateException("shader compilation failed, see log for details");

    @Test
    void commentsArePlentifulAndWrappingIsStable() {
        assertTrue(CrashReport.crashCommentCount() >= 40, "a healthy quip pool, like Minecraft's");

        assertEquals(CrashReport.crashComment(0), CrashReport.crashComment(CrashReport.crashCommentCount()));
        assertNotEquals(CrashReport.crashComment(0), CrashReport.crashComment(1));
        assertTrue(CrashReport.pickCrashComment(new Random(42)).startsWith("//") == false,
                "the quip itself carries no prefix; the report adds the //");
    }

    @Test
    void reportCarriesTheMinecraftShape() {
        String aReport = CrashReport.build("Unexpected error", TEST_CRASH, "Render thread",
                "There are four lights!", LocalDateTime.of(2023, 8, 14, 20, 2, 39));

        assertTrue(aReport.startsWith("---- PvZ-Portable Crash Report ----\n// There are four lights!\n"),
                "the header with the random quip on line two, got: " + aReport);
        assertTrue(aReport.contains("Time: 2023-08-14 20:02:39\n"), "the time line");
        assertTrue(aReport.contains("Description: Unexpected error\n"), "the description line");
        assertTrue(aReport.contains("java.lang.IllegalStateException: shader compilation failed, see log for details"),
                "the stack trace with the exception message");
        assertTrue(aReport.contains("A detailed walkthrough of the error, its code path and all known details is as follows:\n"
                + "---------------------------------------------------------------------------------------\n"),
                "the walkthrough banner, exactly as Minecraft's");
        assertTrue(aReport.contains("-- Head --\nThread: Render thread\nStacktrace:\n"), "the head section");
        assertTrue(aReport.contains("-- System Details --\nDetails:\n"), "the system details block");
    }

    @Test
    void systemDetailsCarryTheIdentityLines() {
        String aReport = CrashReport.build("Unexpected error", TEST_CRASH, "main",
                CrashReport.pickCrashComment(new Random()), LocalDateTime.now());

        assertTrue(aReport.contains("\tPvZ-Portable Version: "), "the game version line");
        assertTrue(aReport.contains("\tOperating System: "), "the OS line");
        assertTrue(aReport.contains("\tJava Version: "), "the Java line");
        assertTrue(aReport.contains("\tJava VM Version: "), "the VM line");
        assertTrue(aReport.matches("(?s).*\tMemory: \\d+ bytes \\(\\d+ MiB\\) / \\d+ bytes \\(\\d+ MiB\\) up to \\d+ bytes \\(\\d+ MiB\\).*"),
                "the Minecraft-style memory picture, got: " + aReport);
        assertTrue(aReport.matches("(?s).*\tCPUs: \\d+.*"), "the CPU count line");
        assertTrue(aReport.matches("(?s).*\tCPU: \\d+x .*"), "the CPU model line");
        assertTrue(aReport.matches("(?s).*\tJVM Flags: \\d+ total; .*"), "the JVM flags line");
        assertTrue(aReport.contains("\tFabric Loader: "), "the loader line");
        assertTrue(aReport.matches("(?s).*\tMods: \\d+.*"), "the mod count line");
        assertTrue(aReport.contains("\tIs Modded: Definitely;"), "the modded line");
        assertTrue(aReport.contains("\tType: Client\n"), "the client type line");
    }

    /** A written report lands under the game directory's crash-reports/ with Minecraft's file name
     * shape and carries the crashing thread's name. */
    @Test
    void writeLandsInTheGameDirectory(@TempDir Path theTempDir) throws IOException {
        Path aGameDir = Files.createDirectories(theTempDir.resolve("gamedir"));
        System.setProperty("pvz.gameDir", aGameDir.toString());
        try {
            Path aReport = CrashReport.write("Unexpected error", TEST_CRASH, Thread.currentThread());

            assertTrue(aReport.startsWith(aGameDir.resolve("crash-reports")),
                    "under the game directory's crash-reports/, got: " + aReport);
            assertTrue(aReport.getFileName().toString().matches("crash-\\d{4}-\\d{2}-\\d{2}_\\d{2}\\.\\d{2}\\.\\d{2}-client\\.txt"),
                    "Minecraft's file name shape, got: " + aReport.getFileName());
            String aContent = Files.readString(aReport);
            assertTrue(aContent.contains("Description: Unexpected error"));
            assertTrue(aContent.contains("Thread: " + Thread.currentThread().getName()));
        } finally {
            System.clearProperty("pvz.gameDir");
        }
    }
}
