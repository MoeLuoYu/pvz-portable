package cn.enaium.fabricpvz.portable.pvzp.platform.sdl;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.lwjgl.system.MemoryUtil;

/**
 * The Windows path of the icon group ({@code /pvzp/window-icon.ico}): the retail icon group carries
 * different members for the small (title bar) and the big (taskbar) icon - a complete flower at
 * 16x16, the zombie hand at 32x32 and up - and SDL's {@code SDL_SetWindowIcon} cannot show two of
 * them at once (it scales one surface for both). The group therefore goes through user32 directly:
 * {@code CreateIconFromResourceEx} builds the HICON of the member that matches the system metric
 * size and {@code WM_SETICON} applies it, which is what loading the executable's own resources did
 * for the retail build.
 *
 * <p>PORT-NOTE (deviation): no upstream equivalent - the upstream Windows build carried these icons
 * in its executable's resources. Nothing here is touched off Windows ({@link #available()}), and the
 * SDL surface icon ({@code SdlPlatform.setWindowIcon}) stays as the cross-platform baseline.
 */
final class Win32WindowIcon {

    private static final int WM_SETICON = 0x0080;
    private static final int ICON_SMALL = 0;
    private static final int ICON_BIG = 1;
    private static final int SM_CXICON = 11;
    private static final int SM_CXSMICON = 49;
    private static final int ICON_VERSION = 0x00030000;

    private Win32WindowIcon() {
    }

    /** True on Windows, the only system this path applies to; everywhere else the SDL icon stays. */
    static boolean available() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    /**
     * Applies the group's small and big icons to the window. Returns false when the file is not an
     * icon container or user32 could not be used, in which case the SDL surface icon stays in place.
     * Any failure down the way is swallowed here: a decorative icon must never take the launch down.
     */
    static boolean apply(long hwnd, byte[] icoFile) {
        try {
            ByteBuffer aHeader = ByteBuffer.wrap(icoFile).order(ByteOrder.LITTLE_ENDIAN);
            if (aHeader.getShort() != 0 || aHeader.getShort() != 1) {
                return false; // reserved word must be 0 and the type must be 1 (icon)
            }
            int aCount = aHeader.getShort() & 0xFFFF;
            if (aCount <= 0) {
                return false;
            }
            boolean aSmallApplied = sendIcon(hwnd, ICON_SMALL, systemMetric(SM_CXSMICON), aHeader, aCount,
                    icoFile);
            aHeader.position(6); // the second pass reads the same entry table from its start
            boolean aBigApplied = sendIcon(hwnd, ICON_BIG, systemMetric(SM_CXICON), aHeader, aCount,
                    icoFile);
            return aSmallApplied || aBigApplied;
        } catch (Throwable theThrowable) {
            return false;
        }
    }

    /** Picks the group member closest to {@code theSize} (bit depth breaks ties) and applies it. */
    private static boolean sendIcon(long hwnd, int theFlag, int theSize, ByteBuffer theEntries,
            int theCount, byte[] theFile) {
        int aBestDelta = Integer.MAX_VALUE;
        int aBestBitCount = 0;
        int aBestOffset = 0;
        int aBestLength = 0;
        for (int i = 0; i < theCount; i++) {
            int aWidth = theEntries.get() & 0xFF;    // 0 encodes 256
            int aHeight = theEntries.get() & 0xFF;
            theEntries.get();                        // color count
            theEntries.get();                        // reserved
            theEntries.getShort();                   // planes
            int aBitCount = theEntries.getShort() & 0xFFFF;
            int aLength = theEntries.getInt();
            int anOffset = theEntries.getInt();
            int aSide = Math.max(aWidth, aHeight) == 0 ? 256 : Math.max(aWidth, aHeight);
            int aDelta = Math.abs(aSide - theSize);
            if (aDelta < aBestDelta || (aDelta == aBestDelta && aBitCount > aBestBitCount)) {
                aBestDelta = aDelta;
                aBestBitCount = aBitCount;
                aBestOffset = anOffset;
                aBestLength = aLength;
            }
        }
        if (aBestOffset == 0) {
            return false;
        }
        // The blob goes into its own native buffer: CreateIconFromResourceEx wants a pointer, and
        // MemoryStack's default 64 KB thread stack is too small for the 256x256 members.
        ByteBuffer aBlob = MemoryUtil.memAlloc(aBestLength);
        try {
            aBlob.put(theFile, aBestOffset, aBestLength).flip();
            long anIcon = createIconFromResourceEx(MemoryUtil.memAddress(aBlob), aBestLength, theSize,
                    theSize);
            if (anIcon == 0) {
                return false;
            }
            sendMessage(hwnd, WM_SETICON, theFlag, anIcon);
            return true;
        } catch (Throwable theThrowable) {
            return false; // the SDL surface icon stays in place
        } finally {
            MemoryUtil.memFree(aBlob);
        }
    }

    /** The user32 functions, bound lazily (and only ever touched on Windows) in a holder. */
    private static final class User32 {
        private static final SymbolLookup LOOKUP = SymbolLookup.libraryLookup("user32", Arena.ofShared());
        private static final Linker LINKER = Linker.nativeLinker();

        private static MethodHandle downcall(String theName, FunctionDescriptor theDescriptor) {
            return LINKER.downcallHandle(LOOKUP.find(theName).orElseThrow(), theDescriptor);
        }

        /** HICON CreateIconFromResourceEx(PBYTE, DWORD, BOOL, DWORD, int, int, UINT). */
        static final MethodHandle CREATE_ICON_FROM_RESOURCE_EX = downcall("CreateIconFromResourceEx",
                FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT,
                        ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
                        ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));

        /** LRESULT SendMessageW(HWND, UINT, WPARAM, LPARAM). */
        static final MethodHandle SEND_MESSAGE_W = downcall("SendMessageW",
                FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT,
                        ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG));

        /** int GetSystemMetrics(int). */
        static final MethodHandle GET_SYSTEM_METRICS = downcall("GetSystemMetrics",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
    }

    private static int systemMetric(int theIndex) {
        try {
            return (int) User32.GET_SYSTEM_METRICS.invokeExact(theIndex);
        } catch (Throwable theThrowable) {
            throw new IllegalStateException("GetSystemMetrics failed", theThrowable);
        }
    }

    private static long createIconFromResourceEx(long theAddress, int theLength, int theWidth, int theHeight) {
        try {
            return (long) User32.CREATE_ICON_FROM_RESOURCE_EX.invokeExact(theAddress, theLength,
                    1 /* fIcon */, ICON_VERSION, theWidth, theHeight, 0 /* LR_DEFAULTCOLOR */);
        } catch (Throwable theThrowable) {
            throw new IllegalStateException("CreateIconFromResourceEx failed", theThrowable);
        }
    }

    private static void sendMessage(long hwnd, int theMessage, long theWParam, long theLParam) {
        try {
            // invokeExact is type-polymorphic: the handle is (HWND, UINT msg, WPARAM, LPARAM) ->
            // LRESULT, so the message arrives as int and the long return has to be consumed even
            // though LRESULT carries nothing this path needs.
            long aResult = (long) User32.SEND_MESSAGE_W.invokeExact(hwnd, theMessage, theWParam,
                    theLParam);
        } catch (Throwable theThrowable) {
            throw new IllegalStateException("SendMessageW failed", theThrowable);
        }
    }
}
