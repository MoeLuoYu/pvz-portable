package cn.enaium.fabricpvz.portable.pvzp.platform;

import cn.enaium.fabricpvz.portable.sexy.graphics.Image;

/**
 * Platform (window / input / misc OS services) abstraction used by the framework.
 *
 * <p>The production implementation is {@code cn.enaium.fabricpvz.portable.pvzp.platform.sdl.SdlPlatform}, built on
 * SDL3 through the LWJGL {@code lwjgl-sdl} bindings (the upstream port equivalent is
 * {@code src/SexyAppFramework/platform/default/Window.cpp} plus {@code Input.cpp}). Tests use
 * recording/dummy implementations, which keeps the framework and game logic unit testable without a
 * display.
 *
 * <p>Threading: every method must be called from the thread that created the backend (upstream rule
 * for SDL event pumping on macOS).
 */
public interface PlatformBackend {

    /**
     * Window/context creation options.
     *
     * <p>{@code fullscreen} is applied when the window is created: upstream passes it as
     * {@code SDL_WINDOW_FULLSCREEN_DESKTOP} into {@code SDL_CreateWindow}
     * (platform/default/Window.cpp:59-60), so a stored screen mode is in effect from the first frame
     * instead of only after the next {@code switchScreenMode}.
     */
    record Options(int width, int height, String title, boolean resizable, boolean hidden,
                   boolean fullscreen) {
    }

    /** System cursor shapes used by the framework ({@code SexyAppBase::SetCursor}). */
    enum CursorType {
        ARROW,
        HAND,
        IBEAM,
        CROSSHAIR,
        HRESIZE,
        VRESIZE,
        NOT_ALLOWED
    }

    /** Creates the window (and renderer/audio devices lazily used by other subsystems). */
    void init(Options options);

    /** Polls the next pending event into {@code out}; returns false when the queue is empty. */
    boolean pollEvent(PlatformEvent out);

    /** Presents the current frame (upstream {@code GLInterface::SwapBuffers}). */
    void swapBuffers();

    /** Monotonic milliseconds; upstream {@code SDL_GetTicks()}. */
    long ticksMillis();

    /** Sleeps like upstream's {@code nanosleep} pacer ({@link cn.enaium.fabricpvz.portable.sexy.SexyAppBase} frame limiter). */
    void sleepMillis(long millis, int nanos);

    void setTitle(String title);

    /**
     * Sets the window/taskbar icon from top-down RGBA pixels ({@code rgba.length == width * height *
     * 4}); no effect when the window was never created, and a no-op by default so test backends need
     * not implement it.
     *
     * <p>PORT-NOTE (deviation): the retail Windows build showed its icon from the executable's
     * resources ({@code IDI_MAIN_ICON}); an SDL window carries none unless one is set explicitly, so
     * this port applies the artwork extracted from the retail executable (bundled at {@code
     * /pvzp/window-icon.png}, applied by {@code SexyAppBase.applyWindowIcon}).
     */
    default void setWindowIcon(int width, int height, byte[] rgba) {
    }

    /**
     * Applies a whole icon group (a complete {@code .ico} file's bytes) on top of {@link
     * #setWindowIcon}: the retail icon group shows a different member in the small (title bar) and
     * the big (taskbar) slot - a complete flower at 16x16, the zombie hand at 32x32 and up - and
     * only the platform's own icon selection can honor that. A no-op by default; the SDL
     * implementation runs it through user32's {@code WM_SETICON} on Windows only, so on the other
     * platforms the surface from {@code setWindowIcon} covers every use.
     */
    default void setWindowIconGroup(byte[] icoFile) {
    }

    /**
     * Switches the existing window between windowed and fullscreen.
     *
     * <p>Upstream does this inside {@code Window::MakeWindow} (platform/default/Window.cpp:44-47, the
     * {@code SDL_SetWindowFullscreen} call) so a screen-mode switch reuses the window. Rebuilding the
     * platform instead fails on the second {@code init} ("platform already initialized").
     */
    default void setFullscreen(boolean theFullscreen) {
    }

    int windowWidth();

    int windowHeight();

    /** Window size in physical pixels (HiDPI aware). */
    int pixelWidth();

    int pixelHeight();

    void setClipboardText(String text);

    String getClipboardText();

    /** Sets a system cursor shape. */
    void setCursor(CursorType cursor);

    /** Sets a custom cursor image with hotspot offset; pass {@code null} to return to the system cursor. */
    void setCustomCursor(Image cursorImage, int hotX, int hotY);

    /** Hides or shows the cursor (upstream {@code CursorObject}/ingame cursor handling). */
    void hideCursor(boolean hide);

    /** Shows a modal message box (upstream {@code SDL_ShowSimpleMessageBox}). */
    void showMessageBox(String title, String message, boolean isError);

    /** Opens a URL in the default browser (upstream {@code OpenURL}). */
    void openURL(String url);

    /**
     * Whether the window currently has keyboard/input focus. Only meaningful after {@link #init};
     * defaults to true so test backends need not implement it.
     *
     * <p>PORT-NOTE (deviation): used by the framework to align its focus state at boot — a window
     * can spend its whole early life unfocused (the game launched behind the active window), and the
     * framework's {@code mActive} default never learns about it from focus events alone.
     */
    default boolean hasInputFocus() {
        return true;
    }

    /** Destroys the window and releases platform resources. */
    void shutdown();
}
