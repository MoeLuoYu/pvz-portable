package cn.enaium.fabricpvz.portable.pvzp;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

import cn.enaium.fabricpvz.portable.sexy.pak.PakInterface;

/**
 * Locates the real Plants vs. Zombies assets for integration tests.
 *
 * <p>Assets may be supplied with {@code -Dpvz.test.resources=<dir>}; otherwise the standard
 * {@link ResourceLocator} search runs (working directory, executable directory, local Steam
 * installation). Tests that need real assets must be gated with
 * {@code Assumptions.assumeTrue(...)} so the suite stays green on machines without the game.
 */
public final class TestAssets {

    /** The retail variant the resource root holds. */
    public enum ResourceVariant {
        /** The Steam/English retail: images/titlescreen.png, sounds/diamond.ogg. */
        ENGLISH_RETAIL,
        /** The Chinese GOTY disc retail: the same manifest with some containers renamed —
         *  images/titlescreen.jpg, sounds/diamond.au, data/_ContinuumBold14.png. */
        CHINESE_GOTY
    }

    private TestAssets() {
    }

    public static Optional<Path> resourceRoot() {
        String override = System.getProperty("pvz.test.resources", "");
        if (!override.isBlank()) {
            Path path = Path.of(override);
            return ResourceLocator.isValidResourceRoot(path) ? Optional.of(path) : Optional.empty();
        }
        return Optional.ofNullable(ResourceLocator.discover(new String[0]).resourceRoot());
    }

    /**
     * Fingerprints a mounted pak. The two variants ship the same manifest but rename some
     * containers; the fingerprint keys on the title screen (PNG in the English retail, JPG in the
     * Chinese GOTY). An unknown layout answers {@link ResourceVariant#ENGLISH_RETAIL} — the
     * variant the suite's golden expectations were calibrated with.
     */
    public static ResourceVariant variantOf(PakInterface thePak) throws IOException {
        return thePak.exists("images/TITLESCREEN.JPG")
                ? ResourceVariant.CHINESE_GOTY
                : ResourceVariant.ENGLISH_RETAIL;
    }

    /** Resolves a resource relative to the real resource root, case-insensitively. */
    public static Optional<Path> resolve(String relative) throws IOException {
        Optional<Path> root = resourceRoot();
        if (root.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(cn.enaium.fabricpvz.portable.sexy.pak.PakSource.resolveCaseInsensitive(root.get().resolve(relative)));
    }
}
