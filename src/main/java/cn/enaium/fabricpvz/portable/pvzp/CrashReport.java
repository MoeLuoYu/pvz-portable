package cn.enaium.fabricpvz.portable.pvzp;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Random;

import cn.enaium.fabricpvz.portable.BuildInfo;
import cn.enaium.fabricpvz.portable.sexy.Common;
import cn.enaium.fabricpvz.portable.sexy.SexyAppBase;

/**
 * The crash report writer (a port addition, after Minecraft's): an uncaught exception writes a
 * {@code crash-reports/crash-<date>_<time>-client.txt} file into the game directory, shaped like a
 * Minecraft crash report - a one-line quip under the header (a different one every crash, picked
 * at random from {@link #CRASH_COMMENTS}, the way Minecraft's own second line behaves), the time
 * and description, the stack trace, and a system-details block.
 *
 * <p>The report is plain text in English, like Minecraft's; the game directory is the
 * {@code -Dpvz.gameDir} system property (the same one the logs live under), defaulting to the
 * working directory.
 */
public final class CrashReport {

    /**
     * The line-two quips: the Tree of Wisdom's own quotes (the user-supplied pool), one of which
     * lands under the header of every crash report - fitting advice for a game that just fell over,
     * and a different one every crash.
     */
    public static final String[] CRASH_COMMENTS = {
        "Chompers and wall-nuts work exceedingly well together. It's no surprise, considering they were roommates in college.",
        "If you ever listen to anything I say, listen to this: you want two columns of sunflowers. I'm dead serious here.",
        "Snorkel zombies. I hate 'em. How do I deal? Wall-nuts on lily pads, that's how.",
        "Pssst! Try typing 'future' while playing to experience zombies... from the FUTURE!",
        "How many cherry bombs does it take to take down a Gargantuar? Here's a hint: more than one, fewer than three. Here's a more explicit hint: Two.",
        "If you're looking for mushroom plants for your Zen Garden, you'll have better luck playing on levels where it's nighttime.",
        "I wouldn't worry about permanently damaging your lawn with doom-shrooms. In time the earth heals itself.",
        "Have you tried clicking on the flowers on the main menu? Give it a shot! I'll wait here.",
        "Legend has it that frozen zombies eat slower. I'm here to tell that legend has its facts straight.",
        "Have you heard of the elusive Yeti Zombie? Some say he likes hiding where it's pitch black.",
        "What's cheaper than free? Nothing! That's why puff-shrooms are essential on all night levels!",
        "Are you hoping to find water plants for your Zen Garden? I bet my phloem you'll have the most luck searching in pool levels.",
        "Have you noticed that Gargantuars sometimes use OTHER ZOMBIES to bash your plants?  Whatever works, I guess.",
        "Stinky the Snail sure loves his chocolate. Maybe loves it a little too much, you know? He won't sit still for an hour after he's had some.",
        "If you think playing survival 'endless' mode only drops pool-style plants for your Zen Garden, think again!  It drops everything-style.",
        "Often the question is asked: where do you find chocolate? A better question would be: where DON'T you find chocolate? It drops in every game mode!",
        "Grave Busters, eh? Pick 'em only when you can see graves on the right side of the screen along with the zombies. That's what I do.",
        "I've heard that Buckethead zombies take five times as many hits as regular ones.",
        "I hear that typing 'mustache' brings about a terrifying transformation in the undead!",
        "Do multiple Snow-peas in a row slow zombies down more than just one? The sad but truthful answer is 'Nay.'",
        "You know that zombies emerge from gravestones, right? So what's stopping you from using grave busters to get rid of them in Survival night? Is it pride?",
        "If you're looking for the inside info on how long a level's going to be, count the flags on the level meter. That'll set you up real nice.",
        "Roof cleaners. Classic items. Can't recommend them highly enough. Best thing about them? They give you a shot at beating Pogo Party.",
        "If you're wondering if feeding a hypno-shroom to a dancer zombie compels him to summon backup dancers for you, bet it all on 'Yes.'",
        "Make Money Fast! By Playing Survival Endless! Then E-mail Me Your Bank Account Number!",
        "You'd think torchwoods would douse snow peas. And you'd be correct, because you, my friend, are one smart cookie.",
        "Those hateful ZomBotany zombies! Who do they think they are, shooting at your plants? It's a good thing wall-nuts stop 'em cold.",
        "The Pogo Party and Bobsled Bonanza mini-games are really, really, really difficult. Wanna drop one of the 'reallys' off that description? Use the squash.",
        "Just when you thought jalapenos couldn't be any more useful, a Tree of Wisdom lets you know that they also destroy the Zomboni's ice trails! BAM!",
        "Once you buy the imitater, try clicking the little drawing in the upper left corner of your Almanac to access the entry on that sucker.",
        "The number of coins you receive in Wall-nut Bowling is proportional to how cool you are as measured by how many ricochets per nut you can pull off.",
        "Please do not tap on the glass! Or actually, go ahead; right-click on your Aquarium Garden or during Zombiquarium to deafen your underwater creatures.",
        "When I was just an acorn my grampa told me, 'Son, Vasebreaker puzzles are much easier if you break the vases on the right side first.'",
        "Dancers in I, Zombie may seem expensive, but in the right situation they're worth every penny.",
        "I had a dream. In it, cattail spikes popped balloons and dropped zombies to the ground. I don't know what it means.",
        "Growing aquatic plants in your Zen Garden is pretty much impossible without the Aquarium Garden. Just saying.",
        "Digger zombies violate the natural order with their subterranean ways. It's only fair to use magnet-shrooms to steal their mining picks.",
        "Every day brings new challenges and opportunities. Oh, and new marigolds in Crazy Dave's shop.",
        "Mushroom Garden! Huh! What is it good for? Absolutely nothin'! Except growing mushrooms, that is.",
        "Tired? Depressed? Ladders on tall-nuts getting you down? A quick magnet-shroom will whisk your cares away!",
        "The tallness of tall-nuts earns widespread acclaim due to their effectiveness vs. Dolphin Riders and Pogo Zombies.",
        "The explosive force of a cherry bomb or jalapeno is more than capable of dislodging a ladder from a Wall-nut.",
        "It's tempting to feed all your chocolate to Stinky the Snail. He's such a chocolate hog. But remember: Zen Garden plants like chocolate too!",
        "Torchwood fire is hotter than rage, but Zombonis, screen doors, ladders and catapults can take the heat.",
        "If you rely on upgrade plants in Survival: Endless, be acutely aware that they get more expensive the more you have on your lawn.",
        "The Imps in I, Zombie seem weak. But they're speedy and great for fetching that last brain when you've cleared the rest of the opposition.",
        "If you type 'trickedout,' don't be surprised if you see something wacky happen to your lawnmowers.",
        "Type 'daisies' to get the zombies to leave tiny daisies behind when they die.",
        "Type 'dance' to get the zombies to boogie on down!",
        "Type 'pinata' to make zombies spit out candy when destroyed!",
    };

    /** The Time line's format ({@code Time: 2023-08-14 20:02:39}). */
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT);

    /** The crash file's name format ({@code crash-2023-08-14_20.02.39-client.txt}). */
    private static final DateTimeFormatter FILE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss", Locale.ROOT);

    private CrashReport() {
    }

    /** The quip at {@code theIndex}, wrapping around (a different one every crash via the pick). */
    public static String crashComment(int theIndex) {
        return CRASH_COMMENTS[Math.floorMod(theIndex, CRASH_COMMENTS.length)];
    }

    /** How many quips there are. */
    public static int crashCommentCount() {
        return CRASH_COMMENTS.length;
    }

    /** A quip picked at random - Minecraft's per-crash variety. */
    public static String pickCrashComment(Random theRandom) {
        return crashComment(theRandom.nextInt(CRASH_COMMENTS.length));
    }

    /**
     * Writes a crash report into the game directory's {@code crash-reports/} and returns its path.
     *
     * @param theTitle     the {@code Description:} line ("Unexpected error", like Minecraft's
     *                     client frame crashes)
     * @param theThrowable the crash's cause
     * @param theThread    the thread that crashed (the {@code -- Head --} section names it)
     */
    public static Path write(String theTitle, Throwable theThrowable, Thread theThread) {
        String aComment = pickCrashComment(new Random());
        String aContent = build(theTitle, theThrowable,
                theThread == null ? "unknown" : theThread.getName(), aComment, LocalDateTime.now());

        Path aReports = crashReportsDirectory();
        Path aFile = aReports.resolve(String.format(Locale.ROOT, "crash-%s-client.txt",
                LocalDateTime.now().format(FILE_FORMAT)));

        try {
            Files.createDirectories(aReports);
            Files.writeString(aFile, aContent);
        } catch (IOException anException) {
            // A crash report that cannot be written is logged and abandoned: the crash itself
            // already happened, and the handler must not crash about its own report.
            Common.logErrorLn("Failed to write the crash report", anException);
            return aFile;
        }
        return aFile;
    }

    /** The directory crash reports are written into: the game directory's {@code crash-reports/}. */
    public static Path crashReportsDirectory() {
        String aGameDir = System.getProperty("pvz.gameDir");
        return (aGameDir == null || aGameDir.isBlank() ? Paths.get(".") : Paths.get(aGameDir))
                .toAbsolutePath().normalize().resolve("crash-reports");
    }

    /** The report's full text; see the class javadoc for the shape. */
    public static String build(String theTitle, Throwable theThrowable, String theThreadName,
            String theComment, LocalDateTime theTime) {
        StringBuilder aReport = new StringBuilder();
        aReport.append("---- PvZ-Portable Crash Report ----\n");
        aReport.append("// ").append(theComment).append('\n');
        aReport.append('\n');
        aReport.append("Time: ").append(theTime.format(TIME_FORMAT)).append('\n');
        aReport.append("Description: ").append(theTitle).append('\n');
        aReport.append('\n');
        aReport.append(stackTraceOf(theThrowable));
        aReport.append("\n\n");
        aReport.append("A detailed walkthrough of the error, its code path and all known details is as follows:\n");
        aReport.append("---------------------------------------------------------------------------------------\n");
        aReport.append('\n');
        aReport.append("-- Head --\n");
        aReport.append("Thread: ").append(theThreadName).append('\n');
        aReport.append("Stacktrace:\n");
        aReport.append(stackTraceOf(theThrowable));
        aReport.append('\n');
        aReport.append("-- System Details --\n");
        aReport.append("Details:\n");
        for (String aLine : systemDetailsLines()) {
            aReport.append('\t').append(aLine).append('\n');
        }
        return aReport.toString();
    }

    /** The {@code java.lang.Throwable} rendering a crash report carries ({@code at} lines tabbed). */
    private static String stackTraceOf(Throwable theThrowable) {
        StringWriter aWriter = new StringWriter();
        theThrowable.printStackTrace(new PrintWriter(aWriter));
        return aWriter.toString();
    }

    /**
     * The {@code -- System Details --} block, one line each: the game's identity, the JVM's, the
     * memory picture, the CPU (probed the same way the F3 overlay probes it), the loader and the
     * mods, and what is drawn into.
     */
    public static String[] systemDetailsLines() {
        return new String[] {
            "PvZ-Portable Version: " + BuildInfo.version(),
            "Operating System: " + System.getProperty("os.name", "?") + " ("
                    + System.getProperty("os.arch", "?") + ") version "
                    + System.getProperty("os.version", "?"),
            "Java Version: " + System.getProperty("java.version", "?") + ", "
                    + System.getProperty("java.vendor", "?"),
            "Java VM Version: " + System.getProperty("java.vm.name", "?") + " ("
                    + System.getProperty("java.vm.info", "?") + "), "
                    + System.getProperty("java.vm.vendor", "?"),
            memoryLine(),
            "CPUs: " + Runtime.getRuntime().availableProcessors(),
            cpuLine(),
            jvmFlagsLine(),
            "Fabric Loader: " + (RuntimeEnvironment.loaderVersion().isEmpty()
                    ? "?" : RuntimeEnvironment.loaderVersion()),
            "Mods: " + RuntimeEnvironment.totalMods(),
            rendererLine(),
            windowLine(),
            "Is Modded: Definitely; 'pvz-portable' runs as a Fabric Loader mod",
            "Type: Client",
        };
    }

    /**
     * Minecraft's memory picture: used / allocated / maximum, each in bytes and MiB -
     * {@code Memory: 314105424 bytes (299 MiB) / 939524096 bytes (896 MiB) up to 7012876288 bytes (6688 MiB)}.
     */
    public static String memoryLine() {
        Runtime aRuntime = Runtime.getRuntime();
        long aUsed = aRuntime.totalMemory() - aRuntime.freeMemory();
        long anAllocated = aRuntime.totalMemory();
        long aMax = aRuntime.maxMemory();
        return String.format(Locale.ROOT, "Memory: %d bytes (%d MiB) / %d bytes (%d MiB) up to %d bytes (%d MiB)",
                aUsed, aUsed / 1048576, anAllocated, anAllocated / 1048576, aMax, aMax / 1048576);
    }

    /** The CPU line, the F3 overlay's ({@code CPU: 16x <model or architecture>}). */
    public static String cpuLine() {
        String aModel = RuntimeEnvironment.cpuModel();
        return "CPU: " + Runtime.getRuntime().availableProcessors() + "x"
                + (aModel.isEmpty() ? " " + System.getProperty("os.arch", "?") : " " + aModel);
    }

    /** The JVM's input arguments, Minecraft-style ({@code JVM Flags: 11 total; -Xmx6679m ...}). */
    public static String jvmFlagsLine() {
        java.util.List<String> aFlags =
                java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments();
        return "JVM Flags: " + aFlags.size() + " total; " + String.join(" ", aFlags);
    }

    /** The renderer's name, or {@code ?} when there is none yet (the crash came early). */
    private static String rendererLine() {
        SexyAppBase anApp = SexyAppBase.current;
        if (anApp != null && anApp.mRendererBackend != null) {
            String aName = anApp.mRendererBackend.rendererName();
            if (aName != null && !aName.isEmpty()) {
                return "Renderer: " + aName;
            }
        }
        return "Renderer: ?";
    }

    /** The window size in points, or nothing meaningful before the platform opened it. */
    private static String windowLine() {
        SexyAppBase anApp = SexyAppBase.current;
        if (anApp != null && anApp.mPlatformBackend != null) {
            return "Window size: " + anApp.mPlatformBackend.windowWidth() + "x"
                    + anApp.mPlatformBackend.windowHeight();
        }
        return "Window size: ?";
    }
}
