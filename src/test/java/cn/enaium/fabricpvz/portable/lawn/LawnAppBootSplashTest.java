package cn.enaium.fabricpvz.portable.lawn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;
import cn.enaium.fabricpvz.portable.sexy.graphics.MemoryImage;

/**
 * Tests the boot splash (a port addition): the jar resource decodes, cover-scales to the canvas and
 * draws headlessly; the decoded image is cached across frames.
 */
class LawnAppBootSplashTest {

    @Test
    void splashLoadsAndDrawsWithoutRealAssets() {
        LawnApp app = new LawnApp();
        LawnApp.gLawnApp = app;
        cn.enaium.fabricpvz.portable.test.HeadlessTestApp.initialize(app);

        MemoryImage aScreen = new MemoryImage(null);
        aScreen.create(800, 600);

        app.drawBootSplash(new Graphics(aScreen));

        assertNotNull(app.mBootSplashImage, "the jar resource /pvzp/loading.jpg must decode");
        assertEquals(800, app.mBootSplashImage.mWidth, "cover-scaled to the canvas");
        assertEquals(600, app.mBootSplashImage.mHeight);

        // A second draw reuses the cached image (no re-decode per frame).
        MemoryImage aCached = app.mBootSplashImage;
        app.drawBootSplash(new Graphics(aScreen));
        assertSame(aCached, app.mBootSplashImage);

        LawnApp.gLawnApp = null;
    }

    /** A recording-free renderer stub: the hook only needs a non-null backend to present through. */
    private static cn.enaium.fabricpvz.portable.sexy.graphics.RendererBackend stubRenderer() {
        return (cn.enaium.fabricpvz.portable.sexy.graphics.RendererBackend) java.lang.reflect.Proxy
                .newProxyInstance(LawnAppBootSplashTest.class.getClassLoader(),
                        new Class<?>[] {cn.enaium.fabricpvz.portable.sexy.graphics.RendererBackend.class},
                        (theProxy, theMethod, theArgs) -> {
                            switch (theMethod.getName()) {
                                case "renderOutputWidth":
                                case "logicalWidth":
                                    return 800;
                                case "renderOutputHeight":
                                case "logicalHeight":
                                    return 600;
                                case "logicalPresentationRect":
                                    return new int[] {0, 0, 800, 600};
                                case "toString":
                                    return "StubRenderer";
                                default:
                                    Class<?> aType = theMethod.getReturnType();
                                    if (!aType.isPrimitive()) {
                                        return null;
                                    }
                                    if (aType == boolean.class) {
                                        return Boolean.FALSE;
                                    }
                                    if (aType == long.class) {
                                        return 0L;
                                    }
                                    if (aType == float.class) {
                                        return 0.0f;
                                    }
                                    if (aType == double.class) {
                                        return 0.0;
                                    }
                                    return 0;
                            }
                        });
    }

    /** The window-creation hook paints and presents when a real canvas exists, and is a no-op on
     * a test double canvas (bare {@code Image}, not a {@link MemoryImage}). */
    @Test
    void windowCreationHookPaintsTheFreshCanvas() {
        LawnApp app = new LawnApp();
        LawnApp.gLawnApp = app;
        cn.enaium.fabricpvz.portable.test.HeadlessTestApp.initialize(app);
        app.mRendererBackend = stubRenderer();

        // A bare Image test double: the hook must not touch it.
        app.mWidgetManager.mImage = new cn.enaium.fabricpvz.portable.sexy.graphics.Image() {
        };
        app.drawBootSplashOnce();
        assertNull(app.mBootSplashImage, "no canvas to draw on, no decode");

        // The real canvas type: the hook decodes, draws and leaves the splash cached.
        MemoryImage aScreen = new MemoryImage(null);
        aScreen.create(800, 600);
        app.mWidgetManager.mImage = aScreen;
        app.drawBootSplashOnce();
        assertNotNull(app.mBootSplashImage, "the fresh window canvas gets the splash");

        int aCenterPixel = aScreen.getBits()[400 * 800 + 400];
        assertTrue((aCenterPixel & 0x00FFFFFF) != 0, "the splash pixels are on the canvas");

        LawnApp.gLawnApp = null;
    }
}
