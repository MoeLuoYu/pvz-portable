package cn.enaium.fabricpvz.portable.lawn;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import cn.enaium.fabricpvz.portable.sexy.Common;
import cn.enaium.fabricpvz.portable.sexy.Dialog;

/**
 * Tests the auto-pause preference (a port addition): the default, the focus-loss/gain pause of the
 * board, the modal guard, and the registry persistence behind the options dialog's checkbox.
 */
class LawnAppAutoPauseTest {

    /** A headless app whose reanimations look loaded (the BoardTest fixture pattern). */
    private static final class LoadedReanimApp extends LawnApp {
        @Override
        public cn.enaium.fabricpvz.portable.pvzp.reanim.Reanimation addReanimation(
                float theX, float theY, int theRenderOrder, int theReanimationType) {
            cn.enaium.fabricpvz.portable.pvzp.reanim.Reanimation aReanim =
                    super.addReanimation(theX, theY, theRenderOrder, theReanimationType);
            cn.enaium.fabricpvz.portable.test.HeadlessTestApp.stubLoadedTracks(aReanim);
            return aReanim;
        }

        /** The focus transition exactly as the platform event path runs it (the mute lives in
         * RehupFocus, not in the LostFocus hook the app overrides). */
        void loseFocusLikeThePlatformDoes() {
            mActive = false;
            rehupFocus();
        }

        void gainFocusLikeThePlatformDoes() {
            mActive = true;
            rehupFocus();
        }
    }

    private static LoadedReanimApp newApp() {
        LoadedReanimApp app = new LoadedReanimApp();
        LawnApp.gLawnApp = app;
        app.mPlayerInfo = app.mProfileMgr.addProfile("AutoPauseTest");
        cn.enaium.fabricpvz.portable.test.HeadlessTestApp.initialize(app);
        return app;
    }

    private static Board newBoard(LawnApp theApp) {
        theApp.mGameMode = GameMode.GAMEMODE_ADVENTURE;
        Board aBoard = new Board(theApp);
        theApp.mBoard = aBoard;
        return aBoard;
    }

    @Test
    void autoPauseDefaultsToOn() {
        assertTrue(newApp().mAutoPause, "the original game always paused on focus loss");
    }

    @Test
    void focusLossOpensTheSpaceStylePauseDialogWhenAutoPauseIsOn() {
        LawnApp app = newApp();
        Board aBoard = newBoard(app);

        app.lostFocus();

        assertTrue(aBoard.mPaused, "leaving the window pauses the board");
        assertNotNull(app.getDialog(Dialogs.DIALOG_PAUSED),
                "the pause is the newspaper-zombie pause dialog the Space key opens");
    }

    @Test
    void focusLossKeepsTheGameRunningWhenAutoPauseIsOff() {
        LawnApp app = newApp();
        app.mAutoPause = false;
        Board aBoard = newBoard(app);

        app.lostFocus();

        assertFalse(aBoard.mPaused, "the preference off keeps the current no-pause behavior");
        assertNull(app.getDialog(Dialogs.DIALOG_PAUSED), "no pause dialog either");
    }

    @Test
    void resumingTheAutoPauseIsThePlayersJob() {
        LawnApp app = newApp();
        Board aBoard = newBoard(app);

        app.lostFocus();
        assertTrue(aBoard.mPaused);
        app.gotFocus();

        assertNotNull(app.getDialog(Dialogs.DIALOG_PAUSED),
                "coming back must not auto-resume: the pause dialog stays for the player");

        // The player presses the dialog's resume button (or Space): the dialog closes and the
        // modal-close hook resumes the board.
        app.killDialog(Dialogs.DIALOG_PAUSED);
        assertFalse(aBoard.mPaused, "the dialog's own close path resumes the board");
    }

    @Test
    void focusLossUnderTheSeedChooserDoesNotPause() {
        LawnApp app = newApp();
        Board aBoard = newBoard(app);
        // canPauseNow refuses while the seed chooser is on screen (LawnApp.cpp:583-585) - the same
        // gate the Space key respects.
        app.mSeedChooserScreen = new cn.enaium.fabricpvz.portable.lawn.widget.SeedChooserScreen();
        app.mSeedChooserScreen.mMouseVisible = true;

        app.lostFocus();

        assertFalse(aBoard.mPaused, "no auto pause where Space would not pause either");
        assertNull(app.getDialog(Dialogs.DIALOG_PAUSED));
    }

    @Test
    void focusGainDoesNotUnpauseUnderAModal() {
        LawnApp app = newApp();
        Board aBoard = newBoard(app);

        // The options dialog itself is a modal: AddDialog opens it and the modal-open hook pauses
        // the board (SexyAppBase:2415-2418 -> LawnApp.modalOpen); the window can lose and regain
        // focus while it stays open.
        app.doDialog(Dialogs.DIALOG_MESSAGE, true, "Test", "Body", "", Dialog.BUTTONS_FOOTER);
        assertTrue(aBoard.mPaused, "a modal pauses the board (LawnApp.cpp:1100-1106)");

        app.lostFocus();
        app.gotFocus();

        assertTrue(aBoard.mPaused, "the modal's pause survives a focus round trip");
        app.killDialog(Dialogs.DIALOG_MESSAGE);
        assertFalse(aBoard.mPaused, "closing the modal resumes");
    }

    @Test
    void focusChangesWithoutABoardDoNothing() {
        LawnApp app = newApp();
        app.mBoard = null;

        app.lostFocus();
        app.gotFocus();
    }

    /** With the auto pause on, leaving the window also silences music and sfx - even on the main
     * menu, where there is no board and the pause dialog never applies. The mute lives in the
     * framework's focus transition (RehupFocus), so the test drives the platform-faithful path. */
    @Test
    void focusLossAlsoMutesAudioWhenAutoPauseIsOn() {
        LoadedReanimApp app = newApp();
        assertFalse(app.isMuted());

        app.loseFocusLikeThePlatformDoes();
        assertTrue(app.isMuted(), "the focus mute rides along with the auto pause");

        app.gainFocusLikeThePlatformDoes();
        assertFalse(app.isMuted(), "regaining focus restores the volumes");
    }

    @Test
    void focusLossWithAutoPauseOffDoesNotMute() {
        LoadedReanimApp app = newApp();
        // What the options checkbox does when untoggled.
        app.mAutoPause = false;
        app.mMuteOnLostFocus = false;

        app.loseFocusLikeThePlatformDoes();
        assertFalse(app.isMuted(), "the preference off keeps the audio playing");
    }

    /** The mute flag mirrors whatever the persisted auto-pause preference reads back as. */
    @Test
    void focusMuteFollowsThePersistedPreference(@TempDir Path theTempDir) throws IOException {
        Path aDir = Files.createDirectories(theTempDir.resolve("appdata"));
        String aPreviousFolder = Common.getAppDataFolder();
        try {
            LawnApp aWriter = newApp();
            Common.setAppDataFolder(aDir.toString());
            aWriter.readFromRegistry();
            aWriter.mAutoPause = false;
            aWriter.writeToRegistry();

            LawnApp aReader = newApp();
            Common.setAppDataFolder(aDir.toString());
            aReader.readFromRegistry();

            assertFalse(aReader.mAutoPause, "the written preference is read back");
            assertFalse(aReader.mMuteOnLostFocus, "the focus mute mirrors the auto pause preference");
        } finally {
            Common.setAppDataFolder(aPreviousFolder);
            LawnApp.gLawnApp = null;
        }
    }

    /** The preference survives a restart through the emulated registry (LawnApp.cpp:313-331). */
    @Test
    void autoPausePersistsAcrossRestarts(@TempDir Path theTempDir) throws IOException {
        Path aDir = Files.createDirectories(theTempDir.resolve("appdata"));
        Path anEmptyDir = Files.createDirectories(theTempDir.resolve("empty"));
        String aPreviousFolder = Common.getAppDataFolder();
        try {
            // HeadlessTestApp.initialize (inside newApp) points the app data folder at its own
            // fixture directory, so the folder is re-pointed after every app is built - mirroring
            // the boot lifecycle, where ReadFromRegistry (which binds the emulated registry file)
            // runs before any write.
            LawnApp aFirst = newApp();
            Common.setAppDataFolder(aDir.toString());
            aFirst.readFromRegistry();
            aFirst.mAutoPause = false;
            aFirst.writeToRegistry();

            assertTrue(Files.exists(aDir.resolve("registry.regemu")), "the registry must be written");

            LawnApp aSecond = newApp();
            Common.setAppDataFolder(aDir.toString());
            aSecond.readFromRegistry();
            assertFalse(aSecond.mAutoPause, "the written preference must be read back");

            LawnApp aThird = newApp();
            Common.setAppDataFolder(anEmptyDir.toString());
            aThird.readFromRegistry();
            assertTrue(aThird.mAutoPause, "an absent value keeps the default");
        } finally {
            Common.setAppDataFolder(aPreviousFolder);
            LawnApp.gLawnApp = null;
        }
    }
}
