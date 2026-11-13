/*
 * Ported from src/Lawn/Widget/NewOptionsDialog.h:37-81 and src/Lawn/Widget/NewOptionsDialog.cpp:39-390.
 *
 * The pause/options dialog: music and sound-effect sliders, a full-screen and a hardware
 * acceleration checkbox, and the almanac / restart / main-menu / back-to-game buttons (plus a
 * disclaimer button in the game-selector variant - a port addition).
 *
 * Porting notes:
 *  - Upstream derives directly from Sexy::Dialog (not LawnDialog); the dialog id stays
 *    Dialogs.DIALOG_NEWOPTIONS.
 *  - The button ids 0..7 keep the upstream enum names.
 *  - Resize() adds the dialog origin (mX/mY) to the dialog-local literals of
 *    NewOptionsDialog.cpp:150-157 (../verification/research/04-reanim-ui.md §13 deviation note 1: upstream
 *    passes un-offset literals even though WidgetManager translates children by their own mX/mY).
 *  - GetPreferredHeight reproduces the upstream quirk of returning the menuback image *width*.
 */
package cn.enaium.fabricpvz.portable.lawn.widget;

import cn.enaium.fabricpvz.portable.lawn.AwardType;
import cn.enaium.fabricpvz.portable.lawn.BoardResult;
import cn.enaium.fabricpvz.portable.lawn.Dialogs;
import cn.enaium.fabricpvz.portable.lawn.DrawStringJustification;
import cn.enaium.fabricpvz.portable.lawn.GameMode;
import cn.enaium.fabricpvz.portable.lawn.GameScenes;
import cn.enaium.fabricpvz.portable.lawn.LawnApp;
import cn.enaium.fabricpvz.portable.lawn.LawnCommon;
import cn.enaium.fabricpvz.portable.lawn.Resources;
import cn.enaium.fabricpvz.portable.lawn.SeedType;
import cn.enaium.fabricpvz.portable.lawn.ZombieType;
import cn.enaium.fabricpvz.portable.pvzp.PvzpCommon;
import cn.enaium.fabricpvz.portable.pvzp.PvzpStringFile;
import cn.enaium.fabricpvz.portable.sexy.Checkbox;
import cn.enaium.fabricpvz.portable.sexy.CheckboxListener;
import cn.enaium.fabricpvz.portable.sexy.Color;
import cn.enaium.fabricpvz.portable.sexy.Dialog;
import cn.enaium.fabricpvz.portable.sexy.KeyCode;
import cn.enaium.fabricpvz.portable.sexy.Slider;
import cn.enaium.fabricpvz.portable.sexy.SliderListener;
import cn.enaium.fabricpvz.portable.sexy.WidgetManager;
import cn.enaium.fabricpvz.portable.sexy.graphics.Font;
import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;

/**
 * Ported from src/Lawn/Widget/NewOptionsDialog.h:37 (declaration) / src/Lawn/Widget/NewOptionsDialog.cpp:39
 * (constructor).
 */
public class NewOptionsDialog extends Dialog implements SliderListener, CheckboxListener {

    // NewOptionsDialog.h:39-48 (protected enum)
    public static final int NewOptionsDialog_Almanac = 0;
    public static final int NewOptionsDialog_MainMenu = 1;
    public static final int NewOptionsDialog_Restart = 2;
    public static final int NewOptionsDialog_Update = 3;
    public static final int NewOptionsDialog_MusicVolume = 4;
    public static final int NewOptionsDialog_SoundVolume = 5;
    public static final int NewOptionsDialog_Fullscreen = 6;
    public static final int NewOptionsDialog_HardwareAcceleration = 7;
    /** A port addition beyond the upstream enum: the auto-pause checkbox. */
    public static final int NewOptionsDialog_AutoPause = 8;
    /** A port addition beyond the upstream enum: the F3 debug-overlay checkbox. */
    public static final int NewOptionsDialog_DebugInfo = 9;
    /** A port addition beyond the upstream enum: the disclaimer button. */
    public static final int NewOptionsDialog_Disclaimer = 10;

    /**
     * The disclaimer body shown in the popup (a port addition, not in upstream). The disclaimer's
     * three sentences, arranged as policy paragraphs (the LawnDialog word-wraps within a paragraph
     * and sizes itself from the wrapped height, LawnDialog.cpp:142-149). The original text ran the
     * sentences together ("unofficialMod"); the missing space is restored.
     */
    public static final String DISCLAIMER_TEXT =
            "This is an unofficial Mod permitted under the Plants vs. Zombies Mod Policy.\n\n"
                    + "Plant vs. Zombies game materials, copyrights, and trademarks are the"
                    + " property of Electronic Arts Inc.\n\n"
                    + "Electronic Arts and PopCap have not endorsed and are not responsible for"
                    + " the operation or content of this Mod.";

    // NewOptionsDialog.cpp:150-157 — layout table (dialog-local coordinates)
    public static final int MUSIC_SLIDER_X = 199;
    public static final int MUSIC_SLIDER_Y = 116;
    public static final int MUSIC_SLIDER_WIDTH = 135;
    public static final int MUSIC_SLIDER_HEIGHT = 40;

    public static final int SFX_SLIDER_X = 199;
    public static final int SFX_SLIDER_Y = 143;
    public static final int SFX_SLIDER_WIDTH = 135;
    public static final int SFX_SLIDER_HEIGHT = 40;

    public static final int HW_ACCEL_CHECKBOX_X = 283;
    public static final int HW_ACCEL_CHECKBOX_Y = 175;
    public static final int HW_ACCEL_CHECKBOX_WIDTH = 46;
    public static final int HW_ACCEL_CHECKBOX_HEIGHT = 45;

    /**
     * The auto-pause checkbox sits in the empty left column of the 3D-acceleration row, the same
     * size and row height as the right-column checkboxes (a port addition, not in upstream). The
     * label reads as on every other row - text left, checkbox right - so the label is right-aligned
     * into the checkbox with the same 9px gap the right column uses (283 checkbox / 274 label).
     * The x is 1.2 checkbox widths right of the column's left edge, as the user tuned it.
     */
    public static final int AUTO_PAUSE_CHECKBOX_X = 130 + (int) (1.0 * HW_ACCEL_CHECKBOX_WIDTH);
    public static final int AUTO_PAUSE_CHECKBOX_Y = HW_ACCEL_CHECKBOX_Y;
    public static final int AUTO_PAUSE_CHECKBOX_WIDTH = HW_ACCEL_CHECKBOX_WIDTH;
    public static final int AUTO_PAUSE_CHECKBOX_HEIGHT = HW_ACCEL_CHECKBOX_HEIGHT;
    /** The auto-pause label's right-alignment anchor: 9px left of its checkbox. */
    public static final int AUTO_PAUSE_LABEL_X = AUTO_PAUSE_CHECKBOX_X - 9;

    public static final int FULLSCREEN_CHECKBOX_X = 284;
    public static final int FULLSCREEN_CHECKBOX_Y = 206;
    public static final int FULLSCREEN_CHECKBOX_WIDTH = 46;
    public static final int FULLSCREEN_CHECKBOX_HEIGHT = 45;

    /**
     * The debug-overlay checkbox sits in the left column, vertically aligned with the fullscreen
     * row (a port addition, not in upstream); same column and label style as the auto-pause one.
     */
    public static final int DEBUG_INFO_CHECKBOX_X = AUTO_PAUSE_CHECKBOX_X;
    public static final int DEBUG_INFO_CHECKBOX_Y = FULLSCREEN_CHECKBOX_Y;
    public static final int DEBUG_INFO_CHECKBOX_WIDTH = AUTO_PAUSE_CHECKBOX_WIDTH;
    public static final int DEBUG_INFO_CHECKBOX_HEIGHT = AUTO_PAUSE_CHECKBOX_HEIGHT;
    public static final int DEBUG_INFO_LABEL_X = DEBUG_INFO_CHECKBOX_X - 9;

    public static final int ALMANAC_BUTTON_X = 107;
    public static final int ALMANAC_BUTTON_Y = 241;
    public static final int OPTIONS_BUTTON_WIDTH = 209;
    public static final int OPTIONS_BUTTON_HEIGHT = 46;
    /** Vertical distance between the almanac, restart and main-menu buttons (NewOptionsDialog.cpp:154-156). */
    public static final int OPTIONS_BUTTON_SPACING_Y = 43;

    public static final int BACK_TO_GAME_BUTTON_X = 30;
    public static final int BACK_TO_GAME_BUTTON_Y = 381;

    // NewOptionsDialog.cpp:159-165 — extra vertical offsets applied when opened from the game selector
    public static final int FROM_SELECTOR_MUSIC_OFFSET_Y = 5;
    public static final int FROM_SELECTOR_SFX_OFFSET_Y = 10;
    public static final int FROM_SELECTOR_HW_ACCEL_OFFSET_Y = 15;
    public static final int FROM_SELECTOR_FULLSCREEN_OFFSET_Y = 20;
    /** NewOptionsDialog.cpp:167-170 — the almanac button is pushed down for zen garden / tree of wisdom. */
    public static final int ZEN_TREE_ALMANAC_OFFSET_Y = 43;

    // NewOptionsDialog.cpp:181-186 (Draw)
    public static final int MUSIC_LABEL_Y = 140;
    public static final int SFX_LABEL_Y = 167;
    public static final int HW_ACCEL_LABEL_Y = 197;
    public static final int FULLSCREEN_LABEL_Y = 229;
    public static final int SLIDER_LABELS_OFFSET_X_DEFAULT = 186;
    public static final int CHECKBOX_LABELS_OFFSET_X_DEFAULT = 274;
    public static final String OPTION_DLG_SLIDER_LABELS_OFFSET_X = "OPTION_DLG_SLIDER_LABELS_OFFSET_X";
    public static final String OPTION_DLG_CHECKBOX_LABELS_OFFSET_X = "OPTION_DLG_CHECKBOX_LABELS_OFFSET_X";
    public static final String OPTION_DLG_LABEL_FONT_SCALE = "OPTION_DLG_LABEL_FONT_SCALE";

    /** The debug-overlay label shares the fullscreen row's label y (NewOptionsDialog.cpp:186). */
    public static final int DEBUG_INFO_LABEL_Y = FULLSCREEN_LABEL_Y;

    // NewOptionsDialog.h:49-59 (fields)
    public LawnApp mApp;
    public Slider mMusicVolumeSlider;
    public Slider mSfxVolumeSlider;
    public Checkbox mFullscreenCheckbox;
    public Checkbox mHardwareAccelerationCheckbox;
    public Checkbox mAutoPauseCheckbox;
    public Checkbox mDebugInfoCheckbox;
    public LawnStoneButton mAlmanacButton;
    public LawnStoneButton mBackToMainButton;
    public LawnStoneButton mRestartButton;
    public LawnStoneButton mDisclaimerButton;
    public NewLawnButton mBackToGameButton;
    public boolean mFromGameSelector;

    /**
     * NewOptionsDialog.cpp:39-113.
     */
    public NewOptionsDialog(LawnApp theApp, boolean theFromGameSelector) {
        super(null, null, Dialogs.DIALOG_NEWOPTIONS, true, "Options", "", "", Dialog.BUTTONS_NONE);

        mApp = theApp;
        mFromGameSelector = theFromGameSelector;
        setButtonTextColor(new Color(255, 255, 100));

        mAlmanacButton = GameButton.makeButton(NewOptionsDialog_Almanac, this, "[VIEW_ALMANAC_BUTTON]");
        // PORT-NOTE: deviation from NewOptionsDialog.cpp:50 — upstream labels this button
        // "[RESTART_LEVEL]", but the retail string table keeps a confirmation question there
        // (RESTART_LEVEL = "你确定想要重新开始这一关卡吗？"), not a label; the button text is
        // banked under RESTART_BUTTON ("重新开始"), so this port resolves that key instead.
        mRestartButton = GameButton.makeButton(NewOptionsDialog_Restart, this, "[RESTART_BUTTON]");
        mBackToMainButton = GameButton.makeButton(NewOptionsDialog_MainMenu, this, "[MAIN_MENU_BUTTON]");
        // A port addition: the disclaimer button lives only in the game-selector variant, one
        // button slot above the credits button. The label, the popup's title and the popup's OK
        // button all resolve the DISCLAIMER key (English default; a LawnStrings entry overrides).
        mDisclaimerButton = GameButton.makeButton(
                NewOptionsDialog_Disclaimer, this, theApp.getString("DISCLAIMER", "Disclaimer"));
        if (!theFromGameSelector) {
            mDisclaimerButton.setVisible(false);
        }

        mBackToGameButton = GameButton.makeNewButton(
                Dialog.ID_OK,
                this,
                "[BACK_TO_GAME]",
                null,
                Resources.getImageById(Resources.IMAGE_OPTIONS_BACKTOGAMEBUTTON0_ID),
                Resources.getImageById(Resources.IMAGE_OPTIONS_BACKTOGAMEBUTTON0_ID),
                Resources.getImageById(Resources.IMAGE_OPTIONS_BACKTOGAMEBUTTON2_ID));
        mBackToGameButton.mTranslateX = 0;
        mBackToGameButton.mTranslateY = 0;
        mBackToGameButton.mTextOffsetX = -2;
        mBackToGameButton.mTextOffsetY = -5;
        mBackToGameButton.mTextDownOffsetX = 0;
        mBackToGameButton.mTextDownOffsetY = 1;
        mBackToGameButton.setFont(Resources.getFontById(Resources.FONT_DWARVENTODCRAFT36GREENINSET_ID));
        mBackToGameButton.setLabelColor(Color.White);
        mBackToGameButton.setLabelHiliteColor(Color.White);
        mBackToGameButton.mHiliteFont = Resources.getFontById(Resources.FONT_DWARVENTODCRAFT36BRIGHTGREENINSET_ID);

        mMusicVolumeSlider = new Slider(
                Resources.getImageById(Resources.IMAGE_OPTIONS_SLIDERSLOT_ID),
                Resources.getImageById(Resources.IMAGE_OPTIONS_SLIDERKNOB2_ID),
                NewOptionsDialog_MusicVolume,
                this);
        double aMusicVolume = theApp.getMusicVolume();
        aMusicVolume = Math.max(0.0, Math.min(1.0, aMusicVolume));
        mMusicVolumeSlider.setValue(aMusicVolume);

        mSfxVolumeSlider = new Slider(
                Resources.getImageById(Resources.IMAGE_OPTIONS_SLIDERSLOT_ID),
                Resources.getImageById(Resources.IMAGE_OPTIONS_SLIDERKNOB2_ID),
                NewOptionsDialog_SoundVolume,
                this);
        mSfxVolumeSlider.setValue(theApp.getSfxVolume() / 0.65);

        mFullscreenCheckbox = LawnCommon.makeNewCheckbox(NewOptionsDialog_Fullscreen, this, !theApp.mIsWindowed);
        mHardwareAccelerationCheckbox = LawnCommon.makeNewCheckbox(NewOptionsDialog_HardwareAcceleration, this, theApp.is3DAccelerated());
        mAutoPauseCheckbox = LawnCommon.makeNewCheckbox(NewOptionsDialog_AutoPause, this, theApp.mAutoPause);
        mDebugInfoCheckbox = LawnCommon.makeNewCheckbox(NewOptionsDialog_DebugInfo, this, theApp.mShowFPS);

        if (mFromGameSelector) {
            mRestartButton.setVisible(false);
            mBackToGameButton.setLabel("[DIALOG_BUTTON_OK]");
            if (showBackToMainAsCredits()) {
                mBackToMainButton.setLabel("[CREDITS]");
            } else {
                mBackToMainButton.setVisible(false);
            }
        }

        if (mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_ICE
                || mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_ZEN_GARDEN
                || mApp.mGameMode == GameMode.GAMEMODE_TREE_OF_WISDOM) {
            mRestartButton.setVisible(false);
        }
        if (mApp.mGameScene == GameScenes.SCENE_LEVEL_INTRO && !mApp.mBoard.mCutScene.isSurvivalRepick()) {
            mRestartButton.setVisible(false);
        }
        if (!mApp.canShowAlmanac()
                || mApp.mGameScene == GameScenes.SCENE_LEVEL_INTRO
                || mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_ZEN_GARDEN
                || mApp.mGameMode == GameMode.GAMEMODE_TREE_OF_WISDOM
                || mFromGameSelector) {
            mAlmanacButton.setVisible(false);
        }
    }

    /**
     * NewOptionsDialog.cpp:85-91 — when opened from the game selector the main-menu button acts as
     * the credits button iff the adventure is finished and the trial stage is not locked.
     */
    public boolean showBackToMainAsCredits() {
        return mApp.hasFinishedAdventure() && !mApp.isTrialStageLocked();
    }

    /**
     * NewOptionsDialog.cpp:214 — the SFX slider's normalised value maps to the stored volume as
     * {@code val * 0.65}. Extracted so the scaling is testable without a live sound system.
     */
    public static double sfxVolumeForSlider(double theVal) {
        return theVal * 0.65;
    }

    // NewOptionsDialog.cpp:116-119 — upstream returns the menuback image *width* (423), not its height.
    @Override
    public int getPreferredHeight(int theWidth) {
        return Resources.getImageById(Resources.IMAGE_OPTIONS_MENUBACK_ID).getWidth();
    }

    // NewOptionsDialog.cpp:121-133
    @Override
    public void addedToManager(WidgetManager theWidgetManager) {
        super.addedToManager(theWidgetManager);
        addWidget(mAlmanacButton);
        addWidget(mRestartButton);
        addWidget(mBackToMainButton);
        addWidget(mDisclaimerButton);
        addWidget(mMusicVolumeSlider);
        addWidget(mSfxVolumeSlider);
        addWidget(mHardwareAccelerationCheckbox);
        addWidget(mFullscreenCheckbox);
        addWidget(mAutoPauseCheckbox);
        addWidget(mDebugInfoCheckbox);
        addWidget(mBackToGameButton);
    }

    // NewOptionsDialog.cpp:135-145
    @Override
    public void removedFromManager(WidgetManager theWidgetManager) {
        super.removedFromManager(theWidgetManager);
        removeWidget(mAlmanacButton);
        removeWidget(mMusicVolumeSlider);
        removeWidget(mSfxVolumeSlider);
        removeWidget(mFullscreenCheckbox);
        removeWidget(mHardwareAccelerationCheckbox);
        removeWidget(mAutoPauseCheckbox);
        removeWidget(mDebugInfoCheckbox);
        removeWidget(mBackToMainButton);
        removeWidget(mDisclaimerButton);
        removeWidget(mBackToGameButton);
        removeWidget(mRestartButton);
    }

    // NewOptionsDialog.cpp:147-171
    @Override
    public void resize(int theX, int theY, int theWidth, int theHeight) {
        super.resize(theX, theY, theWidth, theHeight);

        // Upstream positions these children in the dialog's own coordinate space
        // (NewOptionsDialog.cpp:147-171): the widget tree translates each child by its mX/mY when it
        // draws, so adding the dialog origin here pushed every control outside the dialog's rectangle
        // and the dialog's own clip removed them (sliders clipped to one pixel column, checkboxes to an
        // empty rect, the buttons misplaced).
        mMusicVolumeSlider.resize(MUSIC_SLIDER_X, MUSIC_SLIDER_Y, MUSIC_SLIDER_WIDTH, MUSIC_SLIDER_HEIGHT);
        mSfxVolumeSlider.resize(SFX_SLIDER_X, SFX_SLIDER_Y, SFX_SLIDER_WIDTH, SFX_SLIDER_HEIGHT);
        mHardwareAccelerationCheckbox.resize(HW_ACCEL_CHECKBOX_X, HW_ACCEL_CHECKBOX_Y,
                HW_ACCEL_CHECKBOX_WIDTH, HW_ACCEL_CHECKBOX_HEIGHT);
        mAutoPauseCheckbox.resize(AUTO_PAUSE_CHECKBOX_X, AUTO_PAUSE_CHECKBOX_Y,
                AUTO_PAUSE_CHECKBOX_WIDTH, AUTO_PAUSE_CHECKBOX_HEIGHT);
        mDebugInfoCheckbox.resize(DEBUG_INFO_CHECKBOX_X, DEBUG_INFO_CHECKBOX_Y,
                DEBUG_INFO_CHECKBOX_WIDTH, DEBUG_INFO_CHECKBOX_HEIGHT);
        mFullscreenCheckbox.resize(FULLSCREEN_CHECKBOX_X, FULLSCREEN_CHECKBOX_Y,
                FULLSCREEN_CHECKBOX_WIDTH, FULLSCREEN_CHECKBOX_HEIGHT);
        mAlmanacButton.resize(ALMANAC_BUTTON_X, ALMANAC_BUTTON_Y, OPTIONS_BUTTON_WIDTH, OPTIONS_BUTTON_HEIGHT);
        mRestartButton.resize(mAlmanacButton.mX, mAlmanacButton.mY + OPTIONS_BUTTON_SPACING_Y,
                OPTIONS_BUTTON_WIDTH, OPTIONS_BUTTON_HEIGHT);
        mBackToMainButton.resize(mRestartButton.mX, mRestartButton.mY + OPTIONS_BUTTON_SPACING_Y,
                OPTIONS_BUTTON_WIDTH, OPTIONS_BUTTON_HEIGHT);
        // Port addition: one button slot above the credits/main-menu button (the slot the restart
        // button occupies in level; in the selector variant restart is hidden, so the slot is free).
        mDisclaimerButton.resize(mBackToMainButton.mX, mBackToMainButton.mY - OPTIONS_BUTTON_SPACING_Y,
                OPTIONS_BUTTON_WIDTH, OPTIONS_BUTTON_HEIGHT);
        mBackToGameButton.resize(BACK_TO_GAME_BUTTON_X, BACK_TO_GAME_BUTTON_Y,
                mBackToGameButton.mWidth, mBackToGameButton.mHeight);

        if (mFromGameSelector) {
            mMusicVolumeSlider.mY += FROM_SELECTOR_MUSIC_OFFSET_Y;
            mSfxVolumeSlider.mY += FROM_SELECTOR_SFX_OFFSET_Y;
            mHardwareAccelerationCheckbox.mY += FROM_SELECTOR_HW_ACCEL_OFFSET_Y;
            mAutoPauseCheckbox.mY += FROM_SELECTOR_HW_ACCEL_OFFSET_Y;
            mDebugInfoCheckbox.mY += FROM_SELECTOR_FULLSCREEN_OFFSET_Y;
            mFullscreenCheckbox.mY += FROM_SELECTOR_FULLSCREEN_OFFSET_Y;
        }

        if (mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_ZEN_GARDEN
                || mApp.mGameMode == GameMode.GAMEMODE_TREE_OF_WISDOM) {
            mAlmanacButton.mY += ZEN_TREE_ALMANAC_OFFSET_Y;
        }
    }

    // NewOptionsDialog.cpp:173-201
    @Override
    public void draw(Graphics g) {
        g.drawImage(Resources.getImageById(Resources.IMAGE_OPTIONS_MENUBACK_ID), 0, 0);

        int aMusicOffset = 0;
        int aSfxOffset = 0;
        int a3DAccelOffset = 0;
        int aFullScreenOffset = 0;
        if (mFromGameSelector) {
            aMusicOffset = FROM_SELECTOR_MUSIC_OFFSET_Y;
            aSfxOffset = FROM_SELECTOR_SFX_OFFSET_Y;
            a3DAccelOffset = FROM_SELECTOR_HW_ACCEL_OFFSET_Y;
            aFullScreenOffset = FROM_SELECTOR_FULLSCREEN_OFFSET_Y;
        }
        Color aTextColor = new Color(107, 109, 145);

        int aSliderLabelsX = mApp.getInteger(OPTION_DLG_SLIDER_LABELS_OFFSET_X, SLIDER_LABELS_OFFSET_X_DEFAULT);
        int aCheckboxLabelsX = mApp.getInteger(OPTION_DLG_CHECKBOX_LABELS_OFFSET_X, CHECKBOX_LABELS_OFFSET_X_DEFAULT);
        float aFontScale = mApp.mDoubleProperties.getOrDefault(OPTION_DLG_LABEL_FONT_SCALE, 1.0).floatValue();
        if (aFontScale != 1.0f) {
            g.setScale(aFontScale, aFontScale, 0.0f, 0.0f);
        }
        Font aFont = Resources.getFontById(Resources.FONT_DWARVENTODCRAFT18_ID);
        PvzpCommon.pvzpDrawString(g, mApp.getString("OPTIONS_MUSIC_LABEL", "Music"), aSliderLabelsX, MUSIC_LABEL_Y + aMusicOffset,
                aFont, aTextColor, DrawStringJustification.DS_ALIGN_RIGHT);
        PvzpCommon.pvzpDrawString(g, mApp.getString("OPTIONS_SOUNDFX", "Sound FX"), aSliderLabelsX, SFX_LABEL_Y + aSfxOffset,
                aFont, aTextColor, DrawStringJustification.DS_ALIGN_RIGHT);
        PvzpCommon.pvzpDrawString(g, mApp.getString("OPTIONS_3D_ACCELERATION", "3D Acceleration"), aCheckboxLabelsX,
                HW_ACCEL_LABEL_Y + a3DAccelOffset, aFont, aTextColor, DrawStringJustification.DS_ALIGN_RIGHT);
        // The auto-pause checkbox shares the 3D-acceleration row from the left column; like every
        // other row, its label sits to the left of the checkbox (right-aligned into it).
        PvzpCommon.pvzpDrawString(g, mApp.getString("OPTIONS_AUTO_PAUSE", "Auto Pause"), AUTO_PAUSE_LABEL_X,
                HW_ACCEL_LABEL_Y + a3DAccelOffset, aFont, aTextColor, DrawStringJustification.DS_ALIGN_RIGHT);
        // The debug-overlay checkbox is aligned with the fullscreen row from the left column.
        PvzpCommon.pvzpDrawString(g, mApp.getString("OPTIONS_DEBUG_INFO", "Debug Info"), DEBUG_INFO_LABEL_X,
                DEBUG_INFO_LABEL_Y + aFullScreenOffset, aFont, aTextColor, DrawStringJustification.DS_ALIGN_RIGHT);
        PvzpCommon.pvzpDrawString(g, mApp.getString("OPTIONS_FULL_SCREEN", "Full Screen"), aCheckboxLabelsX,
                FULLSCREEN_LABEL_Y + aFullScreenOffset, aFont, aTextColor, DrawStringJustification.DS_ALIGN_RIGHT);
        if (aFontScale != 1.0f) {
            g.setScale(1.0f, 1.0f, 0.0f, 0.0f);
        }
    }

    // NewOptionsDialog.cpp:203-221
    @Override
    public void sliderVal(int theId, double theVal) {
        switch (theId) {
            case NewOptionsDialog_MusicVolume:
                mApp.setMusicVolume(theVal);
                mApp.mSoundSystem.rehookupSoundWithMusicVolume();
                break;

            case NewOptionsDialog_SoundVolume:
                mApp.setSfxVolume(sfxVolumeForSlider(theVal));
                mApp.mSoundSystem.rehookupSoundWithMusicVolume();
                if (!mSfxVolumeSlider.mDragging) {
                    mApp.playSample(Resources.SOUND_BUTTONCLICK_ID);
                }
                break;
        }
    }

    // NewOptionsDialog.cpp:223-281
    @Override
    public void checkboxChecked(int theId, boolean checked) {
        switch (theId) {
            case NewOptionsDialog_Fullscreen:
                if (!checked && mApp.mForceFullscreen) {
                    mApp.doDialog(
                            Dialogs.DIALOG_COLORDEPTH_EXP,
                            true,
                            mApp.getString("NO_WINDOWED_MODE", "No Windowed Mode"),
                            mApp.getString("INVALID_WINDOWS_MODE",
                                    "Windowed mode is only available if your desktop was running in either\n"
                                            + "16 bit or 32 bit color mode when you started the game.\n\n"
                                            + "If you'd like to run in Windowed mode then you need to quit the game and switch your desktop to 16 or 32 bit color mode."),
                            "[DIALOG_BUTTON_OK]",
                            Dialog.BUTTONS_FOOTER);

                    mFullscreenCheckbox.setChecked(true, false);
                }
                break;

            case NewOptionsDialog_AutoPause:
                // Applied immediately (the flag is read on the next focus change); the framework's
                // mute-on-lost-focus rides along so leaving the window also silences music and
                // sound. Persisted with the rest of the registry values when the game exits.
                mApp.mAutoPause = checked;
                mApp.mMuteOnLostFocus = checked;
                break;

            case NewOptionsDialog_DebugInfo:
                // Applied immediately (the overlay draws or not on the very next frame); F3 toggles
                // the same flag at runtime, and it is persisted as the DebugInfo registry value.
                mApp.mShowFPS = checked;
                break;

            case NewOptionsDialog_HardwareAcceleration:
                if (checked) {
                    if (!mApp.is3DAccelerationSupported()) {
                        mHardwareAccelerationCheckbox.setChecked(false, false);
                        mApp.doDialog(
                                Dialogs.DIALOG_INFO,
                                true,
                                mApp.getString("NOT_SUPPORTED", "Not Supported"),
                                mApp.getString("HARDWARE_ACCELERATION_NOT_SUPPORTED",
                                        "Hardware Acceleration cannot be enabled on this computer.\n\n"
                                                + "Your video card does not\n"
                                                + "meet the minimum requirements\n"
                                                + "for this game."),
                                "[DIALOG_BUTTON_OK]",
                                Dialog.BUTTONS_FOOTER);
                    } else if (!mApp.is3DAccelerationRecommended()) {
                        mApp.doDialog(
                                Dialogs.DIALOG_INFO,
                                true,
                                mApp.getString("DIALOG_WARNING", "Warning"),
                                mApp.getString("SLOW_PERFORMANCE",
                                        "Your video card may not fully support this feature.\n\n"
                                                + "If you experience slower performance, please disable Hardware Acceleration."),
                                "[DIALOG_BUTTON_OK]",
                                Dialog.BUTTONS_FOOTER);
                    }
                }
                break;
        }
    }

    // NewOptionsDialog.cpp:283-298
    @Override
    public void keyDown(int theKey) {
        if (mApp.mBoard != null) {
            mApp.mBoard.doTypingCheck(theKey);
        }

        if (theKey == KeyCode.KEYCODE_SPACE || theKey == KeyCode.KEYCODE_RETURN) {
            super.buttonDepress(Dialog.ID_OK);
        } else if (theKey == KeyCode.KEYCODE_ESCAPE) {
            super.buttonDepress(Dialog.ID_CANCEL);
        }
    }

    // NewOptionsDialog.cpp:300-303
    @Override
    public void buttonPress(int theId) {
        mApp.playSample(Resources.SOUND_GRAVEBUTTON_ID);
    }

    // NewOptionsDialog.cpp:305-390
    @Override
    public void buttonDepress(int theId) {
        super.buttonDepress(theId);

        switch (theId) {
            case NewOptionsDialog_Almanac: {
                mApp.doAlmanacDialog(SeedType.SEED_NONE, ZombieType.ZOMBIE_INVALID).waitForResult(true);
                break;
            }

            case NewOptionsDialog_MainMenu: {
                if (mFromGameSelector) {
                    mApp.killNewOptionsDialog();
                    mApp.killGameSelector();
                    mApp.showAwardScreen(AwardType.AWARD_CREDITS_ZOMBIENOTE, false);
                } else if (mApp.mBoard != null && mApp.mBoard.needSaveGame()) {
                    mApp.doConfirmBackToMain();
                } else if (mApp.mBoard != null && mApp.mBoard.mCutScene != null && mApp.mBoard.mCutScene.isSurvivalRepick()) {
                    mApp.doConfirmBackToMain();
                } else {
                    mApp.mBoardResult = BoardResult.BOARDRESULT_QUIT;
                    mApp.doBackToMain();
                }
                break;
            }

            case NewOptionsDialog_Restart: {
                if (mApp.mBoard != null) {
                    String aDialogTitle;
                    String aDialogMessage;
                    if (mApp.isPuzzleMode()) {
                        aDialogTitle = "[RESTART_PUZZLE_HEADER]";
                        aDialogMessage = "[RESTART_PUZZLE_BODY]";
                    } else if (mApp.isChallengeMode()) {
                        aDialogTitle = "[RESTART_CHALLENGE_HEADER]";
                        aDialogMessage = "[RESTART_CHALLENGE_BODY]";
                    } else if (mApp.isSurvivalMode()) {
                        aDialogTitle = "[RESTART_SURVIVAL_HEADER]";
                        aDialogMessage = "[RESTART_SURVIVAL_BODY]";
                    } else {
                        aDialogTitle = "[RESTART_LEVEL_HEADER]";
                        aDialogMessage = "[RESTART_LEVEL_BODY]";
                    }

                    LawnDialog aDialog = (LawnDialog) mApp.doDialog(
                            Dialogs.DIALOG_CONFIRM_RESTART, true, aDialogTitle, aDialogMessage, "", Dialog.BUTTONS_YES_NO);
                    aDialog.mLawnYesButton.mLabel = mApp.getString("RESTART_LABEL", "RESTART");
                    aDialog.mLawnNoButton.mLabel = PvzpStringFile.translate("[DIALOG_BUTTON_CANCEL]");

                    // PORT-NOTE: NewOptionsDialog.cpp:366-380 — upstream answers with
                    // Dialog::WaitForResult(), which pumps the message loop until a button is
                    // depressed (Dialog.cpp:410-441). This port's waitForResult returns without
                    // pumping (sexy/Dialog.java:506), so the answer arrives in
                    // LawnApp.restartLevelFromOptions(), called from LawnApp.buttonDepress.
                }
                break;
            }

            case NewOptionsDialog_Update:
                // NewOptionsDialog.cpp:388-390 — upstream creates no button with this id (dead case).
                mApp.checkForUpdates();
                break;

            case NewOptionsDialog_Disclaimer: {
                // Port addition: the standard LawnDialog popup; the title carries the DISCLAIMER
                // text, the body is the disclaimer arranged as policy paragraphs, and the button
                // is the standard OK confirmation (DIALOG_BUTTON_OK).
                String aTitle = mApp.getString("DISCLAIMER", "Disclaimer");
                mApp.doDialog(Dialogs.DIALOG_INFO, true, aTitle, DISCLAIMER_TEXT,
                        "[DIALOG_BUTTON_OK]", Dialog.BUTTONS_FOOTER);
                break;
            }
        }
    }
}
