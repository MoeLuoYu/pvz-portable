package cn.enaium.fabricpvz.portable.lawn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import cn.enaium.fabricpvz.portable.sexy.Common;
import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;
import cn.enaium.fabricpvz.portable.sexy.graphics.MemoryImage;

/**
 * Tests the F3 debug overlay: the flag's registry persistence (the options dialog's
 * {@code [OPTIONS_DEBUG_INFO]} checkbox), the pure line builders, and a draw smoke pass through
 * the framework's per-frame hook.
 */
class LawnAppDebugInfoTest {

    private static LawnApp newApp() {
        LawnApp app = new LawnApp();
        LawnApp.gLawnApp = app;
        cn.enaium.fabricpvz.portable.test.HeadlessTestApp.initialize(app);
        return app;
    }

    @Test
    void debugInfoDefaultsToHidden() {
        assertFalse(newApp().mShowFPS, "the overlay is off until the player asks for it");
    }

    /** The persisted flag survives a restart through the emulated registry. */
    @Test
    void debugInfoPersistsAcrossRestarts(@TempDir Path theTempDir) throws IOException {
        Path aDir = Files.createDirectories(theTempDir.resolve("appdata"));
        Path anEmptyDir = Files.createDirectories(theTempDir.resolve("empty"));
        String aPreviousFolder = Common.getAppDataFolder();
        try {
            // HeadlessTestApp.initialize re-points the app data folder, so it is re-pointed after
            // every app is built (the same lifecycle as the auto-pause test).
            LawnApp aFirst = newApp();
            Common.setAppDataFolder(aDir.toString());
            aFirst.readFromRegistry();
            aFirst.mShowFPS = true;
            aFirst.writeToRegistry();

            LawnApp aSecond = newApp();
            Common.setAppDataFolder(aDir.toString());
            aSecond.readFromRegistry();
            assertTrue(aSecond.mShowFPS, "the written DebugInfo must be read back");

            LawnApp aThird = newApp();
            Common.setAppDataFolder(anEmptyDir.toString());
            aThird.readFromRegistry();
            assertFalse(aThird.mShowFPS, "an absent value keeps the default");
        } finally {
            Common.setAppDataFolder(aPreviousFolder);
            LawnApp.gLawnApp = null;
        }
    }

    @Test
    void fpsLineShowsFrameRateAndPerFrameTimes() {
        assertEquals("60 fps (update 0.4 ms / draw 2.3 ms)",
                LawnApp.debugFpsLine(60, 0.42, 2.34));
        assertEquals("0 fps (update 0.0 ms / draw 0.0 ms)",
                LawnApp.debugFpsLine(0, 0.0, 0.0));
    }

    @Test
    void memoryLineShowsUsedShareAllocatedAndMax() {
        long aMiB = 1048576;
        assertEquals("Mem: 31% 704/2272 MiB (max 4096 MiB)",
                LawnApp.debugMemoryLine(704 * aMiB, 2272 * aMiB, 4096 * aMiB));
        assertEquals("Mem: 0% 0/0 MiB (max 0 MiB)", LawnApp.debugMemoryLine(0, 0, 0));
    }

    @Test
    void allocRateLineShowsMiBPerSecond() {
        assertEquals("Alloc rate: 930 MiB/s", LawnApp.debugAllocRateLine(930.4));
    }

    /** The CPU-model probe answers the same string twice (cached) and never throws; on the dev
     * machine (Windows) it finds the real model via the registry, elsewhere it degrades to empty. */
    @Test
    void cpuModelProbeIsCachedAndSafe() {
        assertEquals(cn.enaium.fabricpvz.portable.pvzp.RuntimeEnvironment.cpuModel(),
                cn.enaium.fabricpvz.portable.pvzp.RuntimeEnvironment.cpuModel());
    }

    /** The full overlay draw runs headlessly: every stub font/image the overlay touches is a
     * fixture stub, so a pass through the framework's draw hook must not throw. */
    @Test
    void overlayDrawsWithoutRealAssets() {
        LawnApp app = newApp();
        app.mShowFPS = true;
        MemoryImage aScreen = new MemoryImage(null);
        aScreen.create(800, 600);
        app.mWidgetManager.mImage = aScreen;

        Graphics g = new Graphics(aScreen);
        app.drawDebugOverlay(g);
    }
}
