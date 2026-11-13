package cn.enaium.fabricpvz.portable.pvzp;

/**
 * The runtime environment around the game process: how many mods the loader has loaded, the
 * loader's own version, and the hardware the OS names for the F3 debug overlay.
 *
 * <p>The game's own sources are Fabric-free by design, so this class reads plain system properties
 * that the launch glue fills in - the same channel the version json already uses for the game
 * directory. System properties are JVM-global, which matters here: the provider runs on the launch
 * class path's loader while every {@code cn.enaium.fabricpvz.portable.*} class is defined a second
 * time by Knot's class loader, so a static field the provider writes would live in a copy the game
 * never sees. Outside the loader (tools, tests, a direct {@code Main.main} run) nothing sets the
 * properties, and every getter answers with its default.
 */
public final class RuntimeEnvironment {

    /** System property carrying how many top-level mods the loader loaded (an integer). */
    public static final String LOADED_MODS_PROPERTY = "pvz.modsLoaded";

    /** System property carrying the Fabric Loader's version string. */
    public static final String LOADER_VERSION_PROPERTY = "pvz.loaderVersion";

    /** The cached {@link #cpuModel()} probe; "" = probed, not found. */
    private static volatile String mCpuModel;

    private RuntimeEnvironment() {
    }

    /**
     * Publishes how many top-level mods the loader loaded from the mods directory. The provider
     * calls this after resolving; tests and tools may call it to simulate an installation.
     */
    public static void setLoadedMods(int theCount) {
        System.setProperty(LOADED_MODS_PROPERTY, Integer.toString(Math.max(0, theCount)));
    }

    /**
     * The mod count the selector's caption shows: the game itself counts as one, plus every
     * top-level mod the loader loaded on top (the loader is not counted - it is the thing doing the
     * loading, and the caption's second line already names it). Nothing published (a run without
     * the loader) reads as the bare one.
     */
    public static int totalMods() {
        String aRaw = System.getProperty(LOADED_MODS_PROPERTY);
        if (aRaw == null) {
            return 1;
        }

        try {
            return 1 + Math.max(0, Integer.parseInt(aRaw.trim()));
        } catch (NumberFormatException anException) {
            return 1;
        }
    }

    /**
     * Publishes the Fabric Loader's version string; the provider calls this after resolving (the
     * game asks through {@link #loaderVersion()}).
     */
    public static void setLoaderVersion(String theVersion) {
        if (theVersion != null && !theVersion.isBlank()) {
            System.setProperty(LOADER_VERSION_PROPERTY, theVersion.trim());
        }
    }

    /**
     * The Fabric Loader's version ("0.19.5"), or an empty string when nothing published it (a run
     * without the loader).
     */
    public static String loaderVersion() {
        String aVersion = System.getProperty(LOADER_VERSION_PROPERTY);
        return aVersion == null ? "" : aVersion.trim();
    }

    /**
     * The CPU's marketing model name ("AMD Ryzen 9 5900HX with Radeon Graphics"), probed once from
     * the OS and cached; empty when the platform does not tell (the caller falls back to the
     * architecture). Each platform answers in its own way: the Windows registry, Linux's
     * {@code /proc/cpuinfo}, macOS's {@code sysctl}.
     */
    public static String cpuModel() {
        String aModel = mCpuModel;
        if (aModel == null) {
            aModel = probeCpuModel();
            mCpuModel = aModel;
        }
        return aModel;
    }

    private static String probeCpuModel() {
        String anOs = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        if (anOs.contains("win")) {
            return registryValue("HKLM\\HARDWARE\\DESCRIPTION\\System\\CentralProcessor\\0",
                    "ProcessorNameString");
        }
        if (anOs.contains("linux")) {
            try {
                for (String aLine : java.nio.file.Files.readAllLines(java.nio.file.Path.of("/proc/cpuinfo"))) {
                    if (aLine.startsWith("model name")) {
                        int aColon = aLine.indexOf(':');
                        if (aColon >= 0) {
                            return aLine.substring(aColon + 1).trim();
                        }
                    }
                }
            } catch (Exception anException) {
                // fall through to empty
            }
            return "";
        }
        if (anOs.contains("mac")) {
            return runCommand("sysctl", "-n", "machdep.cpu.brand_string");
        }
        return "";
    }

    /**
     * Reads one string value out of the Windows registry through {@code reg query}; empty on any
     * failure. {@code reg} is a fast native tool that ships with every Windows, unlike the
     * deprecated {@code wmic}.
     */
    private static String registryValue(String theKey, String theValueName) {
        String anOutput = runCommand("reg", "query", theKey, "/v", theValueName);
        if (anOutput.isEmpty()) {
            return "";
        }

        for (String aLine : anOutput.split("\n")) {
            int aMarker = aLine.indexOf("REG_SZ");
            if (aMarker >= 0) {
                return aLine.substring(aMarker + "REG_SZ".length()).trim();
            }
        }
        return "";
    }

    /** Runs a command to completion and returns its output; empty on any failure. */
    private static String runCommand(String... theCommand) {
        try {
            Process aProcess = new ProcessBuilder(theCommand).redirectErrorStream(true).start();
            if (!aProcess.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)) {
                aProcess.destroyForcibly();
                return "";
            }
            return new String(aProcess.getInputStream().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception anException) {
            return "";
        }
    }
}
