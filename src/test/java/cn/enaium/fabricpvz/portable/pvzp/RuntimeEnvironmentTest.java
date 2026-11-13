package cn.enaium.fabricpvz.portable.pvzp;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests {@link RuntimeEnvironment}: the loader version's publish/read channel and the mod count
 * the main menu's caption shows (the game itself counts as one; the loader is not counted).
 */
class RuntimeEnvironmentTest {

    @AfterEach
    void clearProperties() {
        System.clearProperty(RuntimeEnvironment.LOADED_MODS_PROPERTY);
        System.clearProperty(RuntimeEnvironment.LOADER_VERSION_PROPERTY);
    }

    @Test
    void loaderVersionPassesThroughAndDefaultsToEmpty() {
        assertEquals("", RuntimeEnvironment.loaderVersion(), "nothing published reads empty");

        RuntimeEnvironment.setLoaderVersion("0.19.5");
        assertEquals("0.19.5", RuntimeEnvironment.loaderVersion());

        // A blank version (no container found) leaves the last published value alone.
        RuntimeEnvironment.setLoaderVersion(" ");
        assertEquals("0.19.5", RuntimeEnvironment.loaderVersion());
    }

    @Test
    void totalModsDefaultsToTheBareOne() {
        assertEquals(1, RuntimeEnvironment.totalMods());
    }

    @Test
    void totalModsAddsThePublishedTopLevelMods() {
        RuntimeEnvironment.setLoadedMods(3);
        assertEquals(4, RuntimeEnvironment.totalMods());

        RuntimeEnvironment.setLoadedMods(0);
        assertEquals(1, RuntimeEnvironment.totalMods());
    }

    @Test
    void aNegativeOrUnparsableModCountReadsAsZero() {
        RuntimeEnvironment.setLoadedMods(-4);
        assertTrue(Integer.parseInt(System.getProperty(RuntimeEnvironment.LOADED_MODS_PROPERTY)) >= 0);
        assertEquals(1, RuntimeEnvironment.totalMods());

        System.setProperty(RuntimeEnvironment.LOADED_MODS_PROPERTY, "not-a-number");
        assertEquals(1, RuntimeEnvironment.totalMods());
    }
}
