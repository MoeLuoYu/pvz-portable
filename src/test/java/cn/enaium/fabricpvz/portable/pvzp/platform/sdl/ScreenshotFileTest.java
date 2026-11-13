/*
 * Headless tests for the F2 screenshot's file naming rule (Minecraft-style:
 * <dir>/yyyy-MM-dd_HH.mm.ss.png, with -1/-2/... appended on a same-second collision).
 */
package cn.enaium.fabricpvz.portable.pvzp.platform.sdl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ScreenshotFileTest {

    private static final Pattern NAME = Pattern.compile("\\d{4}-\\d{2}-\\d{2}_\\d{2}\\.\\d{2}\\.\\d{2}(-\\d+)?\\.png");

    @Test
    void namesFollowMinecraftsRuleAndCreateTheDirectory(@TempDir Path theTempDir) throws Exception {
        Path aDir = theTempDir.resolve("screenshots");
        Path aFile = SdlRenderer.screenshotFile(aDir);

        assertTrue(aFile.startsWith(aDir), "the file lives in the requested directory");
        assertTrue(Files.isDirectory(aDir), "the screenshots directory is created");
        assertTrue(NAME.matcher(aFile.getFileName().toString()).matches(),
                "the name is yyyy-MM-dd_HH.mm.ss.png, got " + aFile.getFileName());
    }

    @Test
    void sameSecondCollisionsGetNumberSuffixes(@TempDir Path theTempDir) throws Exception {
        Path aDir = theTempDir.resolve("screenshots");

        Path aFirst = SdlRenderer.screenshotFile(aDir);
        Files.createFile(aFirst);
        Path aSecond = SdlRenderer.screenshotFile(aDir);
        Files.createFile(aSecond);
        Path aThird = SdlRenderer.screenshotFile(aDir);

        String aBase = aFirst.getFileName().toString().replace(".png", "");
        assertEquals(aBase + "-1.png", aSecond.getFileName().toString(), "the first collision gets -1");
        assertEquals(aBase + "-2.png", aThird.getFileName().toString(), "the second collision gets -2");
    }
}
