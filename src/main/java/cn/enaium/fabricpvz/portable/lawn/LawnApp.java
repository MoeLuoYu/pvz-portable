/*
 * Ported from src/LawnApp.h:28-344 and src/LawnApp.cpp:1-3268.
 *
 * The application object: owns the game scene/state machine, the Board, the
 * profile/adventure progress, the screen widgets (TitleScreen/GameSelector/
 * SeedChooserScreen/AwardScreen/CreditScreen/ChallengeScreen), the dialog list,
 * music/foley playback and the cheat-key TypingChecks.
 *
 * PORT-NOTE: upstream keeps the process-wide application pointer in the file
 * global `gLawnApp` (LawnApp.h:342) and the framework keeps `gSexyAppBase`/
 * `gSexyApp`; the Java port mirrors this with the public static fields
 * {@link #gLawnApp} (this class) and the inherited
 * {@code cn.enaium.fabricpvz.portable.sexy.SexyAppBase.current} (framework base).
 */
package cn.enaium.fabricpvz.portable.lawn;

import cn.enaium.fabricpvz.portable.lawn.hooks.LawnHooksRegistry;
import cn.enaium.fabricpvz.portable.lawn.system.Music;
import cn.enaium.fabricpvz.portable.lawn.system.PlayerInfo;
import cn.enaium.fabricpvz.portable.lawn.system.PoolEffect;
import cn.enaium.fabricpvz.portable.lawn.system.PottedPlant;
import cn.enaium.fabricpvz.portable.lawn.system.ProfileMgr;
import cn.enaium.fabricpvz.portable.lawn.system.ReanimatorCache;
import cn.enaium.fabricpvz.portable.lawn.system.SaveGame;
import cn.enaium.fabricpvz.portable.lawn.system.TypingCheck;
import cn.enaium.fabricpvz.portable.lawn.widget.AlmanacDialog;
import cn.enaium.fabricpvz.portable.lawn.widget.AwardScreen;
import cn.enaium.fabricpvz.portable.lawn.widget.ChallengeDefinition;
import cn.enaium.fabricpvz.portable.lawn.widget.ChallengeScreen;
import cn.enaium.fabricpvz.portable.lawn.widget.CheatDialog;
import cn.enaium.fabricpvz.portable.lawn.widget.ContinueDialog;
import cn.enaium.fabricpvz.portable.lawn.widget.CreditScreen;
import cn.enaium.fabricpvz.portable.lawn.widget.GameSelector;
import cn.enaium.fabricpvz.portable.lawn.widget.LawnDialog;
import cn.enaium.fabricpvz.portable.lawn.widget.NewOptionsDialog;
import cn.enaium.fabricpvz.portable.lawn.widget.NewUserDialog;
import cn.enaium.fabricpvz.portable.lawn.widget.ReportAchievement;
import cn.enaium.fabricpvz.portable.lawn.widget.SeedChooserScreen;
import cn.enaium.fabricpvz.portable.lawn.widget.StoreScreen;
import cn.enaium.fabricpvz.portable.lawn.widget.TitleScreen;
import cn.enaium.fabricpvz.portable.lawn.widget.UserDialog;
import cn.enaium.fabricpvz.portable.lawn.widget.ZombatarTOS;
import cn.enaium.fabricpvz.portable.pvzp.PvzpCommon;
import cn.enaium.fabricpvz.portable.pvzp.PvzpResourceManager;
import cn.enaium.fabricpvz.portable.pvzp.PvzpStringFile;
import cn.enaium.fabricpvz.portable.pvzp.RuntimeEnvironment;
import cn.enaium.fabricpvz.portable.pvzp.fx.FilterEffect;
import cn.enaium.fabricpvz.portable.pvzp.fx.PvzpFoley;
import cn.enaium.fabricpvz.portable.pvzp.fx.PvzpParticleDefinitions;
import cn.enaium.fabricpvz.portable.pvzp.fx.TrailDefinitions;
import cn.enaium.fabricpvz.portable.pvzp.fx.EffectSystem;
import cn.enaium.fabricpvz.portable.pvzp.fx.FoleyType;
import cn.enaium.fabricpvz.portable.pvzp.fx.PvzpParticleSystem;
import cn.enaium.fabricpvz.portable.pvzp.reanim.AttachEffect;
import cn.enaium.fabricpvz.portable.pvzp.reanim.Attachment;
import cn.enaium.fabricpvz.portable.pvzp.reanim.Reanimation;
import cn.enaium.fabricpvz.portable.pvzp.reanim.Reanimator;
import cn.enaium.fabricpvz.portable.pvzp.reanim.ReanimatorTrackInstance;
import cn.enaium.fabricpvz.portable.sexy.Color;
import cn.enaium.fabricpvz.portable.sexy.Common;
import cn.enaium.fabricpvz.portable.sexy.Dialog;
import cn.enaium.fabricpvz.portable.sexy.KeyCode;
import cn.enaium.fabricpvz.portable.sexy.Rect;
import cn.enaium.fabricpvz.portable.sexy.SexyApp;
import cn.enaium.fabricpvz.portable.sexy.graphics.Font;
import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;
import cn.enaium.fabricpvz.portable.sexy.graphics.Image;
import cn.enaium.fabricpvz.portable.sexy.graphics.MemoryImage;

/**
 * Ported from src/LawnApp.h:67 and src/LawnApp.cpp.
 *
 * <p>Global flags from the upstream LawnApp translation unit (LawnApp.cpp:82-85).
 */
public class LawnApp extends SexyApp {

    // =========================================================================
    // File-scope globals (LawnApp.cpp:82-85)
    // =========================================================================

    /** Upstream {@code bool gIsPartnerBuild}. */
    public static boolean gIsPartnerBuild = false;

    /** Upstream {@code bool gSlowMo}. */
    public static boolean gSlowMo = false;

    /** Upstream {@code bool gFastMo}. */
    public static boolean gFastMo = false;

    /**
     * PORT-NOTE (deviation): the "fast" hidden cheat word's 2× game speed toggle, new to this port
     * (upstream {@code gFastMo} runs 20 updates per frame, which is far beyond double speed).
     */
    public static boolean gCheatFastMo = false;

    /** Upstream {@code LawnApp* gLawnApp} (LawnApp.h:342). Assigned by the constructor. */
    public static LawnApp gLawnApp = null;

    /** Upstream {@code int gSlowMoCounter}. */
    public static int gSlowMoCounter = 0;

    private static int sLastPlayTimeTick = -1;

    /**
     * Upstream {@code LevelStats} (LawnApp.h:57-66). Only {@code mUnusedLawnMowers} exists in the
     * shipped source; the class is kept as a nested type because LawnApp owns it.
     */
    public static class LevelStats {
        public int mUnusedLawnMowers;

        public LevelStats() {
            reset();
        }

        public void reset() {
            mUnusedLawnMowers = 0;
        }
    }

    // =========================================================================
    // Fields (LawnApp.h:75-160)
    // =========================================================================

    public Board mBoard;
    public TitleScreen mTitleScreen;
    public GameSelector mGameSelector;
    public SeedChooserScreen mSeedChooserScreen;
    public AwardScreen mAwardScreen;
    public CreditScreen mCreditScreen;
    public ChallengeScreen mChallengeScreen;
    public PvzpFoley mSoundSystem;
    public String mReferId = "";
    public String mRegisterLink = "";
    public String mMod = "";
    public boolean mRegisterResourcesLoaded;
    public boolean mCheatKeys;
    public int mGameMode;
    public int mGameScene;
    public boolean mLoadingZombiesThreadCompleted;
    public boolean mFirstTimeGameSelector;
    public int mGamesPlayed;
    public int mMaxExecutions;
    public int mMaxPlays;
    public int mMaxTime;
    public boolean mEasyPlantingCheat;
    /**
     * PORT-NOTE (deviation): the "lazyclick" hidden cheat word's auto-collect toggle, new to this
     * port — when on, every sun, coin and pick-up on the board is collected without clicking.
     */
    public boolean mAutoCollectCheat;
    /**
     * The auto-pause preference (a port addition; upstream always paused on focus loss,
     * LawnApp.cpp:299-311): when on, leaving the window pauses the board, when off the game keeps
     * running without focus. Toggled by the options dialog, persisted as the {@code AutoPause}
     * registry value.
     */
    public boolean mAutoPause;
    public PoolEffect mPoolEffect;
    public ZenGarden mZenGarden;
    public EffectSystem mEffectSystem;
    public ReanimatorCache mReanimatorCache;
    public ProfileMgr mProfileMgr;
    public PlayerInfo mPlayerInfo;
    public LevelStats mLastLevelStats;
    /** Upstream {@code std::atomic<bool> mCloseRequest} (LawnApp.h:104). */
    public volatile boolean mCloseRequest;
    public int mAppCounter;
    public Music mMusic;
    public int mCrazyDaveReanimID;
    public int mCrazyDaveState;
    public int mCrazyDaveBlinkCounter;
    public int mCrazyDaveBlinkReanimID;
    public int mCrazyDaveMessageIndex;
    public String mCrazyDaveMessageText = "";
    public int mAppRandSeed;
    public long mSessionID;
    public int mPlayTimeActiveSession;
    public int mPlayTimeInactiveSession;
    public int mBoardResult;
    public boolean mSawYeti;
    public TypingCheck mKonamiCheck;
    public TypingCheck mMustacheCheck;
    public TypingCheck mMoustacheCheck;
    public TypingCheck mSuperMowerCheck;
    public TypingCheck mSuperMowerCheck2;
    public TypingCheck mFutureCheck;
    public TypingCheck mPinataCheck;
    public TypingCheck mDanceCheck;
    public TypingCheck mDaisyCheck;
    public TypingCheck mSukhbirCheck;
    // PORT-NOTE (deviation): seventeen hidden cheat words new to this port
    // (cheatplant/lazyclick/wingame/killzom/killplant/killall/cherryboom/fast/love/froze/
    // retrack/dotrack/fillhoe/final/nextlevel/unlockall/tmpunlock), typed with the same
    // TypingCheck machinery as the upstream words above.
    public TypingCheck mCheatPlantCheck;
    public TypingCheck mLazyClickCheck;
    public TypingCheck mWinGameCheck;
    public TypingCheck mKillZomCheck;
    public TypingCheck mKillPlantCheck;
    public TypingCheck mKillAllCheck;
    public TypingCheck mCherryBoomCheck;
    public TypingCheck mFastCheck;
    public TypingCheck mLoveCheck;
    public TypingCheck mFrozeCheck;
    public TypingCheck mReTrackCheck;
    public TypingCheck mDoTrackCheck;
    public TypingCheck mFillHoeCheck;
    public TypingCheck mFinalCheck;
    public TypingCheck mNextLevelCheck;
    public TypingCheck mTmpUnlockCheck;
    public TypingCheck mUnlockAllCheck;
    public boolean mMustacheMode;
    public boolean mSuperMowerMode;
    public boolean mFutureMode;
    public boolean mPinataMode;
    public boolean mDanceMode;
    public boolean mDaisyMode;
    public boolean mSukhbirMode;
    public int mTrialType;
    /**
     * PORT-NOTE (deviation): the -trial save's unlock flag ([TRIAL_UNLOCKED] click with no backend)
     * — persisted in the isolated trial save's registry (savedata_trial/registry.regemu), so the
     * next launch with this save starts as the full version.
     */
    public boolean mTrialUnlocked;
    public boolean mDebugTrialLocked;
    public boolean mMuteSoundsForCutscene;

    // =========================================================================
    // Construction / destruction (LawnApp.cpp:114-225)
    // =========================================================================

    // LawnApp::LawnApp (LawnApp.cpp:114)
    public LawnApp() {
        super();

        // Replace the base-class resource manager with the PvZP-capable subclass.
        mResourceManager = new PvzpResourceManager(this);
        // Upstream reaches gSexyAppBase from the definition loader; the Java port installs this
        // resolver instead (needed before any reanim/particle definition file is parsed).
        ((PvzpResourceManager) mResourceManager).installDefinitionResourceResolver();

        gLawnApp = this;

        mBoard = null;
        mMustacheMode = false;
        mSuperMowerMode = false;
        mFutureMode = false;
        mPinataMode = false;
        mDanceMode = false;
        mDaisyMode = false;
        mSukhbirMode = false;
        mGameScene = GameScenes.SCENE_LOADING;
        mCloseRequest = false;
        mWidth = GameConstants.BOARD_WIDTH;
        mHeight = GameConstants.BOARD_HEIGHT;
        mFullscreenBits = 32;
        mAppCounter = 0;
        mAppRandSeed = (int) (System.currentTimeMillis() / 1000L);
        mTrialType = TrialType.TRIALTYPE_NONE;
        mDebugTrialLocked = false;
        mMuteSoundsForCutscene = false;
        mMusicVolume = 0.85;
        mSfxVolume = 0.5525;
        mAutoStartLoadingThread = false;
        mDebugKeysEnabled = false;
        // The port's own identity, not the upstream C++ project's: this is the game a user runs, and
        // the product line `-version` prints and the startup log carries.
        mProdName = "cn.enaium.fabricpvz";
        // All three come from the build (build-info.properties): a hard-coded zero build number and an
        // empty commit date are what the log used to show.
        mProductVersion = cn.enaium.fabricpvz.portable.BuildInfo.version();
        mBuildNum = cn.enaium.fabricpvz.portable.BuildInfo.buildNumber();
        mCommitDate = cn.enaium.fabricpvz.portable.BuildInfo.commitDate();
        // The window title carries the retail edition the assets come from - "GOTY" - in the slot a
        // version number would occupy: the port's own version stays in the startup log, the product
        // line and the package names.
        mTitle = "PvZ Portable GOTY";
        mCustomCursorsEnabled = false;
        mPlayerInfo = null;
        mLastLevelStats = new LevelStats();
        mFirstTimeGameSelector = true;
        mGameMode = GameMode.GAMEMODE_ADVENTURE;
        mEasyPlantingCheat = false;
        mAutoCollectCheat = false;
        // The original game always paused on focus loss; the preference starts in that state. The
        // framework's mute-on-lost-focus rides along: with the auto pause on, leaving the window
        // also silences every music track and sound (including where the pause dialog itself does
        // not apply - the main menu has no board to pause).
        mAutoPause = true;
        mMuteOnLostFocus = true;
        mLoadingZombiesThreadCompleted = true;
        mGamesPlayed = 0;
        mMaxExecutions = 0;
        mMaxPlays = 0;
        mMaxTime = 0;
        mProfileMgr = new ProfileMgr();
        mRegisterResourcesLoaded = false;
        mCheatKeys = false;
        mCrazyDaveReanimID = ReanimationID.REANIMATIONID_NULL;
        mCrazyDaveState = CrazyDaveState.CRAZY_DAVE_OFF;
        mCrazyDaveBlinkCounter = 0;
        mCrazyDaveBlinkReanimID = ReanimationID.REANIMATIONID_NULL;
        mCrazyDaveMessageIndex = -1;
    }

    /**
     * Ported from {@code LawnApp::~LawnApp} (LawnApp.cpp:174). Java has no destructors; this is
     * the explicit teardown the driver calls once the main loop has ended (the framework's
     * {@link cn.enaium.fabricpvz.portable.sexy.SexyAppBase#dispose()} runs {@link #shutdown()} first).
     */
    public void dispose() {
        while (!mDialogMap.isEmpty()) {
            killDialog(mDialogMap.keySet().iterator().next());
        }

        if (mBoard != null) {
            mBoardResult = BoardResult.BOARDRESULT_QUIT_APP;
            writeCurrentUserConfig();
            killBoard();
        }

        processSafeDeleteList();

        if (mTitleScreen != null) {
            mWidgetManager.removeWidget(mTitleScreen);
        }
        if (mGameSelector != null) {
            mWidgetManager.removeWidget(mGameSelector);
        }
        if (mChallengeScreen != null) {
            mWidgetManager.removeWidget(mChallengeScreen);
        }
        if (mSeedChooserScreen != null) {
            mWidgetManager.removeWidget(mSeedChooserScreen);
        }
        if (mAwardScreen != null) {
            mWidgetManager.removeWidget(mAwardScreen);
        }
        if (mCreditScreen != null) {
            mWidgetManager.removeWidget(mCreditScreen);
        }

        mGameSelector = null;
        mEffectSystem = null;
        mReanimatorCache = null;

        // Must die before the resource manager is torn down: the ZenGarden destructor releases the
        // delay-load groups it tracked (ZenGarden.cpp:113-116) via the resource manager.
        if (mZenGarden != null) {
            mZenGarden.dispose();
            mZenGarden = null;
        }

        FilterEffect.filterEffectDisposeForApp();
        PvzpParticleDefinitions.pvzpParticleFreeDefinitions();
        Reanimator.reanimatorFreeDefinitions();
        TrailDefinitions.trailFreeDefinitions();
        PvzpFoley.pvzpFoleyDispose();
        PvzpCommon.freeGlobalAllocators();
        updateRegisterInfo();

        mResourceManager.deleteResources("");
    }

    // =========================================================================
    // Lifecycle overrides
    // =========================================================================

    // LawnApp::Shutdown (LawnApp.cpp:227)
    @Override
    public void shutdown() {
        if (!mLoadingThreadCompleted.get()) {
            mLoadingFailed.set(true);
            super.shutdown();
            return;
        }

        if (!mShutdown.get()) {
            super.shutdown();
        }
    }

    // LawnApp::ShutdownHook (LawnApp.cpp:242)
    @Override
    public void shutdownHook() {
        // Save mid-level game while the music is still alive, before Shutdown() stops it.
        if (mBoard != null) {
            mBoardResult = BoardResult.BOARDRESULT_QUIT_APP;
            mBoard.tryToSaveGame();
        }
    }

    // LawnApp::Init (LawnApp.cpp:1148)
    @Override
    public void init() {
        // Hosts must call Main.installHooks() before init(), exactly like upstream's main.cpp: without
        // the resource-group extractor, loaded resources are never bound to their ids and the first
        // image draw of the title screen fails on a null image.
        doParseCmdLine();
        if (!mCheatKeys) {
            mOnlyAllowOneCopyToRun = true;
        }

        mSessionID = System.currentTimeMillis() / 1000L;
        mPlayTimeActiveSession = 0;
        mPlayTimeInactiveSession = 0;
        mBoardResult = BoardResult.BOARDRESULT_NONE;
        mSawYeti = false;

        super.init();

        if (mShutdown.get()) { // makeWindow() failed
            return;
        }

        if (mRecordingDemoBuffer || mPlayingDemoBuffer) {
            mAppRandSeed = mRandSeed; // demo sessions derive the app-level seed from the recorded one
        }

        Common.logInfoLn("session id: {}", mSessionID);

        if (!mResourceManager.parseResourcesFile("properties/resources.xml")) {
            showResourceError(true);
            return;
        }

        if (!pvzpResources().pvzpLoadResources("Init")) {
            return;
        }

        mProfileMgr.load();

        String aCurUser = "";
        String[] aCurUserOut = new String[1];
        if (mPlayerInfo == null && registryReadString("CurUser", aCurUserOut)) {
            aCurUser = aCurUserOut[0];
            mPlayerInfo = mProfileMgr.getProfile(aCurUser);
        }
        if (mPlayerInfo == null) {
            mPlayerInfo = mProfileMgr.getAnyProfile();
        }

        mMaxExecutions = getInteger("MaxExecutions", 0);
        mMaxPlays = getInteger("MaxPlays", 0);
        mMaxTime = getInteger("MaxTime", 60);

        mTitleScreen = new TitleScreen(this);
        mTitleScreen.resize(0, 0, mWidth, mHeight);
        mWidgetManager.addWidget(mTitleScreen);
        mWidgetManager.setFocus(mTitleScreen);

        mMusic = new Music();
        mSoundSystem = new PvzpFoley();
        mEffectSystem = new EffectSystem();
        mEffectSystem.effectSystemInitialize();

        initTypingChecks();

        Reanimator.reanimatorLoadDefinitions(Reanimator.gLawnReanimationArray, ReanimationType.NUM_REANIMS);
        Reanimator.reanimatorEnsureDefinitionLoaded(ReanimationType.REANIM_LOADBAR_SPROUT, true);
        Reanimator.reanimatorEnsureDefinitionLoaded(ReanimationType.REANIM_LOADBAR_ZOMBIEHEAD, true);
    }

    /**
     * PORT-NOTE (deviation): the typed-code matchers, factored out of {@link #init()} so tests can
     * build an app that types cheat words without the full init.
     */
    void initTypingChecks() {
        mKonamiCheck = new TypingCheck();
        mKonamiCheck.addKeyCode(KeyCode.KEYCODE_UP);
        mKonamiCheck.addKeyCode(KeyCode.KEYCODE_UP);
        mKonamiCheck.addKeyCode(KeyCode.KEYCODE_DOWN);
        mKonamiCheck.addKeyCode(KeyCode.KEYCODE_DOWN);
        mKonamiCheck.addKeyCode(KeyCode.KEYCODE_LEFT);
        mKonamiCheck.addKeyCode(KeyCode.KEYCODE_RIGHT);
        mKonamiCheck.addKeyCode(KeyCode.KEYCODE_LEFT);
        mKonamiCheck.addKeyCode(KeyCode.KEYCODE_RIGHT);
        mKonamiCheck.addChar('b');
        mKonamiCheck.addChar('a');
        mMustacheCheck = new TypingCheck("mustache");
        mMoustacheCheck = new TypingCheck("moustache");
        mSuperMowerCheck = new TypingCheck("trickedout");
        mSuperMowerCheck2 = new TypingCheck("tricked out");
        mFutureCheck = new TypingCheck("future");
        mPinataCheck = new TypingCheck("pinata");
        mDanceCheck = new TypingCheck("dance");
        mDaisyCheck = new TypingCheck("daisies");
        mSukhbirCheck = new TypingCheck("sukhbir");
        mCheatPlantCheck = new TypingCheck("cheatplant");
        mLazyClickCheck = new TypingCheck("lazyclick");
        mWinGameCheck = new TypingCheck("wingame");
        mKillZomCheck = new TypingCheck("killzom");
        mKillPlantCheck = new TypingCheck("killplant");
        mKillAllCheck = new TypingCheck("killall");
        mCherryBoomCheck = new TypingCheck("cherryboom");
        mFastCheck = new TypingCheck("fast");
        mLoveCheck = new TypingCheck("love");
        mFrozeCheck = new TypingCheck("froze");
        mReTrackCheck = new TypingCheck("retrack");
        mDoTrackCheck = new TypingCheck("dotrack");
        mFillHoeCheck = new TypingCheck("fillhoe");
        mFinalCheck = new TypingCheck("final");
        mNextLevelCheck = new TypingCheck("nextlevel");
        mTmpUnlockCheck = new TypingCheck("tmpunlock");
        mUnlockAllCheck = new TypingCheck("unlockall");
    }

    /**
     * PORT-NOTE (deviation): the "unlockall" hidden cheat word, typed on the title screen — pushes
     * the current save to the fully-unlocked state: adventure finished, every page unlocked, every
     * non-endless challenge won (the gold sunflower falls out of the 48 trophies this earns), every
     * store item purchased, 999990 coins, the tree of wisdom at 500 (its height lives in its own
     * challenge-record slot), fertilizer / bug spray / chocolate at 100 and every achievement earned.
     * Endless modes stay unwon: there is no beating them.
     */
    public void unlockAllCheat() {
        unlockAllCheat(mPlayerInfo);
    }

    /**
     * PORT-NOTE (deviation): the core of the unlockall hidden cheat, shared with the -demo mode's
     * "Full Unlock" preview profile — one unified unlock state for both entry points.
     */
    public void unlockAllCheat(PlayerInfo thePlayer) {
        PlayerInfo aPlayer = thePlayer;
        if (aPlayer == null) {
            return;
        }

        aPlayer.mLevel = 1;
        aPlayer.mFinishedAdventure = 1;
        aPlayer.mHasUnlockedMinigames = 1;
        aPlayer.mHasUnlockedPuzzleMode = 1;
        aPlayer.mHasUnlockedSurvivalMode = 1;
        aPlayer.mCoins = 999990;

        for (int aMode = GameMode.GAMEMODE_SURVIVAL_NORMAL_STAGE_1; aMode < GameMode.NUM_GAME_MODES; aMode++) {
            int aIndex = aMode - GameMode.GAMEMODE_SURVIVAL_NORMAL_STAGE_1;
            if (isSurvivalEndless(aMode) || isEndlessScaryPotter(aMode) || isEndlessIZombie(aMode)) {
                continue;
            }
            if (aMode == GameMode.GAMEMODE_TREE_OF_WISDOM) {
                aPlayer.mChallengeRecords[aIndex] = 500;
                continue;
            }
            if (isSurvivalNormal(aMode)) {
                aPlayer.mChallengeRecords[aIndex] = GameConstants.SURVIVAL_NORMAL_FLAGS;
            } else if (isSurvivalHard(aMode)) {
                aPlayer.mChallengeRecords[aIndex] = GameConstants.SURVIVAL_HARD_FLAGS;
            } else {
                aPlayer.mChallengeRecords[aIndex] = 1;
            }
        }

        for (int aItem = 0; aItem <= StoreItem.STORE_ITEM_FIRSTAID; aItem++) {
            switch (aItem) {
                case StoreItem.STORE_ITEM_FERTILIZER:
                case StoreItem.STORE_ITEM_BUG_SPRAY:
                case StoreItem.STORE_ITEM_CHOCOLATE:
                    aPlayer.mPurchases[aItem] = PlayerInfo.PURCHASE_COUNT_OFFSET + 100;
                    break;
                case StoreItem.STORE_ITEM_TREE_FOOD:
                    aPlayer.mPurchases[aItem] = PlayerInfo.PURCHASE_COUNT_OFFSET + 100;
                    break;
                case StoreItem.STORE_ITEM_PACKET_UPGRADE:
                    // the seed bank holds purchases + 6 slots (Board.cpp:~2356) — all four
                    // upgrades make the full 10-slot tray
                    aPlayer.mPurchases[aItem] = 4;
                    break;
                default:
                    aPlayer.mPurchases[aItem] = 1;
                    break;
            }
        }

        java.util.Arrays.fill(aPlayer.mEarnedAchievements, true);
        java.util.Arrays.fill(aPlayer.mShownAchievements, true);
        mProfileMgr.save();
    }

    // LawnApp::ChangeDirHook (LawnApp.cpp:1261)
    @Override
    public boolean changeDirHook(String theIntendedPath) {
        return false;
    }

    // LawnApp::Start (LawnApp.cpp:1266)
    @Override
    public void start() {
        if (mLoadingFailed.get()) {
            return;
        }

        super.start();
    }

    // LawnApp::InitHook (LawnApp.cpp:3117)
    @Override
    public void initHook() {
        // PORT-NOTE (deviation): upstream reset the trial type here; this port keeps the -trial
        // launch parameter's stage lock unless the trial save carries the unlock flag (read from
        // the isolated trial registry during init).
        mTrialType = mTrialLaunch && !mTrialUnlocked
                ? TrialType.TRIALTYPE_STAGELOCKED : TrialType.TRIALTYPE_NONE;
    }

    // LawnApp::DebugKeyDown (LawnApp.cpp:1274)
    @Override
    public boolean debugKeyDown(int theKey) {
        return super.debugKeyDown(theKey);
    }

    // LawnApp::HandleCmdLineParam (LawnApp.cpp:1279)
    @Override
    public void handleCmdLineParam(String theParamName, String theParamValue) {
        if ("-cheat".equals(theParamName)) {
            mCheatKeys = true;
            mDebugKeysEnabled = true;
        } else if ("-demo".equals(theParamName)) {
            // the -demo launch parameter includes the -cheat debug keys
            mCheatKeys = true;
            mDebugKeysEnabled = true;
            super.handleCmdLineParam(theParamName, theParamValue);
        } else if ("-trial".equals(theParamName)) {
            // the -trial branch: the stage-locked trial state plus the isolated save folder
            // (mTrialLaunch, handled by super for the savedata resolution)
            mTrialType = TrialType.TRIALTYPE_STAGELOCKED;
            super.handleCmdLineParam(theParamName, theParamValue);
        } else {
            super.handleCmdLineParam(theParamName, theParamValue);
        }
    }

    /**
     * The auto-pause leaves resume control to the player: the pause is the same modal pause dialog
     * the Space key opens (newspaper zombie, "Resume Game"), so it ends the way that dialog ends -
     * by the player clicking its button. Nothing to do on focus regain; upstream's unconditional
     * resume (LawnApp.cpp:299) has no equivalent here.
     */
    @Override
    public void gotFocus() {
    }

    /**
     * The auto-pause preference: leaving the window opens the very same pause dialog the Space key
     * opens ({@link #doPauseDialog} - the newspaper-zombie pause with the save-on-pause), instead of
     * upstream's silent board pause (LawnApp.cpp:303). The gates are {@link #canPauseNow}, the same
     * ones the Space path respects: no pause dialog over the seed chooser, a Crazy Dave speech, zen
     * garden / tree of wisdom, or another open dialog (whose own modal pause already holds). No
     * pause sound plays - the player is leaving, not asking for feedback.
     */
    @Override
    public void lostFocus() {
        if (mAutoPause && canPauseNow()) {
            doPauseDialog();
        }
    }

    // =========================================================================
    // Boot splash (a port addition): the "Made with Fabric" image ships as a jar
    // resource and covers the screen while the retail pak is still loading -
    // upstream (and this port, until now) showed a black window for that whole
    // wait. TitleScreen draws it in its pre-pak states.
    // =========================================================================

    /** The decoded, canvas-sized splash (lazy; null until first drawn, then cached). */
    public MemoryImage mBootSplashImage;

    /**
     * Draws the boot splash over the whole screen; falls back to the upstream black fill when the
     * resource is missing or undecodable. The pak cannot be consulted here - the splash exists
     * exactly because nothing from it is loaded yet - so the image rides in the jar.
     */
    public void drawBootSplash(Graphics g) {
        if (mBootSplashImage == null) {
            mBootSplashImage = loadBootSplash();
        }
        if (mBootSplashImage != null) {
            g.drawImage(mBootSplashImage, 0, 0);
        } else {
            g.setColor(Color.Black);
            g.fillRect(0, 0, mWidth, mHeight);
        }
    }

    /** Paints the splash onto the fresh window canvas and presents it once ({@code makeWindow});
     * {@link #drawBootSplash} keeps covering the screen from TitleScreen while loading continues.
     * The canvas must be a real {@link MemoryImage} (the framework screen image is; test doubles
     * are not), and a renderer must exist to present through. */
    @Override
    protected void drawBootSplashOnce() {
        if (mRendererBackend == null
                || !(mWidgetManager != null && mWidgetManager.mImage instanceof MemoryImage aCanvas)) {
            return;
        }

        drawBootSplash(new Graphics(aCanvas));
        redraw(null);
    }

    /** Decodes {@code /pvzp/loading.jpg} and cover-scales it into a canvas-sized image. */
    private MemoryImage loadBootSplash() {
        try (java.io.InputStream in = LawnApp.class.getResourceAsStream("/pvzp/loading.jpg")) {
            if (in == null) {
                return null;
            }

            java.awt.image.BufferedImage aLoaded = javax.imageio.ImageIO.read(in);
            if (aLoaded == null) {
                return null;
            }

            // Cover-scale: fill the canvas completely, cropping the overflow centred - the same
            // treatment Minecraft's splash applies.
            int aWidth = mWidth;
            int aHeight = mHeight;
            java.awt.image.BufferedImage aScaled = new java.awt.image.BufferedImage(
                    aWidth, aHeight, java.awt.image.BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D aGraphics = aScaled.createGraphics();
            double aScale = Math.max((double) aWidth / aLoaded.getWidth(),
                    (double) aHeight / aLoaded.getHeight());
            int aDrawWidth = (int) Math.round(aLoaded.getWidth() * aScale);
            int aDrawHeight = (int) Math.round(aLoaded.getHeight() * aScale);
            aGraphics.drawImage(aLoaded, (aWidth - aDrawWidth) / 2, (aHeight - aDrawHeight) / 2,
                    aDrawWidth, aDrawHeight, null);
            aGraphics.dispose();

            MemoryImage anImage = new MemoryImage(null);
            anImage.setBits(aScaled.getRGB(0, 0, aWidth, aHeight, null, 0, aWidth),
                    aWidth, aHeight, false);
            return anImage;
        } catch (java.io.IOException anException) {
            return null;
        }
    }

    // =========================================================================
    // F3 debug overlay (a port addition, after Minecraft's F3): fills the
    // framework's FPSDrawCoords stub (SexyAppBase.drawDebugOverlay) with the
    // game's own data. Two blocks like the reference: performance and memory in
    // the top-left corner, identity and environment in the top-right. The text
    // is English in code, like every other in-code default string.
    // =========================================================================

    /** The allocation-rate sampler's last sample (bytes, nanotime); 0 time = unset. */
    private long mLastAllocatedBytes;
    private long mLastAllocatedTimeNs;
    /** The last sampled allocation rate in MiB/s; negative when the JVM does not expose it. */
    private double mAllocatedRateMiBPerSec = -1.0;

    /** {@link #drawDebugOverlay}: the performance and memory block, top-left, left-aligned. */
    private String[] debugLeftLines() {
        java.util.List<String> aLines = new java.util.ArrayList<>();

        aLines.add(debugFpsLine(mDebugFps, mDebugUpdateMs, mDebugDrawMs));
        aLines.add("3D acceleration: " + (is3DAccelerated() ? "on" : "off")
                + ", vsync: " + (mWaitForVSync ? "on" : "off"));

        Runtime aRuntime = Runtime.getRuntime();
        long aUsedBytes = aRuntime.totalMemory() - aRuntime.freeMemory();
        aLines.add(debugMemoryLine(aUsedBytes, aRuntime.totalMemory(), aRuntime.maxMemory()));

        double anAllocRate = sampleAllocatedRateMiBPerSec();
        if (anAllocRate >= 0) {
            aLines.add(debugAllocRateLine(anAllocRate));
        }
        return aLines.toArray(new String[0]);
    }

    /** {@link #drawDebugOverlay}: the identity and environment block, top-right, right-aligned. */
    private String[] debugRightLines() {
        java.util.List<String> aLines = new java.util.ArrayList<>();

        aLines.add("PvZ Portable GOTY " + mProductVersion);

        if (mRendererBackend != null) {
            String aName = mRendererBackend.rendererName();
            if (aName == null || aName.isEmpty()) {
                aName = "unknown";
            }
            aLines.add("Renderer: " + aName
                    + " (" + mRendererBackend.logicalWidth() + "x" + mRendererBackend.logicalHeight()
                    + " -> " + mRendererBackend.renderOutputWidth() + "x"
                    + mRendererBackend.renderOutputHeight() + ")");
        }

        if (mPlatformBackend != null) {
            int aWindowWidth = mPlatformBackend.windowWidth();
            double aDensity = aWindowWidth > 0
                    ? (double) mPlatformBackend.pixelWidth() / aWindowWidth : 1.0;
            aLines.add(String.format(java.util.Locale.ROOT, "Display: %dx%d (%.2fx pixel density)",
                    aWindowWidth, mPlatformBackend.windowHeight(), aDensity));
        }

        aLines.add("Java: " + System.getProperty("java.version", "?"));
        aLines.add("OS: " + System.getProperty("os.name", "?") + " "
                + System.getProperty("os.version", "?") + " (" + System.getProperty("os.arch", "?") + ")");

        // The CPU model comes from each platform in its own way (Windows registry, Linux /proc,
        // macOS sysctl); without one the architecture is what shows.
        String aCpuModel = RuntimeEnvironment.cpuModel();
        aLines.add("CPU: " + Runtime.getRuntime().availableProcessors() + "x"
                + (aCpuModel.isEmpty() ? " " + System.getProperty("os.arch", "?") : " " + aCpuModel));

        String aLoaderVersion = RuntimeEnvironment.loaderVersion();
        aLines.add("Fabric Loader: " + (aLoaderVersion.isEmpty() ? "?" : aLoaderVersion));
        aLines.add("Mods: " + RuntimeEnvironment.totalMods());

        return aLines.toArray(new String[0]);
    }

    @Override
    protected void drawDebugOverlay(Graphics g) {
        Font aFont = Resources.getFontById(Resources.FONT_PICO129_ID);
        if (aFont == null || aFont.mHeight <= 0) {
            return;
        }

        int aLineHeight = aFont.getLineSpacing();
        int aY = 4 + aFont.getAscent();

        for (String aLine : debugLeftLines()) {
            g.setFont(aFont);
            g.setColor(Color.White);
            g.drawStringShaded(aLine, 4, aY, Color.Black);
            aY += aLineHeight;
        }

        aY = 4 + aFont.getAscent();
        for (String aLine : debugRightLines()) {
            g.setFont(aFont);
            g.setColor(Color.White);
            g.drawStringShaded(aLine, mWidth - 4 - aFont.stringWidth(aLine), aY, Color.Black);
            aY += aLineHeight;
        }
    }

    /** The JVM heap's allocation rate on the game thread, in MiB/s; negative when unavailable. */
    private double sampleAllocatedRateMiBPerSec() {
        if (!(java.lang.management.ManagementFactory.getThreadMXBean()
                instanceof com.sun.management.ThreadMXBean aBean)) {
            return -1.0;
        }
        if (!aBean.isThreadAllocatedMemoryEnabled()) {
            try {
                aBean.setThreadAllocatedMemoryEnabled(true);
            } catch (SecurityException anException) {
                return -1.0;
            }
        }

        long aBytes = aBean.getThreadAllocatedBytes(Thread.currentThread().threadId());
        if (aBytes < 0) {
            return -1.0;
        }

        long aNowNs = System.nanoTime();
        if (mLastAllocatedTimeNs != 0 && aNowNs > mLastAllocatedTimeNs) {
            double aSeconds = (aNowNs - mLastAllocatedTimeNs) / 1e9;
            mAllocatedRateMiBPerSec = Math.max(0.0, aBytes - mLastAllocatedBytes) / 1048576.0 / aSeconds;
        }
        mLastAllocatedBytes = aBytes;
        mLastAllocatedTimeNs = aNowNs;
        return mAllocatedRateMiBPerSec;
    }

    /** The overlay's first left line: frame rate and the per-frame update/draw wall times. */
    static String debugFpsLine(int theFps, double theUpdateMs, double theDrawMs) {
        return String.format(java.util.Locale.ROOT, "%d fps (update %.1f ms / draw %.1f ms)",
                theFps, theUpdateMs, theDrawMs);
    }

    /** The overlay's heap line, MC-style: the used share of the <em>allocated</em> heap, then
     * used/allocated and the maximum, in MiB. */
    static String debugMemoryLine(long theUsedBytes, long theAllocatedBytes, long theMaxBytes) {
        int aUsedPercent = theAllocatedBytes > 0
                ? (int) Math.round(theUsedBytes * 100.0 / theAllocatedBytes) : 0;
        return String.format(java.util.Locale.ROOT, "Mem: %d%% %d/%d MiB (max %d MiB)",
                aUsedPercent, theUsedBytes / 1048576, theAllocatedBytes / 1048576, theMaxBytes / 1048576);
    }

    /** The overlay's allocation-rate line, in MiB/s. */
    static String debugAllocRateLine(double theMiBPerSec) {
        return String.format(java.util.Locale.ROOT, "Alloc rate: %.0f MiB/s", theMiBPerSec);
    }

    // LawnApp::WriteToRegistry (LawnApp.cpp:313)
    @Override
    public void writeToRegistry() {
        if (mPlayerInfo != null) {
            registryWriteString("CurUser", mPlayerInfo.mName);
            mPlayerInfo.saveDetails();
        }

        // The auto-pause preference, next to the framework's own ScreenMode/WaitForVSync switches.
        registryWriteBoolean("AutoPause", mAutoPause);
        // The F3 debug overlay's persisted default (the key toggles it at runtime either way).
        registryWriteBoolean("DebugInfo", mShowFPS);
        registryWriteBoolean("TrialUnlocked", mTrialUnlocked);

        super.writeToRegistry();
    }

    // LawnApp::ReadFromRegistry (LawnApp.cpp:324)
    @Override
    public void readFromRegistry() {
        super.readFromRegistry();

        boolean[] aBool = new boolean[1];
        if (registryReadBoolean("AutoPause", aBool)) {
            mAutoPause = aBool[0];
        }
        // The two preferences travel together: no auto pause also means no focus mute.
        mMuteOnLostFocus = mAutoPause;

        if (registryReadBoolean("DebugInfo", aBool)) {
            mShowFPS = aBool[0];
        }
        if (registryReadBoolean("TrialUnlocked", aBool)) {
            mTrialUnlocked = aBool[0];
        }
    }

    // LawnApp::WriteCurrentUserConfig (LawnApp.cpp:329)
    public boolean writeCurrentUserConfig() {
        if (mPlayerInfo != null) {
            mPlayerInfo.saveDetails();
        }

        return true;
    }

    // =========================================================================
    // Board creation / level entry
    // =========================================================================

    // LawnApp::KillBoard (LawnApp.cpp:252)
    public void killBoard() {
        finishModelessDialogs();
        killSeedChooserScreen();
        if (mBoard != null) {
            if (mPlayerInfo != null
                    && (mBoardResult == BoardResult.BOARDRESULT_WON
                    || mBoardResult == BoardResult.BOARDRESULT_LOST
                    || mBoardResult == BoardResult.BOARDRESULT_RESTART
                    || mBoardResult == BoardResult.BOARDRESULT_CHEAT)) {
                String aFileName = LawnCommon.getSavedGameName(mGameMode, mPlayerInfo.mId);
                eraseFile(aFileName);
                String aLegacyFileName = LawnCommon.getLegacySavedGameName(mGameMode, mPlayerInfo.mId);
                eraseFile(aLegacyFileName);
            }

            mBoard.disposeBoard();
            // PORT-NOTE (mapping of a C++ destructor): upstream's `SafeDeleteWidget(mBoard)` runs
            // `delete mBoard` here (LawnApp.cpp:269), i.e. `~Board()` (Board.cpp:238), which destroys
            // the board's owned CutScene; `~CutScene` (CutScene.cpp:121-128) releases the cutscene's
            // tracked delay-load groups AND clears `mApp->mMuteSoundsForCutscene`. Java has no
            // destructor, so that teardown is explicit. Without it the flag set by the intro cinematics
            // (`CutScene::LoadIntroBoard`, CutScene.cpp:1793, reached from
            // `GameSelector::Update` -> `PreNewGame(GAMEMODE_INTRO)`) is never cleared: the intro board
            // is deleted at `TimeIntro_End` (CutScene.cpp:2261 -> LawnApp.cpp:337 -> KillBoard) and
            // every later PlaySample/PlayFoley of the whole session is dropped by the
            // `mMuteSoundsForCutscene` guards (LawnApp.cpp:1957, :1965, :3208).
            mBoard.mCutScene.dispose();
            mWidgetManager.removeWidget(mBoard);
            safeDeleteWidget(mBoard);
            mBoard = null;
        }

        setCursor(CURSOR_POINTER);
    }

    // LawnApp::CanPauseNow (LawnApp.cpp:279)
    public boolean canPauseNow() {
        if (mBoard == null) {
            return false;
        }
        if (mSeedChooserScreen != null && mSeedChooserScreen.mMouseVisible) {
            return false;
        }
        if (mBoard.mBoardFadeOutCounter >= 0) {
            return false;
        }
        if (mCrazyDaveState != CrazyDaveState.CRAZY_DAVE_OFF) {
            return false;
        }
        if (mGameMode == GameMode.GAMEMODE_CHALLENGE_ZEN_GARDEN || mGameMode == GameMode.GAMEMODE_TREE_OF_WISDOM) {
            return false;
        }

        return getDialogCount() <= 0;
    }

    // LawnApp::PreNewGame (LawnApp.cpp:337)
    public void preNewGame(int theGameMode, boolean theLookForSavedGame) {
        mGameMode = theGameMode;
        if (theLookForSavedGame && tryLoadGame()) {
            return;
        }

        String aFileName = LawnCommon.getSavedGameName(mGameMode, mPlayerInfo.mId);
        eraseFile(aFileName);
        // PORT-NOTE (deviation from LawnApp.cpp:350-352): upstream also erases the legacy
        // game<id>_<mode>.dat here. This port cannot decode the legacy raw-C++-memory-image format
        // (SaveGame.LegacySaveFormatException; ../verification/VERIFICATION.md §7), so tryLoadGame always fails
        // for such a file and erasing it would destroy the user's only copy with nothing migrated.
        // The legacy file is therefore erased only after a *successful* migration inside tryLoadGame
        // (LawnApp.cpp:396-410), matching upstream's post-migration cleanup.
        newGame();
    }

    // LawnApp::MakeNewBoard (LawnApp.cpp:356)
    public void makeNewBoard() {
        killBoard();
        mBoard = new Board(this);
        mBoard.resize(0, 0, mWidth, mHeight);
        mWidgetManager.addWidget(mBoard);
        mWidgetManager.bringToBack(mBoard);
        mWidgetManager.setFocus(mBoard);
    }

    // LawnApp::StartPlaying (LawnApp.cpp:366)
    public void startPlaying() {
        killSeedChooserScreen();
        mBoard.startLevel();
        mGameScene = GameScenes.SCENE_PLAYING;
    }

    // LawnApp::SaveFileExists (LawnApp.cpp:373)
    public boolean saveFileExists() {
        String aFileName = LawnCommon.getSavedGameName(GameMode.GAMEMODE_ADVENTURE, mPlayerInfo.mId);
        if (fileExists(aFileName)) {
            return true;
        }
        String aLegacyFileName = LawnCommon.getLegacySavedGameName(GameMode.GAMEMODE_ADVENTURE, mPlayerInfo.mId);
        return fileExists(aLegacyFileName);
    }

    // LawnApp::TryLoadGame (LawnApp.cpp:382)
    public boolean tryLoadGame() {
        String aSaveName = LawnCommon.getSavedGameName(mGameMode, mPlayerInfo.mId);
        String aLegacySaveName = LawnCommon.getLegacySavedGameName(mGameMode, mPlayerInfo.mId);
        mMusic.stopAllMusic();

        if (fileExists(aSaveName)) {
            makeNewBoard();
            if (mBoard.loadGame(aSaveName)) {
                mFirstTimeGameSelector = false;
                if (mBoard.mLevelAwardSpawned) { // ensure save cleanup after award collection
                    mBoardResult = BoardResult.BOARDRESULT_WON;
                }
                doContinueDialog();
                return true;
            }

            killBoard();
        }
        if (fileExists(aLegacySaveName)) {
            makeNewBoard();
            if (mBoard.loadGame(aLegacySaveName)) {
                if (SaveGame.lawnSaveGame(mBoard, aSaveName)) {
                    eraseFile(aLegacySaveName);
                }
                mFirstTimeGameSelector = false;
                if (mBoard.mLevelAwardSpawned) { // ensure save cleanup after award collection
                    mBoardResult = BoardResult.BOARDRESULT_WON;
                }
                doContinueDialog();
                return true;
            }

            killBoard();
        }

        return false;
    }

    // LawnApp::NewGame (LawnApp.cpp:424)
    public void newGame() {
        mFirstTimeGameSelector = false;

        makeNewBoard();
        mBoard.initLevel();
        mBoardResult = BoardResult.BOARDRESULT_NONE;
        mGameScene = GameScenes.SCENE_LEVEL_INTRO;

        showSeedChooserScreen();
        mBoard.mCutScene.startLevelIntro();
    }

    // LawnApp::EndLevel (LawnApp.cpp:565)
    public void endLevel() {
        killBoard();
        if (isAdventureMode()) {
            newGame();
        }

        mFirstTimeGameSelector = true;

        makeNewBoard();
        mBoard.initLevel();
        mBoardResult = BoardResult.BOARDRESULT_NONE;
        mGameScene = GameScenes.SCENE_LEVEL_INTRO;
        showSeedChooserScreen();
        mBoard.mCutScene.startLevelIntro();
    }

    // =========================================================================
    // Screen management
    // =========================================================================

    // LawnApp::ShowGameSelector (LawnApp.cpp:437)
    public void showGameSelector() {
        killBoard();
        if (mGameSelector != null) {
            mWidgetManager.removeWidget(mGameSelector);
            safeDeleteWidget(mGameSelector);
            mGameSelector = null;
        }

        mGameScene = GameScenes.SCENE_MENU;
        mGameSelector = new GameSelector(this);
        mGameSelector.resize(0, 0, mWidth, mHeight);
        mWidgetManager.addWidget(mGameSelector);
        mWidgetManager.bringToBack(mGameSelector);
        mWidgetManager.setFocus(mGameSelector);
    }

    // LawnApp::KillGameSelector (LawnApp.cpp:460)
    public void killGameSelector() {
        if (mGameSelector != null) {
            mWidgetManager.removeWidget(mGameSelector);
            safeDeleteWidget(mGameSelector);
            mGameSelector = null;
        }
    }

    // LawnApp::ShowAwardScreen (LawnApp.cpp:469)
    public void showAwardScreen(int theAwardType, boolean theShowAchievements) {
        mGameScene = GameScenes.SCENE_AWARD;
        mAwardScreen = new AwardScreen(this, theAwardType, theShowAchievements);
        mAwardScreen.resize(0, 0, mWidth, mHeight);
        mWidgetManager.addWidget(mAwardScreen);
        mWidgetManager.bringToBack(mAwardScreen);
        mWidgetManager.setFocus(mAwardScreen);
    }

    // LawnApp::KillAwardScreen (LawnApp.cpp:479)
    public void killAwardScreen() {
        if (mAwardScreen != null) {
            mWidgetManager.removeWidget(mAwardScreen);
            safeDeleteWidget(mAwardScreen);
            mAwardScreen = null;
        }
    }

    // LawnApp::ShowCreditScreen (LawnApp.cpp:488)
    public void showCreditScreen() {
        mCreditScreen = new CreditScreen(this);
        mCreditScreen.resize(0, 0, mWidth, mHeight);
        mWidgetManager.addWidget(mCreditScreen);
        mWidgetManager.bringToBack(mCreditScreen);
        mWidgetManager.setFocus(mCreditScreen);
    }

    // LawnApp::KillCreditScreen (LawnApp.cpp:497)
    public void killCreditScreen() {
        if (mCreditScreen != null) {
            mWidgetManager.removeWidget(mCreditScreen);
            safeDeleteWidget(mCreditScreen);
            mCreditScreen = null;
        }
    }

    // LawnApp::ShowChallengeScreen (LawnApp.cpp:506)
    public void showChallengeScreen(int thePage) {
        mGameScene = GameScenes.SCENE_CHALLENGE;
        mChallengeScreen = new ChallengeScreen(this, thePage);
        mChallengeScreen.resize(0, 0, mWidth, mHeight);
        mWidgetManager.addWidget(mChallengeScreen);
        mWidgetManager.bringToBack(mChallengeScreen);
        mWidgetManager.setFocus(mChallengeScreen);
    }

    // LawnApp::KillChallengeScreen (LawnApp.cpp:516)
    public void killChallengeScreen() {
        if (mChallengeScreen != null) {
            mWidgetManager.removeWidget(mChallengeScreen);
            safeDeleteWidget(mChallengeScreen);
            mChallengeScreen = null;
        }
    }

    // LawnApp::ShowStoreScreen (LawnApp.cpp:525)
    public StoreScreen showStoreScreen() {
        StoreScreen aStoreScreen = new StoreScreen(this);
        addDialog(aStoreScreen);
        mWidgetManager.setFocus(aStoreScreen);

        return aStoreScreen;
    }

    // LawnApp::KillStoreScreen (LawnApp.cpp:537)
    public void killStoreScreen() {
        StoreScreen aStore = (StoreScreen) getDialog(Dialogs.DIALOG_STORE);
        if (aStore != null) {
            killDialog(Dialogs.DIALOG_STORE);
            clearUpdateBacklog(false);
            // PORT-NOTE: this is the point upstream's callers regained control — every store caller
            // sits after `StoreScreen::WaitForResult(true)`, which returns only once autoKill removed
            // the screen (Dialog.cpp:410-441), and this port's wait does not pump
            // (sexy/Dialog.java:506). A caller that has a transition to run on the way out registered
            // it on the screen (StoreScreen.onStoreClosed) and it runs now that the store is gone.
            aStore.runStoreClosedListener();
        }
    }

    // LawnApp::ShowSeedChooserScreen (LawnApp.cpp:546)
    public void showSeedChooserScreen() {
        mSeedChooserScreen = new SeedChooserScreen();
        mSeedChooserScreen.resize(0, 0, mWidth, mHeight);
        mWidgetManager.addWidget(mSeedChooserScreen);
        mWidgetManager.bringToBack(mSeedChooserScreen);
    }

    // LawnApp::KillSeedChooserScreen (LawnApp.cpp:556)
    public void killSeedChooserScreen() {
        if (mSeedChooserScreen != null) {
            mWidgetManager.removeWidget(mSeedChooserScreen);
            safeDeleteWidget(mSeedChooserScreen);
            mSeedChooserScreen = null;
        }
    }

    // =========================================================================
    // Dialog management
    // =========================================================================

    // LawnApp::DoBackToMain (LawnApp.cpp:583)
    public void doBackToMain() {
        mMusic.stopAllMusic();
        mSoundSystem.cancelPausedFoley();
        writeCurrentUserConfig();
        killNewOptionsDialog();
        killBoard();
        showGameSelector();
    }

    // LawnApp::DoConfirmBackToMain (LawnApp.cpp:593)
    public void doConfirmBackToMain() {
        LawnDialog aDialog = (LawnDialog) doDialog(
                Dialogs.DIALOG_CONFIRM_BACK_TO_MAIN,
                true,
                getString("LEAVE_GAME_HEADER", "Leave Game?"),
                getString("LEAVE_GAME",
                        "Do you want to return\nto the main menu?\n\nYour game will be saved."),
                "",
                Dialog.BUTTONS_YES_NO);

        aDialog.mLawnYesButton.mLabel = PvzpStringFile.translate("[LEAVE_BUTTON]");
        aDialog.mLawnNoButton.mLabel = PvzpStringFile.translate("[DIALOG_BUTTON_CANCEL]");
    }

    // LawnApp::DoNewOptions (LawnApp.cpp:610)
    public void doNewOptions(boolean theFromGameSelector) {
        NewOptionsDialog aDialog = new NewOptionsDialog(this, theFromGameSelector);
        Image aBack = Resources.getImageById(Resources.IMAGE_OPTIONS_MENUBACK_ID);
        centerDialog(aDialog, aBack.mWidth, aBack.mHeight);
        addDialog(Dialogs.DIALOG_NEWOPTIONS, aDialog);
        mWidgetManager.setFocus(aDialog);
    }

    // LawnApp::ShowZombatarTOS (LawnApp.cpp:620)
    public void showZombatarTOS() {
        ZombatarTOS aDialog = new ZombatarTOS(this);
        centerDialog(aDialog, aDialog.mWidth, aDialog.mHeight);
        addDialog(Dialogs.DIALOG_ZOMBATAR_TOS, aDialog);
        mWidgetManager.setFocus(aDialog);
    }

    // LawnApp::DoAlmanacDialog (LawnApp.cpp:628)
    public AlmanacDialog doAlmanacDialog(int theSeedType, int theZombieType) {
        AlmanacDialog aDialog = new AlmanacDialog(this);
        addDialog(Dialogs.DIALOG_ALMANAC, aDialog);
        mWidgetManager.setFocus(aDialog);

        if (theSeedType != SeedType.SEED_NONE) {
            aDialog.showPlant(theSeedType);
        } else if (theZombieType != ZombieType.ZOMBIE_INVALID) {
            aDialog.showZombie(theZombieType);
        }

        return aDialog;
    }

    // LawnApp::DoContinueDialog (LawnApp.cpp:654)
    public void doContinueDialog() {
        ContinueDialog aDialog = new ContinueDialog(this);
        centerDialog(aDialog, aDialog.mWidth, aDialog.mHeight);
        addDialog(Dialogs.DIALOG_CONTINUE, aDialog);
        mWidgetManager.setFocus(aDialog);
    }

    // LawnApp::DoPauseDialog (LawnApp.cpp:662)
    public void doPauseDialog() {
        mBoard.pause(true);

        LawnDialog aDialog = (LawnDialog) doDialog(
                Dialogs.DIALOG_PAUSED,
                true,
                getString("GAME_PAUSED", "GAME PAUSED"),
                getString("CLICK_TO_RESUME", "Click to resume game"),
                getString("RESUME_GAME", "Resume Game"),
                Dialog.BUTTONS_FOOTER);

        aDialog.mReanimation.addReanimation(72.0f, 42.0f, ReanimationType.REANIM_ZOMBIE_NEWSPAPER);
        aDialog.mSpaceAfterHeader = 155;
        aDialog.calcSize(0, 10);
        centerDialog(aDialog, aDialog.mWidth, aDialog.mHeight);
    }

    /**
     * How a {@link #lawnMessageBox} answer is delivered. Upstream's message box <em>returns</em> the
     * pressed button because {@code Dialog::WaitForResult} pumps the message loop until a button is
     * depressed (Dialog.cpp:410-441); this port's {@code waitForResult} does not pump
     * (sexy/Dialog.java:506), so a caller that has to act on the answer passes a callback instead and
     * it runs when the box's button is depressed ({@link #finishLawnMessageBox}).
     */
    @FunctionalInterface
    public interface DialogAnswer {
        void answered(boolean theYes);
    }

    /** The pending {@link #lawnMessageBox} answer (null when the caller ignores the answer). */
    private DialogAnswer mMessageBoxAnswer;
    private int mMessageBoxDialogId;
    private cn.enaium.fabricpvz.portable.sexy.Widget mMessageBoxOldFocus;

    // LawnApp::LawnMessageBox (LawnApp.cpp:682)
    public int lawnMessageBox(int theDialogId, String theHeaderName, String theLinesName,
            String theButton1Name, String theButton2Name, int theButtonMode) {
        return lawnMessageBox(theDialogId, theHeaderName, theLinesName, theButton1Name, theButton2Name,
                theButtonMode, null);
    }

    /**
     * LawnApp.cpp:682-702 with the answer deferred: see {@link DialogAnswer}. The returned int is the
     * dialog's result while it is still unanswered and must not be branched on — pass
     * {@code theAnswer} and act in the callback.
     */
    public int lawnMessageBox(int theDialogId, String theHeaderName, String theLinesName,
            String theButton1Name, String theButton2Name, int theButtonMode, DialogAnswer theAnswer) {
        cn.enaium.fabricpvz.portable.sexy.Widget aOldFocus = mWidgetManager.mFocusWidget;

        LawnDialog aDialog = (LawnDialog) doDialog(theDialogId, true, theHeaderName, theLinesName,
                theButton1Name, theButtonMode);
        if (aDialog.mLawnYesButton != null) {
            aDialog.mLawnYesButton.mLabel = PvzpStringFile.translate(theButton1Name);
        }
        if (aDialog.mLawnNoButton != null) {
            aDialog.mLawnNoButton.mLabel = PvzpStringFile.translate(theButton2Name);
        }

        mWidgetManager.setFocus(aDialog);
        // Upstream restores the focus after the wait returns, i.e. when the box is answered.
        mMessageBoxDialogId = theDialogId;
        mMessageBoxAnswer = theAnswer;
        mMessageBoxOldFocus = theAnswer == null ? null : aOldFocus;
        int aResult = aDialog.waitForResult(true);
        if (theAnswer == null) {
            mWidgetManager.setFocus(aOldFocus);
        }

        return aResult;
    }

    /**
     * Delivers a {@link #lawnMessageBox} answer. Upstream's blocking wait returns it to the caller;
     * here it arrives when the box's button is depressed (LawnApp::ButtonDepress' switch dispatches
     * the button ids, LawnApp.cpp:1791).
     */
    public void finishLawnMessageBox(int theDialogId, boolean theYes) {
        if (mMessageBoxAnswer == null || theDialogId != mMessageBoxDialogId) {
            return;
        }

        DialogAnswer anAnswer = mMessageBoxAnswer;
        mMessageBoxAnswer = null;
        if (mMessageBoxOldFocus != null) {
            mWidgetManager.setFocus(mMessageBoxOldFocus);
            mMessageBoxOldFocus = null;
        }

        anAnswer.answered(theYes);
    }

    // LawnApp::DoDialog (LawnApp.cpp:704)
    @Override
    public Dialog doDialog(int theDialogId, boolean isModal, String theDialogHeader,
            String theDialogLines, String theDialogFooter, int theButtonMode) {
        String aHeader = PvzpStringFile.translate(theDialogHeader);
        String aLines = PvzpStringFile.translate(theDialogLines);
        String aFooter = PvzpStringFile.translate(theDialogFooter);

        Dialog aDialog = super.doDialog(theDialogId, isModal, aHeader, aLines, aFooter, theButtonMode);
        if (mWidgetManager.mFocusWidget == null) {
            mWidgetManager.mFocusWidget = aDialog;
        }

        return aDialog;
    }

    // LawnApp::DoDialogDelay (LawnApp.cpp:719)
    public Dialog doDialogDelay(int theDialogId, boolean isModal, String theDialogHeader,
            String theDialogLines, String theDialogFooter, int theButtonMode) {
        LawnDialog aDialog = (LawnDialog) super.doDialog(theDialogId, isModal, theDialogHeader,
                theDialogLines, theDialogFooter, theButtonMode);
        aDialog.setButtonDelay(30);
        return aDialog;
    }

    // LawnApp::NewDialog (LawnApp.cpp:1040)
    @Override
    public Dialog newDialog(int theDialogId, boolean isModal, String theDialogHeader,
            String theDialogLines, String theDialogFooter, int theButtonMode) {
        LawnDialog aDialog = new LawnDialog(this, theDialogId, isModal, theDialogHeader,
                theDialogLines, theDialogFooter, theButtonMode);

        centerDialog(aDialog, aDialog.mWidth, aDialog.mHeight);
        return aDialog;
    }

    // LawnApp::DoUserDialog (LawnApp.cpp:726)
    public void doUserDialog() {
        killDialog(Dialogs.DIALOG_USERDIALOG);

        UserDialog aDialog = new UserDialog(this);
        centerDialog(aDialog, aDialog.mWidth, aDialog.mHeight);
        addDialog(Dialogs.DIALOG_USERDIALOG, aDialog);
        mWidgetManager.setFocus(aDialog);
    }

    // LawnApp::FinishUserDialog (LawnApp.cpp:736)
    public void finishUserDialog(boolean isYes) {
        UserDialog aUserDialog = (UserDialog) getDialog(Dialogs.DIALOG_USERDIALOG);
        if (aUserDialog != null) {
            if (isYes) {
                PlayerInfo aProfile = mProfileMgr.getProfile(aUserDialog.getSelName());
                if (aProfile != null) {
                    mPlayerInfo = aProfile;
                    mWidgetManager.markAllDirty();

                    if (mGameSelector != null) {
                        mGameSelector.syncProfile(true);
                    }
                }
            }

            killDialog(Dialogs.DIALOG_USERDIALOG);
        }
    }

    // LawnApp::DoCreateUserDialog (LawnApp.cpp:760)
    public void doCreateUserDialog() {
        killDialog(Dialogs.DIALOG_CREATEUSER);

        NewUserDialog aDialog = new NewUserDialog(this, false);
        centerDialog(aDialog, aDialog.mWidth, aDialog.mHeight);
        addDialog(Dialogs.DIALOG_CREATEUSER, aDialog);
    }

    // LawnApp::FinishCreateUserDialog (LawnApp.cpp:769)
    public void finishCreateUserDialog(boolean isYes) {
        NewUserDialog aNewUserDialog = (NewUserDialog) getDialog(Dialogs.DIALOG_CREATEUSER);
        if (aNewUserDialog == null) {
            return;
        }

        String aName = aNewUserDialog.getName();

        if (isYes && aName.isEmpty()) {
            doDialog(
                    Dialogs.DIALOG_CREATEUSERERROR,
                    true,
                    getString("ENTER_YOUR_NAME", "Enter Your Name"),
                    getString("USER_ERROR_MESSAGE",
                            "Please enter your name to create a new user profile for storing high score data and game progress."),
                    "[DIALOG_BUTTON_OK]",
                    Dialog.BUTTONS_FOOTER);
        } else if (mPlayerInfo == null && (!isYes || aName.isEmpty())) {
            doDialog(
                    Dialogs.DIALOG_CREATEUSERERROR,
                    true,
                    getString("ENTER_YOUR_NAME", "Enter Your Name"),
                    getString("USER_ERROR_MESSAGE",
                            "Please enter your name to create a new user profile for storing high score data and game progress."),
                    "[DIALOG_BUTTON_OK]",
                    Dialog.BUTTONS_FOOTER);
        } else if (!isYes) {
            killDialog(Dialogs.DIALOG_CREATEUSER);
        } else {
            PlayerInfo aProfile = mProfileMgr.addProfile(aName);
            if (aProfile == null) {
                doDialog(
                        Dialogs.DIALOG_CREATEUSERERROR,
                        true,
                        getString("NAME_CONFLICT", "Name Conflict"),
                        getString("ENTER_UNIQUE_PLAYER_NAME",
                                "The name you entered is already being used.  Please enter a unique player name."),
                        "[DIALOG_BUTTON_OK]",
                        Dialog.BUTTONS_FOOTER);
            } else {
                mProfileMgr.save();
                mPlayerInfo = aProfile;

                killDialog(Dialogs.DIALOG_USERDIALOG);
                killDialog(Dialogs.DIALOG_CREATEUSER);
                mWidgetManager.markAllDirty();

                if (mGameSelector != null) {
                    mGameSelector.syncProfile(true);
                }
            }
        }
    }

    // LawnApp::GetFormattedString (LawnApp.cpp:837)
    public String getFormattedString(String theComponentId, String theDefault, Object... theArgs) {
        String aFormat = getString(theComponentId, theDefault);
        if (theArgs == null || theArgs.length == 0) {
            return aFormat;
        }
        return String.format(aFormat, theArgs);
    }

    // LawnApp::DoConfirmDeleteUserDialog (LawnApp.cpp:869)
    public void doConfirmDeleteUserDialog(String theName) {
        killDialog(Dialogs.DIALOG_CONFIRMDELETEUSER);
        doDialog(
                Dialogs.DIALOG_CONFIRMDELETEUSER,
                true,
                getString("ARE_YOU_SURE", "Are You Sure?"),
                getFormattedString("DELETE_USER_WARNING",
                        "This will permanently remove '%s' from the player roster!", theName),
                "",
                Dialog.BUTTONS_YES_NO);
    }

    // LawnApp::FinishConfirmDeleteUserDialog (LawnApp.cpp:882)
    public void finishConfirmDeleteUserDialog(boolean isYes) {
        killDialog(Dialogs.DIALOG_CONFIRMDELETEUSER);
        UserDialog aUserDialog = (UserDialog) getDialog(Dialogs.DIALOG_USERDIALOG);
        if (aUserDialog == null) {
            return;
        }

        mWidgetManager.setFocus(aUserDialog);

        if (!isYes) {
            return;
        }

        String aCurName = mPlayerInfo != null ? mPlayerInfo.mName : "";
        String aName = aUserDialog.getSelName();
        if (aName.equals(aCurName)) {
            mPlayerInfo = null;
        }

        mProfileMgr.deleteProfile(aName);
        aUserDialog.finishDeleteUser();
        if (mPlayerInfo == null) {
            mPlayerInfo = mProfileMgr.getProfile(aUserDialog.getSelName());
            if (mPlayerInfo == null) {
                mPlayerInfo = mProfileMgr.getAnyProfile();
            }
        }

        mProfileMgr.save();
        if (mPlayerInfo == null) {
            doCreateUserDialog();
        }

        mWidgetManager.markAllDirty();
        if (mGameSelector != null) {
            mGameSelector.syncProfile(true);
        }
    }

    // LawnApp::DoRenameUserDialog (LawnApp.cpp:925)
    public void doRenameUserDialog(String theName) {
        killDialog(Dialogs.DIALOG_RENAMEUSER);

        NewUserDialog aDialog = new NewUserDialog(this, true);
        centerDialog(aDialog, aDialog.mWidth, aDialog.mHeight);
        aDialog.setName(theName);
        addDialog(Dialogs.DIALOG_RENAMEUSER, aDialog);
    }

    // LawnApp::FinishRenameUserDialog (LawnApp.cpp:935)
    public void finishRenameUserDialog(boolean isYes) {
        UserDialog aUserDialog = (UserDialog) getDialog(Dialogs.DIALOG_USERDIALOG);
        if (!isYes) {
            killDialog(Dialogs.DIALOG_RENAMEUSER);
            mWidgetManager.setFocus(aUserDialog);
            return;
        }

        NewUserDialog aNewUserDialog = (NewUserDialog) getDialog(Dialogs.DIALOG_RENAMEUSER);
        if (aUserDialog == null || aNewUserDialog == null) {
            return;
        }

        String anOldName = aUserDialog.getSelName();
        String aNewName = aNewUserDialog.getName();
        if (aNewName.isEmpty()) {
            return;
        }

        boolean isCurrentUser = mProfileMgr.getProfile(anOldName) == mPlayerInfo;
        if (!mProfileMgr.renameProfile(anOldName, aNewName)) {
            doDialog(
                    Dialogs.DIALOG_RENAMEUSERERROR,
                    true,
                    getString("NAME_CONFLICT", "Name Conflict"),
                    getString("ENTER_UNIQUE_PLAYER_NAME",
                            "The name you entered is already being used.  Please enter a unique player name."),
                    "[DIALOG_BUTTON_OK]",
                    Dialog.BUTTONS_FOOTER);
            return;
        }

        mProfileMgr.save();
        if (isCurrentUser) {
            mPlayerInfo = mProfileMgr.getProfile(aNewName);
        }

        aUserDialog.finishRenameUser(aNewName);
        mWidgetManager.markAllDirty();
        killDialog(Dialogs.DIALOG_RENAMEUSER);
        mWidgetManager.setFocus(aUserDialog);
    }

    // LawnApp::FinishNameError (LawnApp.cpp:981)
    public void finishNameError(int theId) {
        killDialog(theId);

        NewUserDialog aNewUserDialog = (NewUserDialog) getDialog(
                theId == Dialogs.DIALOG_CREATEUSERERROR ? Dialogs.DIALOG_CREATEUSER : Dialogs.DIALOG_RENAMEUSER);
        if (aNewUserDialog != null) {
            mWidgetManager.setFocus(aNewUserDialog.mNameEditWidget);
        }
    }

    // LawnApp::FinishRestartConfirmDialog (LawnApp.cpp:992)
    public void finishRestartConfirmDialog() {
        mSawYeti = mBoard.mKilledYeti;

        killDialog(Dialogs.DIALOG_CONTINUE);
        killDialog(Dialogs.DIALOG_RESTARTCONFIRM);
        killBoard();

        preNewGame(mGameMode, false);
    }

    /**
     * NewOptionsDialog.cpp:366-380 — the "restart this level?" answer for the options screen.
     * Upstream runs this right after the blocking {@code Dialog::WaitForResult()}; this port applies
     * it when the confirm dialog's "Yes" is depressed (see the PORT-NOTE in
     * {@code NewOptionsDialog.buttonDepress}).
     */
    public void restartLevelFromOptions() {
        mMusic.stopAllMusic();
        mSoundSystem.cancelPausedFoley();
        killNewOptionsDialog();
        mBoardResult = BoardResult.BOARDRESULT_RESTART;
        mSawYeti = mBoard.mKilledYeti;
        preNewGame(mGameMode, false);
    }

    // LawnApp::DoCheatDialog (LawnApp.cpp:1003)
    public void doCheatDialog() {
        killDialog(Dialogs.DIALOG_CHEAT);

        CheatDialog aDialog = new CheatDialog(this);
        centerDialog(aDialog, aDialog.mWidth, aDialog.mHeight);
        addDialog(Dialogs.DIALOG_CHEAT, aDialog);
    }

    /**
     * PORT-NOTE (deviation): F1 opens the original hidden level-jump dialog ("Enter New Level") for
     * everyone — upstream only exposed it through the debug 'l' key with -cheat (Board.cpp KeyChar
     * 'l' → DoCheatDialog). The level behind it needs no explicit pause: the cheat dialog is modal,
     * and {@link #modalOpen}/{@link #modalClose} already pause and resume the board around it.
     */
    @Override
    public void f1Pressed() {
        if (!mDebugLaunch) {
            return; // the level-jump dialog is a debug-launch feature
        }
        if (mBoard == null || mGameScene != GameScenes.SCENE_PLAYING) {
            return;
        }
        if (getDialogCount() > 0) {
            return;
        }

        doCheatDialog();
    }

    // LawnApp::FinishCheatDialog (LawnApp.cpp:1012)
    public void finishCheatDialog(boolean isYes) {
        CheatDialog aCheatDialog = (CheatDialog) getDialog(Dialogs.DIALOG_CHEAT);
        if (aCheatDialog == null) {
            return;
        }

        if (isYes && !aCheatDialog.applyCheat()) {
            return;
        }

        killDialog(Dialogs.DIALOG_CHEAT);
        if (isYes) {
            mMusic.stopAllMusic();
            mBoardResult = BoardResult.BOARDRESULT_CHEAT;
            preNewGame(mGameMode, false);
        }
    }

    // LawnApp::FinishTimesUpDialog (LawnApp.cpp:1030)
    public void finishTimesUpDialog() {
        killDialog(Dialogs.DIALOG_TIMESUP);
    }

    // LawnApp::DoConfirmSellDialog (LawnApp.cpp:1035)
    public void doConfirmSellDialog(String theMessage) {
        doDialog(Dialogs.DIALOG_ZEN_SELL, true, "[ZEN_SELL_HEADER]", theMessage, "", Dialog.BUTTONS_YES_NO);
    }

    // LawnApp::KillNewOptionsDialog (LawnApp.cpp:1056)
    public boolean killNewOptionsDialog() {
        NewOptionsDialog aNewOptionsDialog = (NewOptionsDialog) getDialog(Dialogs.DIALOG_NEWOPTIONS);
        if (aNewOptionsDialog == null) {
            return false;
        }

        boolean wantWindowed = !aNewOptionsDialog.mFullscreenCheckbox.isChecked();
        boolean want3D = aNewOptionsDialog.mHardwareAccelerationCheckbox.isChecked();
        switchScreenMode(wantWindowed, want3D, false);

        killDialog(Dialogs.DIALOG_NEWOPTIONS);
        clearUpdateBacklog(false);
        return true;
    }

    // LawnApp::KillAlmanacDialog (LawnApp.cpp:1071)
    public boolean killAlmanacDialog() {
        if (getDialog(Dialogs.DIALOG_ALMANAC) != null) {
            killDialog(Dialogs.DIALOG_ALMANAC);
            clearUpdateBacklog(false);
            return true;
        }

        return false;
    }

    // LawnApp::NeedPauseGame (LawnApp.cpp:1083)
    public boolean needPauseGame() {
        if (mDialogList.isEmpty()) {
            return false;
        }

        if (mDialogList.size() == 1 && mDialogList.get(0).mId != Dialogs.DIALOG_NEW_GAME) {
            int anId = mDialogList.get(0).mId;
            if (anId == Dialogs.DIALOG_CHOOSER_WARNING || anId == Dialogs.DIALOG_PURCHASE_PACKET_SLOT
                    || anId == Dialogs.DIALOG_IMITATER) {
                return false;
            }
        }

        return (mBoard == null || mGameMode != GameMode.GAMEMODE_CHALLENGE_ZEN_GARDEN)
                && (mBoard == null || mGameMode != GameMode.GAMEMODE_TREE_OF_WISDOM);
    }

    // LawnApp::ModalOpen (LawnApp.cpp:1100)
    @Override
    public void modalOpen() {
        if (mBoard != null && needPauseGame()) {
            mBoard.pause(true);
        }
    }

    // LawnApp::ModalClose (LawnApp.cpp:1108)
    @Override
    public void modalClose() {
        if (mBoard != null && !needPauseGame()) {
            mBoard.pause(false);
        }
    }

    // LawnApp::KillDialog (LawnApp.cpp:1116)
    @Override
    public boolean killDialog(int theDialogId) {
        if (super.killDialog(theDialogId)) {
            if (mDialogMap.isEmpty() && mWidgetManager.mFocusWidget == null) {
                if (mBoard != null) {
                    mWidgetManager.setFocus(mBoard);
                } else if (mGameSelector != null) {
                    mWidgetManager.setFocus(mGameSelector);
                }
            }

            if (mBoard != null && !needPauseGame()) {
                mBoard.pause(false);
            }

            // PORT-NOTE: upstream's `DoAlmanacDialog()->WaitForResult(true)` callers regain control
            // once autoKill removed the almanac (Dialog.cpp:410-441); this port's wait does not pump
            // (sexy/Dialog.java:506), so their post-almanac work is registered with onAlmanacClosed
            // and runs here, where the almanac is actually gone.
            if (theDialogId == Dialogs.DIALOG_ALMANAC) {
                runAlmanacClosedListener();
            }

            return true;
        }

        return false;
    }

    /**
     * The work a caller runs after {@code DoAlmanacDialog()->WaitForResult(true)} returned; see
     * {@link #onAlmanacClosed} and {@link #killDialog(int)}.
     */
    private Runnable mAlmanacClosedListener;

    /**
     * Registers the caller's post-almanac work. Upstream blocks in the almanac's own
     * {@code Dialog::WaitForResult} until the player closes it (Dialog.cpp:410-441); a caller that
     * reads state afterwards would otherwise read it while the almanac is still up.
     */
    public void onAlmanacClosed(Runnable theListener) {
        mAlmanacClosedListener = theListener;
    }

    private void runAlmanacClosedListener() {
        Runnable aListener = mAlmanacClosedListener;
        mAlmanacClosedListener = null;
        if (aListener != null) {
            aListener.run();
        }
    }

    // LawnApp::ShowResourceError (LawnApp.cpp:1143)
    @Override
    public void showResourceError(boolean doExit) {
        super.showResourceError(doExit);
    }

    // LawnApp::FinishModelessDialogs (LawnApp.cpp:3250)
    public void finishModelessDialogs() {
        // Kill dialogs bound to the board; a killed dialog counts as cancelled and deletion is deferred
        killDialog(Dialogs.DIALOG_CONFIRM_RESTART);
        killDialog(Dialogs.DIALOG_CONFIRM_BACK_TO_MAIN);
        killDialog(Dialogs.DIALOG_PAUSED);
        killDialog(Dialogs.DIALOG_ALMANAC); // may be mid-WaitForResult with the options dialog parked under it
        killNewOptionsDialog();
    }

    // =========================================================================
    // Frame hooks
    // =========================================================================

    // LawnApp::UpdatePlayTimeStats (LawnApp.cpp:1498)
    public void updatePlayTimeStats() {
        int aTickCount = ticks();
        int aSession = (aTickCount - sLastPlayTimeTick) / 1000;

        if (mPlayerInfo != null && mPlayerInfo.mHasUsedCheatKeys == 0 && !mDebugKeysEnabled && mCheatKeys) {
            mPlayerInfo.mHasUsedCheatKeys = 1;
        }

        if (sLastPlayTimeTick == -1) {
            sLastPlayTimeTick = aTickCount;
            return;
        }

        if (aSession > 0) {
            sLastPlayTimeTick = aTickCount;

            if ((mBoard == null || !mBoard.mPaused) && mHasFocus && mLastTimerTime - mLastUserInputTick <= 10000) {
                mPlayTimeActiveSession += aSession;

                if (mBoard != null) {
                    mBoard.mPlayTimeActiveLevel += aSession;
                }

                if (mPlayerInfo != null) {
                    mPlayerInfo.mPlayTimeActivePlayer += aSession;
                }
            } else {
                mPlayTimeInactiveSession += aSession;

                if (mBoard != null) {
                    mBoard.mPlayTimeInactiveLevel += aSession;
                }

                if (mPlayerInfo != null) {
                    mPlayerInfo.mPlayTimeInactivePlayer += aSession;
                }
            }
        }
    }

    // LawnApp::UpdateFrames (LawnApp.cpp:1551)
    @Override
    public void updateFrames() {
        if ((!mActive || mMinimized) && mBoard != null) {
            mBoard.resetFPSStats();
        }

        updatePlayTimeStats();

        int aUpdateCount = 1;
        if (gSlowMo) {
            ++gSlowMoCounter;
            if (gSlowMoCounter < 4) {
                aUpdateCount = 0;
            } else {
                gSlowMoCounter = 0;
            }
        } else if (gFastMo) {
            aUpdateCount = 20;
        } else if (gCheatFastMo) {
            // PORT-NOTE (deviation): the "fast" hidden cheat word runs the game at exactly 2×.
            aUpdateCount = 2;
        }

        for (int i = 0; i < aUpdateCount; i++) {
            mAppCounter++;

            if (mBoard != null) {
                mBoard.processDeleteQueue();
            }
            if (mLoadingThreadCompleted.get() && mEffectSystem != null) {
                mEffectSystem.processDeleteQueue();
            }

            super.updateFrames();

            mMusic.musicUpdate();

            checkForGameEnd();
        }
    }

    // LawnApp::ToggleSlowMo (LawnApp.cpp:1601)
    public void toggleSlowMo() {
        gSlowMoCounter = 0;
        gSlowMo = !gSlowMo;
        gFastMo = false;
    }

    // LawnApp::ToggleFastMo (LawnApp.cpp:1608)
    public void toggleFastMo() {
        gSlowMo = false;
        gFastMo = !gFastMo;
    }

    // LawnApp::UpdateAppStep (LawnApp.cpp:2197)
    @Override
    public boolean updateAppStep(boolean[] updated) {
        if (mCloseRequest) {
            shutdown();
            return false;
        }
        pollScreenshotNotification();
        return super.updateAppStep(updated);
    }

    /** PORT-NOTE (deviation): the F2 screenshot caption, marshalled from the save worker thread. */
    private volatile String mScreenshotNotification;
    /** The transient caption state (drawn over any screen, see drawTransientMessage). */
    private String mTransientMessage;
    private long mTransientMessageUntilTick;
    private boolean mTransientMessageCleanupDone;

    /**
     * Called by the screenshot save worker (see screenshotRequested): the file's path on success,
     * the failure reason on a failed save — either way the caption to show is queued.
     */
    void onScreenshotFinished(java.nio.file.Path theFile, String theFailureReason) {
        if (theFile != null) {
            mScreenshotNotification = PvzpStringFile.translate("[SCREENSHOT_SAVED]")
                    .replace("{FILENAME}", theFile.getFileName().toString());
        } else {
            mScreenshotNotification = PvzpStringFile.translate("[SCREENSHOT_SAVE_FAILED]")
                    .replace("{REASON}", theFailureReason);
        }
    }

    /** Flushes the queued screenshot caption into the transient caption (drawn over any screen). */
    private void pollScreenshotNotification() {
        if (mScreenshotNotification == null) {
            return;
        }
        String aMessage = mScreenshotNotification;
        mScreenshotNotification = null;
        showTransientMessage(aMessage, 3000);
    }

    @Override
    public void showTransientMessage(String theMessage, int theDurationMs) {
        mTransientMessage = theMessage;
        mTransientMessageUntilTick = ticks() + theDurationMs;
        mTransientMessageCleanupDone = false;
        mWidgetManager.markAllDirty();
    }

    /** Draws the transient caption top-centre with the debug overlay's shaded font. */
    @Override
    public void drawTransientMessage(Graphics g) {
        if (mTransientMessage == null) {
            return;
        }
        if (ticks() >= mTransientMessageUntilTick) {
            // expired: the canvas keeps the last drawn caption baked in - one full redraw erases it
            if (!mTransientMessageCleanupDone) {
                mTransientMessageCleanupDone = true;
                mWidgetManager.markAllDirty();
            }
            return;
        }

        Font aFont = Resources.getFontById(Resources.FONT_PICO129_ID);
        if (aFont == null || aFont.mHeight <= 0) {
            return;
        }
        g.setFont(aFont);
        g.setColor(Color.White);
        int aX = GameConstants.BOARD_WIDTH / 2 - aFont.stringWidth(mTransientMessage) / 2;
        g.drawStringShaded(mTransientMessage, aX, 4 + aFont.getAscent(), Color.Black);
    }

    /**
     * PORT-NOTE (deviation): F2 saves a screenshot (see SexyAppBase.screenshotRequested); this game
     * slice adds the [SCREENSHOT_SAVED] / [SCREENSHOT_SAVE_FAILED] caption once the save worker
     * reports the outcome.
     */
    @Override
    public void screenshotRequested() {
        if (mRendererBackend != null) {
            mRendererBackend.requestScreenshot(this::onScreenshotFinished);
        }
    }

    // LawnApp::UpdateApp (LawnApp.cpp:2207)
    @Override
    public boolean updateApp() {
        if (mCloseRequest) {
            shutdown();
            return false;
        }

        return super.updateApp();
    }

    // LawnApp::CloseRequestAsync (LawnApp.cpp:2230)
    @Override
    public void closeRequestAsync() {
        mExitToTop = true;
        mCloseRequest = true;
    }

    // =========================================================================
    // Loading thread (LawnApp.cpp:1614-1779)
    // =========================================================================

    // LawnApp::LoadGroup (LawnApp.cpp:1614)
    public void loadGroup(String theGroupName, int theGroupAveMsToLoad) {
        mResourceManager.startLoadResources(theGroupName);
        while (!mShutdown.get() && !mCloseRequest && !mLoadingFailed.get()
                && pvzpResources().pvzpLoadNextResource()) {
            mCompletedLoadingThreadTasks.addAndGet(theGroupAveMsToLoad);
        }

        if (mShutdown.get() || mCloseRequest) {
            return;
        }

        if (mResourceManager.hadError() || !Resources.extractResourcesByName(mResourceManager, theGroupName)) {
            showResourceError(false);
            mLoadingFailed.set(true);
        }
    }

    // LawnApp::LoadingThreadProc (LawnApp.cpp:1635)
    @Override
    public void loadingThreadProc() {
        if (!pvzpResources().pvzpLoadResources("LoaderBar")) {
            return;
        }

        PvzpStringFile.load("Properties/LawnStrings.txt");
        PvzpStringFile.readFile("Properties/ZombatarTOS.txt");

        // Load localized properties AFTER LawnStrings so they can override string values
        loadProperties("properties/default.xml", false, false);
        loadProperties("properties/Layout.xml", false, false);
        loadProperties("properties/pvz-portable.xml", false, false);

        if (mTitleScreen != null) {
            mTitleScreen.mLoaderScreenIsLoaded = true;
        }

        String[] groups = { "LoadingFonts", "LoadingImages", "LoadingSounds" };
        int[] groupAveMsToLoad = { 54, 9, 54 };
        for (int i = 0; i < 3; i++) {
            mNumLoadingThreadTasks.addAndGet(mResourceManager.getNumResources(groups[i]) * groupAveMsToLoad[i]);
        }
        mNumLoadingThreadTasks.addAndGet(636);
        mNumLoadingThreadTasks.addAndGet(getNumPreloadingTasks());
        mNumLoadingThreadTasks.addAndGet(Music.MUSIC_LOADING_TASKS);

        loadGroup("LoadingImages", 9);
        loadGroup("LoadingFonts", 54);
        if (mLoadingFailed.get() || mShutdown.get() || mCloseRequest) {
            return;
        }
        mDefaultFont = Resources.getFontById(Resources.FONT_PICO129_ID); // framework widgets fall back to this when no font is set

        mMusic.musicInit();

        mPoolEffect = new PoolEffect();
        mPoolEffect.poolEffectInitialize();
        mReanimatorCache = new ReanimatorCache();
        mReanimatorCache.reanimatorCacheInitialize();
        PvzpFoley.pvzpFoleyInitialize(PvzpFoley.gLawnFoleyParamArray, PvzpFoley.gLawnFoleyParamArray.length);

        // Upstream also constructs mZenGarden here (LawnApp.cpp:1685); the Zen Garden gameplay is
        // implemented by ZenGarden.
        mZenGarden = new ZenGarden();
        TrailDefinitions.trailLoadDefinitions(TrailDefinitions.gLawnTrailArray, TrailDefinitions.gLawnTrailArray.length);
        PvzpParticleDefinitions.pvzpParticleLoadDefinitions(
                PvzpParticleDefinitions.gLawnParticleArray, PvzpParticleDefinitions.gLawnParticleArray.length);

        preloadForUser();
        if (mLoadingFailed.get() || mShutdown.get() || mCloseRequest) {
            return;
        }

        loadGroup("LoadingSounds", 54);
    }

    // LawnApp::FastLoad (LawnApp.cpp:1711)
    public void fastLoad(int theGameMode) {
        if (!mShutdown.get()) {
            mWidgetManager.removeWidget(mTitleScreen);
            safeDeleteWidget(mTitleScreen);
            mTitleScreen = null;

            preNewGame(theGameMode, false);
        }
    }

    // LawnApp::LoadingThreadCompleted (LawnApp.cpp:1722)
    @Override
    public void loadingThreadCompleted() {
    }

    // LawnApp::LoadingCompleted (LawnApp.cpp:1726)
    public void loadingCompleted() {
        mWidgetManager.removeWidget(mTitleScreen);
        safeDeleteWidget(mTitleScreen);
        mTitleScreen = null;

        mResourceManager.deleteImage("IMAGE_TITLESCREEN");

        showGameSelector();
    }

    // LawnApp::URLOpenFailed (LawnApp.cpp:1736)
    @Override
    public void urlOpenFailed(String theURL) {
        super.urlOpenFailed(theURL);
        killDialog(Dialogs.DIALOG_OPENURL_WAIT);
        copyToClipboard(theURL);

        String aString = getFormattedString("OPEN_URL",
                "Please open the following URL in your browser\n\n%s\n\nFor your convenience, this URL has already been copied to your clipboard.",
                theURL);

        doDialog(Dialogs.DIALOG_OPENURL_WAIT, true, getString("OPEN_BROWSER", "Open Browser"),
                aString, "[DIALOG_BUTTON_OK]", Dialog.BUTTONS_FOOTER);
    }

    // LawnApp::URLOpenSucceeded (LawnApp.cpp:1748)
    @Override
    public void urlOpenSucceeded(String theURL) {
        super.urlOpenSucceeded(theURL);
        killDialog(Dialogs.DIALOG_OPENURL_WAIT);
    }

    // LawnApp::OpenURL (LawnApp.cpp:1754)
    @Override
    public boolean openURL(String theURL, boolean shutdownOnOpen) {
        doDialog(
                Dialogs.DIALOG_OPENURL_WAIT,
                true,
                getString("OPENING_BROWSER", "Opening Browser"),
                getString("OPENING_BROWSER", "Opening Browser"),
                "",
                Dialog.BUTTONS_NONE);

        drawDirtyStuff();

        return super.openURL(theURL, shutdownOnOpen);
    }

    // LawnApp::ConfirmQuit (LawnApp.cpp:1770)
    public void confirmQuit() {
        String aBody = PvzpStringFile.translate("[QUIT_MESSAGE]");
        String aHeader = PvzpStringFile.translate("[QUIT_HEADER]");
        LawnDialog aDialog = (LawnDialog) doDialog(Dialogs.DIALOG_QUIT, true, aHeader, aBody, "",
                Dialog.BUTTONS_OK_CANCEL);
        aDialog.mLawnYesButton.mLabel = PvzpStringFile.translate("[QUIT_BUTTON]");
        centerDialog(aDialog, aDialog.mWidth, aDialog.mHeight);
    }

    // LawnApp::PreDisplayHook (LawnApp.cpp:1779)
    @Override
    public void preDisplayHook() {
        super.preDisplayHook();
    }

    // =========================================================================
    // Button handling (LawnApp.cpp:1785-1948)
    // =========================================================================

    @Override
    public void buttonPress(int theId) {
    }

    @Override
    public void buttonDownTick(int theId) {
    }

    @Override
    public void buttonMouseEnter(int theId) {
    }

    @Override
    public void buttonMouseLeave(int theId) {
    }

    @Override
    public void buttonMouseMove(int theId, int theX, int theY) {
    }

    // LawnApp::ButtonDepress (LawnApp.cpp:1791)
    @Override
    public void buttonDepress(int theId) {
        if (theId % 10000 >= 2000 && theId % 10000 < 3000) { // the "Yes" button of dialog (theId - 2000)
            switch (theId - 2000) {
                case Dialogs.DIALOG_NEW_GAME:
                    killDialog(Dialogs.DIALOG_NEW_GAME);
                    showGameSelector();
                    return;
                case Dialogs.DIALOG_NEWOPTIONS:
                    killNewOptionsDialog();
                    return;
                case Dialogs.DIALOG_PREGAME_NAG:
                    doRegister();
                    return;
                case Dialogs.DIALOG_LOAD_GAME:
                    return;
                case Dialogs.DIALOG_CONFIRM_UPDATE_CHECK:
                    killDialog(Dialogs.DIALOG_CONFIRM_UPDATE_CHECK);
                    checkForUpdates();
                    return;
                case Dialogs.DIALOG_QUIT:
                    killDialog(Dialogs.DIALOG_QUIT);
                    closeRequestAsync();
                    return;
                case Dialogs.DIALOG_NAG:
                    killDialog(Dialogs.DIALOG_NAG);
                    doRegister();
                    return;
                case Dialogs.DIALOG_INFO:
                    killDialog(Dialogs.DIALOG_INFO);
                    return;
                case Dialogs.DIALOG_PAUSED:
                    killDialog(Dialogs.DIALOG_PAUSED);
                    return;
                case Dialogs.DIALOG_NO_MORE_MONEY:
                    killDialog(Dialogs.DIALOG_NO_MORE_MONEY);
                    mBoard.addSunMoney(100);
                    return;
                case Dialogs.DIALOG_BONUS:
                    killDialog(Dialogs.DIALOG_BONUS);
                    return;
                case Dialogs.DIALOG_CONFIRM_BACK_TO_MAIN:
                    killDialog(Dialogs.DIALOG_CONFIRM_BACK_TO_MAIN);
                    mBoardResult = BoardResult.BOARDRESULT_QUIT;
                    mBoard.tryToSaveGame();
                    doBackToMain();
                    return;
                case Dialogs.DIALOG_USERDIALOG:
                    finishUserDialog(true);
                    return;
                case Dialogs.DIALOG_CREATEUSER:
                    finishCreateUserDialog(true);
                    return;
                case Dialogs.DIALOG_CONFIRMDELETEUSER:
                    finishConfirmDeleteUserDialog(true);
                    return;
                case Dialogs.DIALOG_RENAMEUSER:
                    finishRenameUserDialog(true);
                    return;
                case Dialogs.DIALOG_CREATEUSERERROR:
                case Dialogs.DIALOG_RENAMEUSERERROR:
                    finishNameError(theId - 2000);
                    return;
                case Dialogs.DIALOG_CHEAT:
                    finishCheatDialog(true);
                    return;
                case Dialogs.DIALOG_RESTARTCONFIRM:
                    finishRestartConfirmDialog();
                    return;
                case Dialogs.DIALOG_TIMESUP:
                    finishTimesUpDialog();
                    return;
                // PORT-NOTE: these two dialogs ask a question and then act on the answer. Upstream
                // answers them with the blocking Dialog::WaitForResult() (StoreScreen.cpp:1014,
                // CutScene.cpp:1618); this port's dialogs do not block (sexy/Dialog.java:506), so the
                // answer is delivered here instead - the same place every other dialog's button is
                // handled (upstream's LawnApp::ButtonDepress switch, LawnApp.cpp:1791).
                case Dialogs.DIALOG_STORE_PURCHASE: {
                    killDialog(Dialogs.DIALOG_STORE_PURCHASE);
                    StoreScreen aStore = (StoreScreen) getDialog(Dialogs.DIALOG_STORE);
                    if (aStore != null) {
                        aStore.finishPurchaseConfirmation(true);
                    }
                    return;
                }
                case Dialogs.DIALOG_PURCHASE_PACKET_SLOT:
                    killDialog(Dialogs.DIALOG_PURCHASE_PACKET_SLOT);
                    if (mBoard != null && mBoard.mCutScene != null) {
                        mBoard.mCutScene.answerPacketUpgradeOffer(true);
                    }
                    return;
                // PORT-NOTE: same class as above — the options screen's "restart this level?" and the
                // Zen garden's "sell this plant?" ask with a blocking Dialog::WaitForResult()
                // (NewOptionsDialog.cpp:366-380, ZenGarden.cpp:487-491); their answers are applied
                // here because this port delivers a dialog's answer when its button is depressed.
                case Dialogs.DIALOG_CONFIRM_RESTART:
                    killDialog(Dialogs.DIALOG_CONFIRM_RESTART);
                    restartLevelFromOptions();
                    return;
                case Dialogs.DIALOG_ZEN_SELL:
                    killDialog(Dialogs.DIALOG_ZEN_SELL);
                    mZenGarden.finishSellPlant(true);
                    return;
                case 20008:
                    killDialog(20008);
                    killDialog(Dialogs.DIALOG_CHECKING_UPDATES);
                    return;
                default:
                    killDialog(theId - 2000);
                    finishLawnMessageBox(theId - 2000, true);
                    return;
            }
        }

        // PORT-NOTE: upstream tests `theId < 4000` rather than `theId % 10000 < 4000`
        // (LawnApp.cpp:1885); reproduced verbatim so PvZ's existing ids behave identically.
        if (theId % 10000 >= 3000 && theId < 4000) { // the "No" button of dialog (theId - 3000)
            switch (theId - 3000) {
                case Dialogs.DIALOG_PREGAME_NAG:
                    killDialog(Dialogs.DIALOG_PREGAME_NAG);
                    shutdown();
                    return;
                case Dialogs.DIALOG_LOAD_GAME:
                    killDialog(Dialogs.DIALOG_LOAD_GAME);
                    return;
                case Dialogs.DIALOG_USERDIALOG:
                    finishUserDialog(false);
                    return;
                case Dialogs.DIALOG_CREATEUSER:
                    finishCreateUserDialog(false);
                    return;
                case Dialogs.DIALOG_CONFIRMDELETEUSER:
                    finishConfirmDeleteUserDialog(false);
                    return;
                case Dialogs.DIALOG_RENAMEUSER:
                    finishRenameUserDialog(false);
                    return;
                case Dialogs.DIALOG_CHEAT:
                    finishCheatDialog(false);
                    return;
                case Dialogs.DIALOG_TIMESUP:
                    finishTimesUpDialog();
                    return;
                // PORT-NOTE: the "No" halves of the two dialogs added in the "Yes" switch above.
                case Dialogs.DIALOG_STORE_PURCHASE: {
                    killDialog(Dialogs.DIALOG_STORE_PURCHASE);
                    StoreScreen aStore = (StoreScreen) getDialog(Dialogs.DIALOG_STORE);
                    if (aStore != null) {
                        aStore.finishPurchaseConfirmation(false);
                    }
                    return;
                }
                case Dialogs.DIALOG_PURCHASE_PACKET_SLOT:
                    killDialog(Dialogs.DIALOG_PURCHASE_PACKET_SLOT);
                    if (mBoard != null && mBoard.mCutScene != null) {
                        mBoard.mCutScene.answerPacketUpgradeOffer(false);
                    }
                    return;
                case Dialogs.DIALOG_ZEN_SELL:
                    killDialog(Dialogs.DIALOG_ZEN_SELL);
                    mZenGarden.finishSellPlant(false);
                    return;
                case 10008:
                    killDialog(10008);
                    killDialog(Dialogs.DIALOG_CHECKING_UPDATES);
                    return;
                default:
                    killDialog(theId - 3000);
                    finishLawnMessageBox(theId - 3000, false);
                    return;
            }
        }
    }

    // LawnApp::CenterDialog (LawnApp.cpp:1950)
    public static void centerDialog(Dialog theDialog, int theWidth, int theHeight) {
        theDialog.resize((GameConstants.BOARD_WIDTH - theWidth) / 2,
                (GameConstants.BOARD_HEIGHT - theHeight) / 2, theWidth, theHeight);
    }

    // =========================================================================
    // Music / foley
    // =========================================================================

    // LawnApp::PlayFoley (LawnApp.cpp:1955)
    public void playFoley(int theFoleyType) {
        if (!mMuteSoundsForCutscene) {
            mSoundSystem.playFoley(theFoleyType);
        }
    }

    // LawnApp::PlayFoleyPitch (LawnApp.cpp:1963)
    public void playFoleyPitch(int theFoleyType, float thePitch) {
        if (!mMuteSoundsForCutscene) {
            mSoundSystem.playFoleyPitch(theFoleyType, thePitch);
        }
    }

    // LawnApp::PlaySample (LawnApp.cpp:3206)
    @Override
    public void playSample(long theSoundNum) {
        if (!mMuteSoundsForCutscene) {
            // The argument is a resource entry id, while the mixer is addressed by the id the loader
            // handed out (upstream reads the Sexy::SOUND_* global that Resources.cpp:2195 assigned from
            // GetSoundThrow). Without this translation every sound whose entry id is past the
            // 256-source limit - the Crazy Dave laugh, the map roll-in, most of the game - is silent.
            super.playSample(Resources.getSoundById((int) theSoundNum));
        }
    }

    // LawnApp::SwitchScreenMode (LawnApp.cpp:3214)
    @Override
    public void switchScreenMode(boolean wantWindowed, boolean is3d, boolean force) {
        super.switchScreenMode(wantWindowed, is3d, force);

        NewOptionsDialog aNewOptionsDialog = (NewOptionsDialog) getDialog(Dialogs.DIALOG_NEWOPTIONS);
        if (aNewOptionsDialog != null) {
            aNewOptionsDialog.mFullscreenCheckbox.setChecked(!mIsWindowed, false);
        }
    }

    /** Re-syncs the options dialog's debug-info checkbox after an F3 toggle, the same way
     * {@link #switchScreenMode} re-syncs the fullscreen checkbox after Alt+Enter / F11. */
    @Override
    protected void debugInfoToggled() {
        NewOptionsDialog aNewOptionsDialog = (NewOptionsDialog) getDialog(Dialogs.DIALOG_NEWOPTIONS);
        if (aNewOptionsDialog != null) {
            aNewOptionsDialog.mDebugInfoCheckbox.setChecked(mShowFPS, false);
        }
    }

    // =========================================================================
    // Mode / level predicates (LawnApp.cpp:1971-2193)
    // =========================================================================

    // LawnApp::GetStageString (LawnApp.cpp:1971)
    public static String getStageString(int theLevel) {
        int aArea = Math.clamp((theLevel - 1) / GameConstants.LEVELS_PER_AREA + 1, 1, GameConstants.ADVENTURE_AREAS + 1);
        int aSub = theLevel - (aArea - 1) * GameConstants.LEVELS_PER_AREA;
        return aArea + "-" + aSub;
    }

    public boolean isAdventureMode() {
        return mGameMode == GameMode.GAMEMODE_ADVENTURE;
    }

    public boolean isSurvivalMode() {
        return mGameMode >= GameMode.GAMEMODE_SURVIVAL_NORMAL_STAGE_1
                && mGameMode <= GameMode.GAMEMODE_SURVIVAL_ENDLESS_STAGE_5;
    }

    public boolean isPuzzleMode() {
        return (mGameMode >= GameMode.GAMEMODE_SCARY_POTTER_1 && mGameMode <= GameMode.GAMEMODE_SCARY_POTTER_ENDLESS)
                || (mGameMode >= GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_1 && mGameMode <= GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_ENDLESS);
    }

    public boolean isChallengeMode() {
        return !isAdventureMode() && !isPuzzleMode() && !isSurvivalMode();
    }

    public static boolean isSurvivalNormal(int theGameMode) {
        int aLevel = theGameMode - GameMode.GAMEMODE_SURVIVAL_NORMAL_STAGE_1;
        return aLevel >= 0 && aLevel <= 4;
    }

    public static boolean isSurvivalHard(int theGameMode) {
        int aLevel = theGameMode - GameMode.GAMEMODE_SURVIVAL_HARD_STAGE_1;
        return aLevel >= 0 && aLevel <= 4;
    }

    public static boolean isSurvivalEndless(int theGameMode) {
        int aLevel = theGameMode - GameMode.GAMEMODE_SURVIVAL_ENDLESS_STAGE_1;
        return aLevel >= 0 && aLevel <= 4;
    }

    public static boolean isEndlessScaryPotter(int theGameMode) {
        return theGameMode == GameMode.GAMEMODE_SCARY_POTTER_ENDLESS;
    }

    public static boolean isEndlessIZombie(int theGameMode) {
        return theGameMode == GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_ENDLESS;
    }

    public boolean isContinuousChallenge() {
        return isArtChallenge()
                || isSlotMachineLevel()
                || isFinalBossLevel()
                || mGameMode == GameMode.GAMEMODE_CHALLENGE_BEGHOULED
                || mGameMode == GameMode.GAMEMODE_UPSELL
                || mGameMode == GameMode.GAMEMODE_INTRO
                || mGameMode == GameMode.GAMEMODE_CHALLENGE_BEGHOULED_TWIST;
    }

    public boolean isArtChallenge() {
        if (mBoard == null) {
            return false;
        }

        return mGameMode == GameMode.GAMEMODE_CHALLENGE_ART_CHALLENGE_WALLNUT
                || mGameMode == GameMode.GAMEMODE_CHALLENGE_ART_CHALLENGE_SUNFLOWER
                || mGameMode == GameMode.GAMEMODE_CHALLENGE_SEEING_STARS;
    }

    public boolean isSquirrelLevel() {
        return mBoard != null && mGameMode == GameMode.GAMEMODE_CHALLENGE_SQUIRREL;
    }

    public boolean isIZombieLevel() {
        if (mBoard == null) {
            return false;
        }

        return mGameMode >= GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_1
                && mGameMode <= GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_ENDLESS;
    }

    public boolean isShovelLevel() {
        return mBoard != null && mGameMode == GameMode.GAMEMODE_CHALLENGE_SHOVEL;
    }

    public boolean isWallnutBowlingLevel() {
        if (mBoard == null) {
            return false;
        }

        if (mGameMode == GameMode.GAMEMODE_CHALLENGE_WALLNUT_BOWLING
                || mGameMode == GameMode.GAMEMODE_CHALLENGE_WALLNUT_BOWLING_2) {
            return true;
        }

        return isAdventureMode() && mBoard.mLevel == 5;
    }

    public boolean isSlotMachineLevel() {
        return mBoard != null && mGameMode == GameMode.GAMEMODE_CHALLENGE_SLOT_MACHINE;
    }

    public boolean isWhackAZombieLevel() {
        if (mBoard == null) {
            return false;
        }

        if (mGameMode == GameMode.GAMEMODE_CHALLENGE_WHACK_A_ZOMBIE) {
            return true;
        }

        return isAdventureMode() && mBoard.mLevel == 15;
    }

    public boolean isLittleTroubleLevel() {
        return mBoard != null && (mGameMode == GameMode.GAMEMODE_CHALLENGE_LITTLE_TROUBLE
                || (mGameMode == GameMode.GAMEMODE_ADVENTURE && mBoard.mLevel == 25));
    }

    public boolean isScaryPotterLevel() {
        if (mGameMode >= GameMode.GAMEMODE_SCARY_POTTER_1 && mGameMode <= GameMode.GAMEMODE_SCARY_POTTER_ENDLESS) {
            return true;
        }

        return isAdventureMode() && mBoard != null && mBoard.mLevel == 35;
    }

    public boolean isStormyNightLevel() {
        if (mBoard == null) {
            return false;
        }

        if (mGameMode == GameMode.GAMEMODE_CHALLENGE_STORMY_NIGHT) {
            return true;
        }

        return isAdventureMode() && mBoard.mLevel == 40;
    }

    public boolean isBungeeBlitzLevel() {
        if (mBoard == null) {
            return false;
        }

        if (mGameMode == GameMode.GAMEMODE_CHALLENGE_BUNGEE_BLITZ) {
            return true;
        }

        return isAdventureMode() && mBoard.mLevel == 45;
    }

    public boolean isMiniBossLevel() {
        if (mBoard == null) {
            return false;
        }

        return isAdventureMode() && (mBoard.mLevel == 10 || mBoard.mLevel == 20 || mBoard.mLevel == 30);
    }

    public boolean isFinalBossLevel() {
        if (mBoard == null) {
            return false;
        }

        if (mGameMode == GameMode.GAMEMODE_CHALLENGE_FINAL_BOSS) {
            return true;
        }

        return isAdventureMode() && mBoard.mLevel == 50;
    }

    public boolean isChallengeWithoutSeedBank() {
        return mGameMode == GameMode.GAMEMODE_CHALLENGE_RAINING_SEEDS
                || mGameMode == GameMode.GAMEMODE_UPSELL
                || mGameMode == GameMode.GAMEMODE_INTRO
                || isWhackAZombieLevel()
                || isSquirrelLevel()
                || isScaryPotterLevel()
                || mGameMode == GameMode.GAMEMODE_CHALLENGE_ZEN_GARDEN
                || mGameMode == GameMode.GAMEMODE_TREE_OF_WISDOM;
    }

    public boolean isNight() {
        if (isIceDemo() || mPlayerInfo == null) {
            return false;
        }

        return (mPlayerInfo.mLevel >= 11 && mPlayerInfo.mLevel <= 20)
                || (mPlayerInfo.mLevel >= 31 && mPlayerInfo.mLevel <= 40)
                || mPlayerInfo.mLevel == 50;
    }

    // LawnApp::GetCurrentChallengeIndex (LawnApp.cpp:2181)
    public int getCurrentChallengeIndex() {
        return mGameMode - GameMode.GAMEMODE_SURVIVAL_NORMAL_STAGE_1;
    }

    // LawnApp::GetCurrentChallengeDef (LawnApp.cpp:2186)
    public ChallengeDefinition getCurrentChallengeDef() {
        return ChallengeScreen.getChallengeDefinition(getCurrentChallengeIndex());
    }

    // LawnApp::GetPottedPlantByIndex (LawnApp.cpp:2191)
    public PottedPlant getPottedPlantByIndex(int thePottedPlantIndex) {
        return mPlayerInfo.mPottedPlant[thePottedPlantIndex];
    }

    // LawnApp::IsIceDemo (LawnApp.h:224)
    public boolean isIceDemo() {
        return false;
    }

    // =========================================================================
    // Seed availability (LawnApp.cpp:2236-2302)
    // =========================================================================

    // LawnApp::GetAwardSeedForLevel (LawnApp.cpp:2236)
    public static int getAwardSeedForLevel(int theLevel) {
        int aArea = (theLevel - 1) / GameConstants.LEVELS_PER_AREA + 1;
        int aSub = (theLevel - 1) % GameConstants.LEVELS_PER_AREA + 1;
        int aSeedsHasGot = (aArea - 1) * 8 + aSub; // in general, each area awards 8 plants and each level awards 1
        if (aSub >= 10) {
            aSeedsHasGot -= 2; // 2 levels in this area don't award a new plant
        } else if (aSub >= 5) {
            aSeedsHasGot -= 1; // 1 level in this area doesn't award a new plant
        }
        if (aSeedsHasGot > 40) {
            aSeedsHasGot = 40;
        }

        return aSeedsHasGot;
    }

    // LawnApp::GetSeedsAvailable (LawnApp.cpp:2257)
    public int getSeedsAvailable() {
        int aLevel = mPlayerInfo.getLevel();
        if (hasFinishedAdventure() || aLevel > 50) {
            return 49;
        }

        int aSeedTypeMax = getAwardSeedForLevel(aLevel);
        return Math.min(SeedType.NUM_SEEDS_IN_CHOOSER, aSeedTypeMax);
    }

    // LawnApp::HasSeedType (LawnApp.cpp:2269)
    public boolean hasSeedType(int theSeedType) {
        if (isTrialStageLocked() && theSeedType >= SeedType.SEED_JALAPENO) {
            return false;
        }

        switch (theSeedType) {
            case SeedType.SEED_GATLINGPEA:
                return mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_PLANT_GATLINGPEA] > 0;
            case SeedType.SEED_TWINSUNFLOWER:
                return mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_PLANT_TWINSUNFLOWER] > 0;
            case SeedType.SEED_GLOOMSHROOM:
                return mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_PLANT_GLOOMSHROOM] > 0;
            case SeedType.SEED_CATTAIL:
                return mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_PLANT_CATTAIL] > 0;
            case SeedType.SEED_WINTERMELON:
                return mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_PLANT_WINTERMELON] > 0;
            case SeedType.SEED_GOLD_MAGNET:
                return mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_PLANT_GOLD_MAGNET] > 0;
            case SeedType.SEED_SPIKEROCK:
                return mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_PLANT_SPIKEROCK] > 0;
            case SeedType.SEED_COBCANNON:
                return mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_PLANT_COBCANNON] > 0;
            case SeedType.SEED_IMITATER:
                return mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_PLANT_IMITATER] > 0;
            default:
                return theSeedType < getSeedsAvailable();
        }
    }

    // =========================================================================
    // Effects (LawnApp.cpp:2304-2360)
    // =========================================================================

    public Reanimation addReanimation(float theX, float theY, int theRenderOrder, int theReanimationType) {
        return mEffectSystem.mReanimationHolder.allocReanimation(theX, theY, theRenderOrder, theReanimationType);
    }

    public PvzpParticleSystem addPvzpParticle(float theX, float theY, int theRenderOrder, int theEffect) {
        return mEffectSystem.mParticleHolder.allocParticleSystem(theX, theY, theRenderOrder, theEffect);
    }

    public int particleGetID(PvzpParticleSystem theParticle) {
        return mEffectSystem.mParticleHolder.mParticleSystems.dataArrayGetID(theParticle);
    }

    public int reanimationGetID(Reanimation theReanimation) {
        return mEffectSystem.mReanimationHolder.mReanimations.dataArrayGetID(theReanimation);
    }

    public PvzpParticleSystem particleGet(int theParticleID) {
        return mEffectSystem.mParticleHolder.mParticleSystems.dataArrayGet(theParticleID);
    }

    public PvzpParticleSystem particleTryToGet(int theParticleID) {
        return mEffectSystem.mParticleHolder.mParticleSystems.dataArrayTryToGet(theParticleID);
    }

    public Reanimation reanimationGet(int theReanimationID) {
        return mEffectSystem.mReanimationHolder.mReanimations.dataArrayGet(theReanimationID);
    }

    public Reanimation reanimationTryToGet(int theReanimationID) {
        return mEffectSystem.mReanimationHolder.mReanimations.dataArrayTryToGet(theReanimationID);
    }

    public void removeReanimation(int theReanimationID) {
        Reanimation aReanim = reanimationTryToGet(theReanimationID);
        if (aReanim != null) {
            aReanim.reanimationDie();
        }
    }

    public void removeParticle(int theParticleID) {
        PvzpParticleSystem aParticle = particleTryToGet(theParticleID);
        if (aParticle != null) {
            aParticle.particleSystemDie();
        }
    }

    // =========================================================================
    // Adventure / profile progress (LawnApp.cpp:1294-1498, 2362-2455)
    // =========================================================================

    // LawnApp::UpdatePlayerProfileForFinishingLevel (LawnApp.cpp:1294)
    public boolean updatePlayerProfileForFinishingLevel() {
        boolean aUnlockedNewChallenge = false;

        if (isAdventureMode()) {
            if (mBoard.mLevel == GameConstants.FINAL_LEVEL) {
                mPlayerInfo.setLevel(1);
                mPlayerInfo.mFinishedAdventure++;
                if (mPlayerInfo.mFinishedAdventure == 1) {
                    mPlayerInfo.mNeedsMessageOnGameSelector = 1;
                }
                ReportAchievement.giveAchievement(this, AchievementId.HomeSecurity, false);
            } else {
                mPlayerInfo.setLevel(mBoard.mLevel + 1);
            }

            if (!hasFinishedAdventure() && mBoard.mLevel == 34) {
                mPlayerInfo.mNeedsMagicTacoReward = 1;
            }
        } else if (isSurvivalMode()) {
            if (mBoard.isFinalSurvivalStage()) {
                aUnlockedNewChallenge = !hasBeatenChallenge(mGameMode);
                mBoard.survivalSaveScore();

                if (aUnlockedNewChallenge && hasFinishedAdventure()) {
                    int aNumTrophies = getNumTrophies(ChallengePage.CHALLENGE_PAGE_SURVIVAL);
                    if (aNumTrophies != 8 && aNumTrophies != 9) {
                        mPlayerInfo.mHasNewSurvival = 1;
                    }
                }
            }
        } else if (isPuzzleMode()) {
            aUnlockedNewChallenge = !hasBeatenChallenge(mGameMode);
            mPlayerInfo.mChallengeRecords[getCurrentChallengeIndex()]++;

            if (!hasFinishedAdventure() && (mGameMode == GameMode.GAMEMODE_SCARY_POTTER_3
                    || mGameMode == GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_3)) {
                aUnlockedNewChallenge = false;
            }

            if (aUnlockedNewChallenge) {
                if (isScaryPotterLevel()) {
                    mPlayerInfo.mHasNewScaryPotter = 1;
                } else {
                    mPlayerInfo.mHasNewIZombie = 1;
                }
            }
        } else {
            aUnlockedNewChallenge = !hasBeatenChallenge(mGameMode);
            mPlayerInfo.mChallengeRecords[getCurrentChallengeIndex()]++;

            if (aUnlockedNewChallenge && hasFinishedAdventure()) {
                int aNumTrophies = getNumTrophies(ChallengePage.CHALLENGE_PAGE_CHALLENGE);
                if (aNumTrophies <= 17) {
                    mPlayerInfo.mHasNewMiniGame = 1;
                }
            }

            int aNumTrophies = getNumTrophies(ChallengePage.CHALLENGE_PAGE_CHALLENGE);
            if (aNumTrophies == 20) {
                ReportAchievement.giveAchievement(this, AchievementId.BeyondTheGrave, false);
            }
        }

        if ((isAdventureMode() || isSurvivalMode()) && !isScaryPotterLevel() && !isWhackAZombieLevel()) {
            if (mBoard.stageIsDayWithPool() && !mBoard.mPeaShooterUsed) {
                ReportAchievement.giveAchievement(this, AchievementId.DontPea, false);
            } else if (mBoard.stageHasRoof() && !mBoard.hasConveyorBeltSeedBank() && !mBoard.mCatapultPlantsUsed) {
                ReportAchievement.giveAchievement(this, AchievementId.Grounded, false);
            } else if (mBoard.stageIsDayWithoutPool() && mBoard.mMushroomAndCoffeeBeansOnly) {
                ReportAchievement.giveAchievement(this, AchievementId.GoodMorning, false);
            }
            if (mBoard.stageIsNight() && !mBoard.mMushroomsUsed) {
                ReportAchievement.giveAchievement(this, AchievementId.NoFungusAmongUs, false);
            }
        }

        writeCurrentUserConfig();

        return aUnlockedNewChallenge;
    }

    // LawnApp::CheckForGameEnd (LawnApp.cpp:1396)
    public void checkForGameEnd() {
        if (mBoard == null || !mBoard.mLevelComplete) {
            return;
        }

        boolean aUnlockedNewChallenge = updatePlayerProfileForFinishingLevel();

        // Mod hook: level ended with a win (../verification/research/03-board-core.md §7.4).
        LawnHooksRegistry.dispatcher().onLevelEnd(mBoard, BoardResult.BOARDRESULT_WON);

        if (isAdventureMode()) {
            int aLevel = mBoard.mLevel;
            killBoard();

            if (isFirstTimeAdventureMode() && aLevel < 50) {
                showAwardScreen(AwardType.AWARD_FORLEVEL, true);
            } else if (aLevel == GameConstants.FINAL_LEVEL) {
                if (mPlayerInfo.mFinishedAdventure == 1) {
                    showAwardScreen(AwardType.AWARD_FORLEVEL, true);
                } else {
                    showAwardScreen(AwardType.AWARD_CREDITS_ZOMBIENOTE, true);
                }
            } else if (aLevel == 9 || aLevel == 19 || aLevel == 29 || aLevel == 39 || aLevel == 49) {
                showAwardScreen(AwardType.AWARD_FORLEVEL, true);
            } else if (hasUnshownAchievements(mPlayerInfo)) {
                showAwardScreen(AwardType.AWARD_ACHIEVEMENTONLY, true);
            } else {
                preNewGame(mGameMode, false);
            }
        } else if (isSurvivalMode()) {
            if (mBoard.isFinalSurvivalStage()) {
                killBoard();

                if (aUnlockedNewChallenge && hasFinishedAdventure()) {
                    showAwardScreen(AwardType.AWARD_FORLEVEL, true);
                } else if (hasUnshownAchievements(mPlayerInfo)) {
                    showAwardScreen(AwardType.AWARD_ACHIEVEMENTONLY, true);
                } else {
                    showChallengeScreen(ChallengePage.CHALLENGE_PAGE_SURVIVAL);
                }
            } else {
                mBoard.mChallenge.mSurvivalStage++;
                killGameSelector();
                mBoard.initSurvivalStage();
            }
        } else if (isPuzzleMode()) {
            killBoard();

            if (aUnlockedNewChallenge) {
                showAwardScreen(AwardType.AWARD_FORLEVEL, true);
            } else if (hasUnshownAchievements(mPlayerInfo)) {
                showAwardScreen(AwardType.AWARD_ACHIEVEMENTONLY, true);
            } else {
                showChallengeScreen(ChallengePage.CHALLENGE_PAGE_PUZZLE);
            }
        } else {
            killBoard();

            if (aUnlockedNewChallenge && hasFinishedAdventure()) {
                showAwardScreen(AwardType.AWARD_FORLEVEL, true);
            } else if (hasUnshownAchievements(mPlayerInfo)) {
                showAwardScreen(AwardType.AWARD_ACHIEVEMENTONLY, true);
            } else {
                showChallengeScreen(ChallengePage.CHALLENGE_PAGE_CHALLENGE);
            }
        }
    }

    // LawnApp.cpp:86 (static bool HasUnshownAchievements)
    private static boolean hasUnshownAchievements(PlayerInfo thePlayerInfo) {
        if (thePlayerInfo == null) {
            return false;
        }

        for (int i = 0; i < AchievementId.MAX_ACHIEVEMENTS; i++) {
            if (thePlayerInfo.mEarnedAchievements[i] && !thePlayerInfo.mShownAchievements[i]) {
                return true;
            }
        }

        return false;
    }

    // LawnApp::AdvanceCrazyDaveText (LawnApp.cpp:2362)
    public boolean advanceCrazyDaveText() {
        String aMessageName = "[CRAZY_DAVE_" + (mCrazyDaveMessageIndex + 1) + "]";
        if (!PvzpStringFile.exists(aMessageName)) {
            return false;
        }

        crazyDaveTalkIndex(mCrazyDaveMessageIndex + 1);
        return true;
    }

    // LawnApp::GetCrazyDaveText (LawnApp.cpp:2374)
    public String getCrazyDaveText(int theMessageIndex) {
        String aMessage = "[" + "CRAZY_DAVE_" + theMessageIndex + "]";
        aMessage = PvzpCommon.pvzpReplaceString(aMessage, "{PLAYER_NAME}", mPlayerInfo.mName);
        aMessage = PvzpCommon.pvzpReplaceString(aMessage, "{MONEY}", getMoneyString(mPlayerInfo.mCoins));
        int aCost = StoreScreen.getItemCost(StoreItem.STORE_ITEM_PACKET_UPGRADE);
        aMessage = PvzpCommon.pvzpReplaceString(aMessage, "{UPGRADE_COST}", getMoneyString(aCost));
        return aMessage;
    }

    // LawnApp::CanShowAlmanac (LawnApp.cpp:2384)
    public boolean canShowAlmanac() {
        if (isIceDemo()) {
            return false;
        }

        if (mPlayerInfo == null) {
            return false;
        }

        return hasFinishedAdventure() || mPlayerInfo.mLevel >= 15;
    }

    // LawnApp::CanShowStore (LawnApp.cpp:2395)
    public boolean canShowStore() {
        if (isIceDemo()) {
            return false;
        }

        if (mPlayerInfo == null) {
            return false;
        }

        return hasFinishedAdventure() || mPlayerInfo.mHasSeenUpsell != 0 || mPlayerInfo.mLevel >= 25;
    }

    // LawnApp::CanShowZenGarden (LawnApp.cpp:2406)
    public boolean canShowZenGarden() {
        if (mPlayerInfo == null) {
            return false;
        }

        if (isTrialStageLocked()) {
            return false;
        }

        return hasFinishedAdventure() || mPlayerInfo.mLevel >= 45;
    }

    // LawnApp::CanSpawnYetis (LawnApp.cpp:2417)
    public boolean canSpawnYetis() {
        ZombieDefinition aZombieDef = Zombie.getZombieDefinition(ZombieType.ZOMBIE_YETI);
        return hasFinishedAdventure()
                && (mPlayerInfo.mFinishedAdventure >= 2 || mPlayerInfo.mLevel >= aZombieDef.mStartingLevel);
    }

    // LawnApp::HasBeatenChallenge (LawnApp.cpp:2423)
    public boolean hasBeatenChallenge(int theGameMode) {
        if (mPlayerInfo == null) {
            return false;
        }

        int aChallengeIndex = theGameMode - GameMode.GAMEMODE_SURVIVAL_NORMAL_STAGE_1;
        if (isSurvivalNormal(theGameMode)) {
            return mPlayerInfo.mChallengeRecords[aChallengeIndex] >= GameConstants.SURVIVAL_NORMAL_FLAGS;
        }
        if (isSurvivalHard(theGameMode)) {
            return mPlayerInfo.mChallengeRecords[aChallengeIndex] >= GameConstants.SURVIVAL_HARD_FLAGS;
        }
        if (isSurvivalEndless(theGameMode) || isEndlessScaryPotter(theGameMode) || isEndlessIZombie(theGameMode)) {
            return false;
        }
        return mPlayerInfo.mChallengeRecords[aChallengeIndex] > 0;
    }

    // LawnApp::HasFinishedAdventure (LawnApp.cpp:2445)
    public boolean hasFinishedAdventure() {
        return mPlayerInfo != null && mPlayerInfo.mFinishedAdventure > 0;
    }

    // LawnApp::IsFirstTimeAdventureMode (LawnApp.cpp:2450)
    public boolean isFirstTimeAdventureMode() {
        return isAdventureMode() && !hasFinishedAdventure();
    }

    // =========================================================================
    // Crazy Dave (LawnApp.cpp:2455-2941)
    // =========================================================================

    // LawnApp::CrazyDaveEnter (LawnApp.cpp:2455)
    public void crazyDaveEnter() {
        Reanimation aCrazyDaveReanim = addReanimation(0.0f, 0.0f, 0, ReanimationType.REANIM_CRAZY_DAVE);
        aCrazyDaveReanim.mIsAttachment = true;
        aCrazyDaveReanim.setBasePoseFromAnim("anim_idle_handing");
        mCrazyDaveReanimID = reanimationGetID(aCrazyDaveReanim);
        aCrazyDaveReanim.playReanim("anim_enter", ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 0, 24.0f);

        mCrazyDaveState = CrazyDaveState.CRAZY_DAVE_ENTERING;
        mCrazyDaveMessageIndex = -1;
        mCrazyDaveMessageText = "";
        mCrazyDaveBlinkCounter = PvzpCommon.randRangeInt(400, 800);

        if (mGameScene == GameScenes.SCENE_LEVEL_INTRO && isStormyNightLevel()) {
            aCrazyDaveReanim.mColorOverride = new Color(64, 64, 64);
        }
    }

    // LawnApp::CrazyDaveDie (LawnApp.cpp:2477)
    public void crazyDaveDie() {
        Reanimation aCrazyDaveReanim = reanimationTryToGet(mCrazyDaveReanimID);
        if (aCrazyDaveReanim != null) {
            aCrazyDaveReanim.reanimationDie();
        }

        mCrazyDaveState = CrazyDaveState.CRAZY_DAVE_OFF;
        mCrazyDaveReanimID = ReanimationID.REANIMATIONID_NULL;
        mCrazyDaveBlinkReanimID = ReanimationID.REANIMATIONID_NULL;
        mCrazyDaveMessageIndex = -1;
        mCrazyDaveMessageText = "";

        crazyDaveStopSound();
    }

    // LawnApp::CrazyDaveLeave (LawnApp.cpp:2494)
    public void crazyDaveLeave() {
        Reanimation aCrazyDaveReanim = reanimationTryToGet(mCrazyDaveReanimID);
        if (aCrazyDaveReanim != null) {
            if (mCrazyDaveState == CrazyDaveState.CRAZY_DAVE_HANDING_TALKING
                    || mCrazyDaveState == CrazyDaveState.CRAZY_DAVE_HANDING_IDLING) {
                crazyDaveDoneHanding();
            }

            aCrazyDaveReanim.playReanim("anim_leave", ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 20, 24.0f);
            aCrazyDaveReanim.setImageOverride("Dave_mouths", null);

            mCrazyDaveState = CrazyDaveState.CRAZY_DAVE_LEAVING;
            mCrazyDaveMessageIndex = -1;
            mCrazyDaveMessageText = "";

            crazyDaveStopSound();
        }
    }

    // LawnApp::CrazyDaveTalkIndex (LawnApp.cpp:2515)
    public void crazyDaveTalkIndex(int theMessageIndex) {
        mCrazyDaveMessageIndex = theMessageIndex;
        String aMessageText = getCrazyDaveText(theMessageIndex);
        crazyDaveTalkMessage(aMessageText);
    }

    // LawnApp::CrazyDaveDoneHanding (LawnApp.cpp:2522)
    public void crazyDaveDoneHanding() {
        Reanimation aCrazyDaveReanim = reanimationGet(mCrazyDaveReanimID);
        ReanimatorTrackInstance aHandTrackInstance = aCrazyDaveReanim.getTrackInstanceByName("Dave_handinghand");
        Attachment.attachmentDie(aHandTrackInstance.mAttachmentID);

        Common.logDebugLn("DoneHanding");
    }

    // LawnApp::CrazyDaveStopSound (LawnApp.cpp:2531)
    public void crazyDaveStopSound() {
        mSoundSystem.stopFoley(FoleyType.FOLEY_CRAZY_DAVE_SHORT);
        mSoundSystem.stopFoley(FoleyType.FOLEY_CRAZY_DAVE_LONG);
        mSoundSystem.stopFoley(FoleyType.FOLEY_CRAZY_DAVE_EXTRA_LONG);
        mSoundSystem.stopFoley(FoleyType.FOLEY_CRAZY_DAVE_CRAZY);
    }

    // LawnApp::CrazyDaveTalkMessage (LawnApp.cpp:2539)
    public void crazyDaveTalkMessage(String theMessage) {
        Reanimation aCrazyDaveReanim = reanimationGet(mCrazyDaveReanimID);

        boolean doHanding = theMessage.contains("{HANDING}");
        if ((mCrazyDaveState == CrazyDaveState.CRAZY_DAVE_HANDING_TALKING
                || mCrazyDaveState == CrazyDaveState.CRAZY_DAVE_HANDING_IDLING) && !doHanding) {
            crazyDaveDoneHanding();
        }

        boolean doSound = true;
        if (theMessage.contains("{NO_SOUND}")) {
            doSound = false;
        } else {
            crazyDaveStopSound();
        }

        int aWordsCount = 0;
        boolean isControlWord = false;
        for (int i = 0; i < theMessage.length(); i++) { // byte count tracks CJK talk duration better than code points
            char c = theMessage.charAt(i);
            if (c == '{') {
                isControlWord = true;
            } else if (c == '}') {
                isControlWord = false;
            } else if (!isControlWord) {
                aWordsCount++;
            }
        }

        aCrazyDaveReanim.setImageOverride("Dave_mouths", null);

        if (mCrazyDaveState != CrazyDaveState.CRAZY_DAVE_TALKING || doSound) {
            if (doHanding) {
                aCrazyDaveReanim.playReanim("anim_talk_handing", ReanimLoopType.REANIM_LOOP, 50, 12.0f);

                if (doSound) {
                    if (theMessage.contains("{SHORT_SOUND}")) {
                        playFoley(FoleyType.FOLEY_CRAZY_DAVE_SHORT);
                    } else if (theMessage.contains("{SCREAM}")) {
                        playFoley(FoleyType.FOLEY_CRAZY_DAVE_SCREAM);
                    } else {
                        playFoley(FoleyType.FOLEY_CRAZY_DAVE_LONG);
                    }
                }

                mCrazyDaveState = CrazyDaveState.CRAZY_DAVE_HANDING_TALKING;
            } else if (theMessage.contains("{SHAKE}")) {
                aCrazyDaveReanim.playReanim("anim_crazy", ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 50, 12.0f);

                if (doSound) {
                    playFoley(FoleyType.FOLEY_CRAZY_DAVE_CRAZY);
                }

                mCrazyDaveState = CrazyDaveState.CRAZY_DAVE_TALKING;
            } else if (theMessage.contains("{SCREAM}")) {
                aCrazyDaveReanim.playReanim("anim_smalltalk", ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 50, 12.0f);

                if (doSound) {
                    playFoley(FoleyType.FOLEY_CRAZY_DAVE_SCREAM);
                }

                mCrazyDaveState = CrazyDaveState.CRAZY_DAVE_TALKING;
            } else if (theMessage.contains("{SCREAM2}")) {
                aCrazyDaveReanim.playReanim("anim_mediumtalk", ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 50, 12.0f);

                if (doSound) {
                    playFoley(FoleyType.FOLEY_CRAZY_DAVE_SCREAM_2);
                }

                mCrazyDaveState = CrazyDaveState.CRAZY_DAVE_TALKING;
            } else if (theMessage.contains("{SHOW_WALLNUT}")) {
                aCrazyDaveReanim.playReanim("anim_talk_handing", ReanimLoopType.REANIM_LOOP, 50, 12.0f);

                Reanimation aWallnutReanim = addReanimation(0.0f, 0.0f, 0, ReanimationType.REANIM_WALLNUT);
                aWallnutReanim.playReanim("anim_idle", ReanimLoopType.REANIM_LOOP, 0, 12.0f);
                Common.logDebugLn("Handed");

                ReanimatorTrackInstance aHandTrackInstance = aCrazyDaveReanim.getTrackInstanceByName("Dave_handinghand");
                AttachEffect aAttachEffect = Attachment.attachReanim(aHandTrackInstance.mAttachmentID, aWallnutReanim, 100.0f, 393.0f);
                aAttachEffect.mOffset.m00 = 1.2f;
                aAttachEffect.mOffset.m11 = 1.2f;

                aCrazyDaveReanim.update();

                if (doSound) {
                    playFoley(FoleyType.FOLEY_CRAZY_DAVE_SCREAM_2);
                }

                mCrazyDaveState = CrazyDaveState.CRAZY_DAVE_HANDING_TALKING;
            } else if (theMessage.contains("{SHOW_HAMMER}")) {
                aCrazyDaveReanim.playReanim("anim_talk_handing", ReanimLoopType.REANIM_LOOP, 50, 12.0f);

                Reanimation aHammerReanim = addReanimation(0.0f, 0.0f, 0, ReanimationType.REANIM_HAMMER);
                aHammerReanim.playReanim("anim_whack_zombie", ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 0, 24.0f);
                aHammerReanim.mAnimTime = 1.0f;

                ReanimatorTrackInstance aHandTrackInstance = aCrazyDaveReanim.getTrackInstanceByName("Dave_handinghand");
                AttachEffect aAttachEffect = Attachment.attachReanim(aHandTrackInstance.mAttachmentID, aHammerReanim, 62.0f, 445.0f);
                aAttachEffect.mOffset.m00 = 1.5f;
                aAttachEffect.mOffset.m11 = 1.5f;

                aCrazyDaveReanim.update();

                if (doSound) {
                    playFoley(FoleyType.FOLEY_CRAZY_DAVE_LONG);
                }

                mCrazyDaveState = CrazyDaveState.CRAZY_DAVE_HANDING_TALKING;
            } else if (theMessage.contains("{SHOW_FERTILIZER}")) {
                aCrazyDaveReanim.playReanim("anim_talk_handing", ReanimLoopType.REANIM_LOOP, 50, 12.0f);

                Reanimation aFertilizerReanim = addReanimation(0.0f, 0.0f, 0, ReanimationType.REANIM_ZENGARDEN_FERTILIZER);
                aFertilizerReanim.playReanim("bag", ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 0, 24.0f);
                aFertilizerReanim.mAnimRate = 0.0f;

                ReanimatorTrackInstance aHandTrackInstance = aCrazyDaveReanim.getTrackInstanceByName("Dave_handinghand");
                Attachment.attachReanim(aHandTrackInstance.mAttachmentID, aFertilizerReanim, 102.0f, 412.0f);
                aCrazyDaveReanim.update();

                if (doSound) {
                    playFoley(FoleyType.FOLEY_CRAZY_DAVE_LONG);
                }

                mCrazyDaveState = CrazyDaveState.CRAZY_DAVE_HANDING_TALKING;
            } else if (theMessage.contains("{SHOW_TREE_FOOD}")) {
                aCrazyDaveReanim.playReanim("anim_talk_handing", ReanimLoopType.REANIM_LOOP, 50, 12.0f);

                Reanimation aTreeFoodReanim = addReanimation(0.0f, 0.0f, 0, ReanimationType.REANIM_TREEOFWISDOM_TREEFOOD);
                aTreeFoodReanim.playReanim("bag", ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 0, 24.0f);
                aTreeFoodReanim.mAnimRate = 0.0f;

                ReanimatorTrackInstance aHandTrackInstance = aCrazyDaveReanim.getTrackInstanceByName("Dave_handinghand");
                Attachment.attachReanim(aHandTrackInstance.mAttachmentID, aTreeFoodReanim, 102.0f, 412.0f);
                aCrazyDaveReanim.update();

                if (doSound) {
                    playFoley(FoleyType.FOLEY_CRAZY_DAVE_LONG);
                }

                mCrazyDaveState = CrazyDaveState.CRAZY_DAVE_HANDING_TALKING;
            } else if (theMessage.contains("{SHOW_MONEYBAG}")) {
                aCrazyDaveReanim.playReanim("anim_talk_handing", ReanimLoopType.REANIM_LOOP, 50, 12.0f);

                Reanimation aMoneyBagReanim = addReanimation(0.0f, 0.0f, 0, ReanimationType.REANIM_ZENGARDEN_FERTILIZER);
                aMoneyBagReanim.playReanim("bag", ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 0, 24.0f);
                aMoneyBagReanim.mAnimRate = 0.0f;
                aMoneyBagReanim.setImageOverride("bag", Resources.getImageById(Resources.IMAGE_MONEYBAG_ID));

                ReanimatorTrackInstance aHandTrackInstance = aCrazyDaveReanim.getTrackInstanceByName("Dave_handinghand");
                Attachment.attachReanim(aHandTrackInstance.mAttachmentID, aMoneyBagReanim, 90.0f, 405.0f);
                aCrazyDaveReanim.update();
                if (doSound) {
                    playFoley(FoleyType.FOLEY_CRAZY_DAVE_LONG);
                }

                mCrazyDaveState = CrazyDaveState.CRAZY_DAVE_HANDING_TALKING;
            } else {
                if (aWordsCount < 23) {
                    aCrazyDaveReanim.playReanim("anim_smalltalk", ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 50, 12.0f);

                    if (doSound) {
                        playFoley(FoleyType.FOLEY_CRAZY_DAVE_SHORT);
                    }

                    mCrazyDaveState = CrazyDaveState.CRAZY_DAVE_TALKING;
                } else if (aWordsCount < 52) {
                    aCrazyDaveReanim.playReanim("anim_mediumtalk", ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 50, 12.0f);

                    if (doSound) {
                        playFoley(FoleyType.FOLEY_CRAZY_DAVE_LONG);
                    }

                    mCrazyDaveState = CrazyDaveState.CRAZY_DAVE_TALKING;
                } else {
                    aCrazyDaveReanim.playReanim("anim_blahblah", ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 50, 12.0f);

                    if (doSound) {
                        playFoley(FoleyType.FOLEY_CRAZY_DAVE_EXTRA_LONG);
                    }

                    mCrazyDaveState = CrazyDaveState.CRAZY_DAVE_TALKING;
                }
            }
        }

        mCrazyDaveMessageText = theMessage;
    }

    // LawnApp::CrazyDaveStopTalking (LawnApp.cpp:2782)
    public void crazyDaveStopTalking() {
        boolean aDoneHanding = true;
        if (mGameMode == GameMode.GAMEMODE_UPSELL) {
            aDoneHanding = false;
        }
        if (aDoneHanding && mCrazyDaveState == CrazyDaveState.CRAZY_DAVE_HANDING_TALKING) {
            crazyDaveDoneHanding();
        }

        Reanimation aCrazyDaveReanim = reanimationGet(mCrazyDaveReanimID);
        aCrazyDaveReanim.setImageOverride("Dave_mouths", null);
        if (mCrazyDaveState == CrazyDaveState.CRAZY_DAVE_HANDING_TALKING && !aDoneHanding) {
            aCrazyDaveReanim.playReanim("anim_idle_handing", ReanimLoopType.REANIM_LOOP, 20, 12.0f);
            mCrazyDaveState = CrazyDaveState.CRAZY_DAVE_HANDING_IDLING;
        } else if (mCrazyDaveState == CrazyDaveState.CRAZY_DAVE_TALKING
                || mCrazyDaveState == CrazyDaveState.CRAZY_DAVE_HANDING_TALKING) {
            aCrazyDaveReanim.playReanim("anim_idle", ReanimLoopType.REANIM_LOOP, 20, 12.0f);
            mCrazyDaveState = CrazyDaveState.CRAZY_DAVE_IDLING;
        }

        mCrazyDaveMessageIndex = -1;
        mCrazyDaveMessageText = "";
        crazyDaveStopSound();
    }

    // LawnApp::UpdateCrazyDave (LawnApp.cpp:2812)
    public void updateCrazyDave() {
        Reanimation aCrazyDaveReanim = reanimationTryToGet(mCrazyDaveReanimID);
        if (aCrazyDaveReanim == null) {
            return;
        }

        if (mCrazyDaveState == CrazyDaveState.CRAZY_DAVE_ENTERING
                || mCrazyDaveState == CrazyDaveState.CRAZY_DAVE_TALKING) {
            if (aCrazyDaveReanim.mLoopCount > 0) {
                aCrazyDaveReanim.playReanim("anim_idle", ReanimLoopType.REANIM_LOOP, 20, 12.0f);
                mCrazyDaveState = CrazyDaveState.CRAZY_DAVE_IDLING;
            }
        } else if (mCrazyDaveState == CrazyDaveState.CRAZY_DAVE_HANDING_TALKING) {
            if (aCrazyDaveReanim.mLoopCount > 0) {
                aCrazyDaveReanim.playReanim("anim_idle_handing", ReanimLoopType.REANIM_LOOP, 20, 12.0f);
                mCrazyDaveState = CrazyDaveState.CRAZY_DAVE_HANDING_IDLING;
            }
        } else if (mCrazyDaveState == CrazyDaveState.CRAZY_DAVE_LEAVING && aCrazyDaveReanim.mLoopCount > 0) {
            crazyDaveDie();
        }

        if (mCrazyDaveState == CrazyDaveState.CRAZY_DAVE_IDLING
                || mCrazyDaveState == CrazyDaveState.CRAZY_DAVE_HANDING_IDLING) {
            if (mCrazyDaveMessageText.contains("{MOUTH_BIG_SMILE}")) {
                aCrazyDaveReanim.setImageOverride("Dave_mouths", Resources.getImageById(Resources.IMAGE_REANIM_CRAZYDAVE_MOUTH1_ID));
            } else if (mCrazyDaveMessageText.contains("{MOUTH_SMALL_SMILE}")) {
                aCrazyDaveReanim.setImageOverride("Dave_mouths", Resources.getImageById(Resources.IMAGE_REANIM_CRAZYDAVE_MOUTH5_ID));
            } else if (mCrazyDaveMessageText.contains("{MOUTH_BIG_OH}")) {
                aCrazyDaveReanim.setImageOverride("Dave_mouths", Resources.getImageById(Resources.IMAGE_REANIM_CRAZYDAVE_MOUTH4_ID));
            } else if (mCrazyDaveMessageText.contains("{MOUTH_SMALL_OH}")) {
                aCrazyDaveReanim.setImageOverride("Dave_mouths", Resources.getImageById(Resources.IMAGE_REANIM_CRAZYDAVE_MOUTH6_ID));
            }
        }

        if (mCrazyDaveState == CrazyDaveState.CRAZY_DAVE_IDLING
                || mCrazyDaveState == CrazyDaveState.CRAZY_DAVE_TALKING
                || mCrazyDaveState == CrazyDaveState.CRAZY_DAVE_HANDING_TALKING
                || mCrazyDaveState == CrazyDaveState.CRAZY_DAVE_HANDING_IDLING) {
            mCrazyDaveBlinkCounter--;
            if (mCrazyDaveBlinkCounter <= 0) {
                mCrazyDaveBlinkCounter = PvzpCommon.randRangeInt(400, 800);
                Reanimation aBlinkReanim = addReanimation(0.0f, 0.0f, 0, ReanimationType.REANIM_CRAZY_DAVE);
                aBlinkReanim.setFramesForLayer("anim_blink");
                aBlinkReanim.mLoopType = ReanimLoopType.REANIM_PLAY_ONCE_FULL_LAST_FRAME_AND_HOLD;
                aBlinkReanim.mAnimRate = 15.0f;
                aBlinkReanim.attachToAnotherReanimation(aCrazyDaveReanim, "Dave_head");
                aBlinkReanim.mColorOverride = aCrazyDaveReanim.mColorOverride;
                aCrazyDaveReanim.assignRenderGroupToTrack("Dave_eye", Reanimation.RENDER_GROUP_HIDDEN);
                mCrazyDaveBlinkReanimID = reanimationGetID(aBlinkReanim);
            }
        }

        Reanimation aBlinkReanim = reanimationTryToGet(mCrazyDaveBlinkReanimID);
        if (aBlinkReanim != null && aBlinkReanim.mLoopCount > 0) {
            aCrazyDaveReanim.assignRenderGroupToTrack("Dave_eye", Reanimation.RENDER_GROUP_NORMAL);
            removeReanimation(mCrazyDaveBlinkReanimID);
            mCrazyDaveBlinkReanimID = ReanimationID.REANIMATIONID_NULL;
        }

        aCrazyDaveReanim.update();
    }

    // LawnApp::DrawCrazyDave (LawnApp.cpp:2888)
    public void drawCrazyDave(Graphics g) {
        Reanimation aCrazyDaveReanim = reanimationTryToGet(mCrazyDaveReanimID);
        if (aCrazyDaveReanim == null) {
            return;
        }

        if (!mCrazyDaveMessageText.isEmpty()) {
            Image aBubbleImage = Resources.getImageById(Resources.IMAGE_STORE_SPEECHBUBBLE2_ID);
            int aPosX = 285;
            int aPosY = 20;
            if (getDialog(Dialogs.DIALOG_STORE) != null) {
                aBubbleImage = Resources.getImageById(Resources.IMAGE_STORE_SPEECHBUBBLE_ID);
                aPosX -= 180;
                aPosY -= 78;
            } else if (mGameMode == GameMode.GAMEMODE_UPSELL) {
                aPosX += 130;
                aPosY += 70;
            }
            g.drawImage(aBubbleImage, aPosX, aPosY);

            String aBubbleText = mCrazyDaveMessageText;
            Rect aRect = new Rect(aPosX + 25, aPosY + 6, 233, 144);
            if (aBubbleText.contains("{SHAKE}")) {
                aBubbleText = PvzpCommon.pvzpReplaceString(aBubbleText, "{SHAKE}", "");
                // PORT-NOTE: upstream shakes the bubble with libc rand() % 2 (LawnApp.cpp:2917-2918).
                // That stream is not MTRand: it is neither silenced by this draw pass'
                // MTAutoDisallowRand guard (SexyAppBase.cpp:1778) nor part of the seeded gameplay
                // sequence, so Sexy::Rand (Common.rand) both asserts here and would desync the MT
                // stream. See Common.libcRand.
                aRect.mX += Common.libcRand(2);
                aRect.mY += Common.libcRand(2);
            }

            boolean clickToContinue = true;
            if (mGameMode == GameMode.GAMEMODE_UPSELL) {
                clickToContinue = false;
            } else if (aBubbleText.contains("{NO_CLICK}")) {
                aBubbleText = PvzpCommon.pvzpReplaceString(aBubbleText, "{NO_CLICK}", "");
                clickToContinue = false;
            }

            int aWrapEnum = getInteger("CRAZY_DAVE_MESSAGE_TEXT_WRAP_ENUM", DrawStringJustification.DS_ALIGN_CENTER_VERTICAL_MIDDLE);
            PvzpStringFile.pvzpDrawStringWrapped(g, aBubbleText, aRect, Resources.getFontById(Resources.FONT_BRIANNETOD16_ID),
                    Color.Black, aWrapEnum);
            if (clickToContinue) {
                PvzpCommon.pvzpDrawString(g, getString("CLICK_TO_CONTINUE", "click to continue"), aPosX + 139,
                        aPosY + 140, Resources.getFontById(Resources.FONT_PICO129_ID), Color.Black,
                        DrawStringJustification.DS_ALIGN_CENTER);
            }
        }

        aCrazyDaveReanim.draw(g);
    }

    // =========================================================================
    // Preloading (LawnApp.cpp:2943-3064)
    // =========================================================================

    // LawnApp::GetNumPreloadingTasks (LawnApp.cpp:2943)
    public int getNumPreloadingTasks() {
        int aTaskCount = 10;
        if (mPlayerInfo != null) {
            // The plant loop walks the seed-type id space, not just the retail seed types: a seed
            // type with a definition row is a plant that may have to be preloaded, whether it is one
            // of the retail ones or one a definition was written for. An empty row is no plant, and
            // skipping it before asking HasSeedType is what keeps this loop's count equal to the
            // number of plants PreloadForUser actually loads.
            for (int i = SeedType.SEED_PEASHOOTER; i < SeedType.MAX_SEED_TYPES; i++) {
                if (Plant.gPlantDefs[i] == null) {
                    continue;
                }
                if (hasSeedType(i) || hasFinishedAdventure()) {
                    aTaskCount++;
                }
            }

            for (int i = ZombieType.ZOMBIE_NORMAL; i < ZombieType.MAX_ZOMBIE_TYPES; i++) {
                ZombieDefinition aZombieDef = Zombie.getZombieDefinition(i);
                if (aZombieDef == null) {
                    continue;
                }
                if (hasFinishedAdventure() || mPlayerInfo.mLevel >= aZombieDef.mStartingLevel) {
                    if (i != ZombieType.ZOMBIE_BOSS
                            && i != ZombieType.ZOMBIE_CATAPULT
                            && i != ZombieType.ZOMBIE_GARGANTUAR
                            && i != ZombieType.ZOMBIE_DIGGER
                            && i != ZombieType.ZOMBIE_ZAMBONI) {
                        aTaskCount++;
                    }
                }
            }
        }
        return aTaskCount * 68;
    }

    // LawnApp::PreloadForUser (LawnApp.cpp:2974)
    public void preloadForUser() {
        int aNumTasks = mCompletedLoadingThreadTasks.get() + getNumPreloadingTasks();
        if (mTitleScreen != null && mTitleScreen.mQuickLoadKey != KeyCode.KEYCODE_UNKNOWN) {
            Common.logDebugLn("preload canceled");
            mNumLoadingThreadTasks.set(aNumTasks);
            return;
        }

        Reanimator.reanimatorEnsureDefinitionLoaded(ReanimationType.REANIM_PUFF, true);
        Reanimator.reanimatorEnsureDefinitionLoaded(ReanimationType.REANIM_LAWN_MOWERED_ZOMBIE, true);
        Reanimator.reanimatorEnsureDefinitionLoaded(ReanimationType.REANIM_READYSETPLANT, true);
        mCompletedLoadingThreadTasks.addAndGet(68);
        Reanimator.reanimatorEnsureDefinitionLoaded(ReanimationType.REANIM_FINAL_WAVE, true);
        Reanimator.reanimatorEnsureDefinitionLoaded(ReanimationType.REANIM_SUN, true);
        Reanimator.reanimatorEnsureDefinitionLoaded(ReanimationType.REANIM_TEXT_FADE_ON, true);
        mCompletedLoadingThreadTasks.addAndGet(68);
        Reanimator.reanimatorEnsureDefinitionLoaded(ReanimationType.REANIM_ZOMBIE, true);
        mCompletedLoadingThreadTasks.addAndGet(68);
        Reanimator.reanimatorEnsureDefinitionLoaded(ReanimationType.REANIM_ZOMBIE_NEWSPAPER, true);
        mCompletedLoadingThreadTasks.addAndGet(68);
        Reanimator.reanimatorEnsureDefinitionLoaded(ReanimationType.REANIM_SELECTOR_SCREEN, true);
        mCompletedLoadingThreadTasks.addAndGet(340);
        Reanimator.reanimatorEnsureDefinitionLoaded(ReanimationType.REANIM_ZOMBIE_HAND, true);
        mCompletedLoadingThreadTasks.addAndGet(68);

        if (mPlayerInfo != null) {
            for (int i = SeedType.SEED_PEASHOOTER; i < SeedType.MAX_SEED_TYPES; i++) {
                if (Plant.gPlantDefs[i] == null) {
                    continue;
                }
                if (hasSeedType(i) || hasFinishedAdventure()) {
                    Plant.preloadPlantResources(i);
                    if (mCompletedLoadingThreadTasks.get() < aNumTasks) {
                        mCompletedLoadingThreadTasks.addAndGet(68);
                    }

                    if (mTitleScreen != null && mTitleScreen.mQuickLoadKey != KeyCode.KEYCODE_UNKNOWN) {
                        Common.logDebugLn("preload canceled");
                        mNumLoadingThreadTasks.set(aNumTasks);
                        return;
                    }

                    if (mShutdown.get() || mCloseRequest) {
                        return;
                    }
                }
            }

            for (int i = ZombieType.ZOMBIE_NORMAL; i < ZombieType.MAX_ZOMBIE_TYPES; i++) {
                ZombieDefinition aZombieDef = Zombie.getZombieDefinition(i);
                if (aZombieDef == null) {
                    continue;
                }
                if (!hasFinishedAdventure() && mPlayerInfo.mLevel < aZombieDef.mStartingLevel) {
                    continue;
                }
                if (i == ZombieType.ZOMBIE_BOSS || i == ZombieType.ZOMBIE_CATAPULT
                        || i == ZombieType.ZOMBIE_GARGANTUAR || i == ZombieType.ZOMBIE_DIGGER
                        || i == ZombieType.ZOMBIE_ZAMBONI) {
                    continue;
                }

                Zombie.preloadZombieResources(i);
                if (mCompletedLoadingThreadTasks.get() < aNumTasks) {
                    mCompletedLoadingThreadTasks.addAndGet(68);
                }

                if (mTitleScreen != null && mTitleScreen.mQuickLoadKey != KeyCode.KEYCODE_UNKNOWN) {
                    Common.logDebugLn("preload canceled");
                    mNumLoadingThreadTasks.set(aNumTasks);
                    return;
                }

                if (mShutdown.get() || mCloseRequest) {
                    return;
                }
            }
        }

        if (mCompletedLoadingThreadTasks.get() != aNumTasks) {
            Common.logDebugLn("num preload tasks wasn't calculated correctly");
            mCompletedLoadingThreadTasks.set(aNumTasks);
        }
    }

    // =========================================================================
    // Stats / misc (LawnApp.cpp:3066-3268)
    // =========================================================================

    // LawnApp::Pluralize (LawnApp.cpp:3066)
    public static String pluralize(int theCount, String theSingular, String thePlural) {
        if (theCount == 1) {
            return PvzpCommon.pvzpReplaceNumberString(theSingular, "{COUNT}", theCount);
        }

        return PvzpCommon.pvzpReplaceNumberString(thePlural, "{COUNT}", theCount);
    }

    // LawnApp::GetNumTrophies (LawnApp.cpp:3076)
    public int getNumTrophies(int thePage) {
        int aNumTrophies = 0;

        for (int i = 0; i < GameMode.NUM_GAME_MODES - 1; i++) {
            ChallengeDefinition aDef = ChallengeScreen.getChallengeDefinition(i);
            // Port addition: the limbo showcase entries (zen garden / tree of wisdom / upsell /
            // intro) are not winnable challenges — never count them (ChallengeScreen.isShowcaseEntry).
            if (aDef.mPage == thePage && !ChallengeScreen.isShowcaseEntry(aDef.mChallengeMode)
                    && hasBeatenChallenge(aDef.mChallengeMode)) {
                aNumTrophies++;
            }
        }

        return aNumTrophies;
    }

    // LawnApp::TrophiesNeedForGoldSunflower (LawnApp.cpp:3092)
    public int trophiesNeedForGoldSunflower() {
        return 48 - getNumTrophies(ChallengePage.CHALLENGE_PAGE_SURVIVAL)
                - getNumTrophies(ChallengePage.CHALLENGE_PAGE_CHALLENGE)
                - getNumTrophies(ChallengePage.CHALLENGE_PAGE_PUZZLE);
    }

    // LawnApp::EarnedGoldTrophy (LawnApp.cpp:3097)
    public boolean earnedGoldTrophy() {
        return hasFinishedAdventure() && trophiesNeedForGoldSunflower() <= 0;
    }

    // LawnApp::FinishZenGardenToturial (LawnApp.cpp:3102)
    public void finishZenGardenToturial() {
        mBoardResult = BoardResult.BOARDRESULT_WON;
        killBoard();
        preNewGame(GameMode.GAMEMODE_ADVENTURE, false);
    }

    // LawnApp::IsTrialStageLocked (LawnApp.cpp:3109)
    public boolean isTrialStageLocked() {
        if (mDebugTrialLocked) {
            return true;
        }

        // PORT-NOTE (deviation): the unlock flag from the trial save ([TRIAL_UNLOCKED]) lifts the
        // trial locks for good
        if (mTrialUnlocked) {
            return false;
        }

        return mTrialType == TrialType.TRIALTYPE_STAGELOCKED;
    }

    /**
     * PORT-NOTE (deviation): the trial's "unlock full version" click with no backend — the unlock
     * flag persists in the isolated trial save's registry (savedata_trial/registry.regemu, written
     * immediately), so the next launch with this save starts as the full version. The seed
     * chooser / almanac / level-cap trial locks lift for the rest of the session.
     */
    public void unlockFullVersion() {
        if (mTrialUnlocked) {
            return;
        }
        mTrialUnlocked = true;
        mTrialType = TrialType.TRIALTYPE_NONE;
        registryWriteBoolean("TrialUnlocked", true);
        // PORT-NOTE (deviation): the confirmation is a LawnDialog ([TRIAL_UNLOCKED_TITLE] /
        // [TRIAL_UNLOCKED]); DIALOG_INFO keeps it clear of the answering DIALOG_MESSAGE
        lawnMessageBox(Dialogs.DIALOG_INFO, "[TRIAL_UNLOCKED_TITLE]", "[TRIAL_UNLOCKED]",
                "[DIALOG_BUTTON_OK]", "", Dialog.BUTTONS_FOOTER);
    }

    // LawnApp::GetMoneyString (LawnApp.cpp:3122)
    public String getMoneyString(int theAmount) {
        int aValue = theAmount * 10;
        if (aValue > 999999) {
            return String.format("$%,d,%03d,%03d", aValue / 1000000, (aValue - aValue / 1000000 * 1000000) / 1000,
                    aValue - aValue / 1000 * 1000);
        } else if (aValue > 9999) {
            return String.format("$%,d,%03d", aValue / 1000, aValue - aValue / 1000 * 1000);
        } else {
            return String.format("$%,d", aValue);
        }
    }

    // LawnApp::CanDoPinataMode (LawnApp.cpp:3182)
    public boolean canDoPinataMode() {
        if (mPlayerInfo == null) {
            return false;
        }

        return mPlayerInfo.mChallengeRecords[GameMode.GAMEMODE_TREE_OF_WISDOM - GameMode.GAMEMODE_SURVIVAL_NORMAL_STAGE_1] >= 1000;
    }

    // LawnApp::CanDoDanceMode (LawnApp.cpp:3190)
    public boolean canDoDanceMode() {
        if (mPlayerInfo == null) {
            return false;
        }

        return mPlayerInfo.mChallengeRecords[GameMode.GAMEMODE_TREE_OF_WISDOM - GameMode.GAMEMODE_SURVIVAL_NORMAL_STAGE_1] >= 500;
    }

    // LawnApp::CanDoDaisyMode (LawnApp.cpp:3198)
    public boolean canDoDaisyMode() {
        if (mPlayerInfo == null) {
            return false;
        }

        return mPlayerInfo.mChallengeRecords[GameMode.GAMEMODE_TREE_OF_WISDOM - GameMode.GAMEMODE_SURVIVAL_NORMAL_STAGE_1] >= 100;
    }

    // LawnApp::DoHighScoreDialog (LawnApp.cpp:3225)
    public void doHighScoreDialog() {
    }

    // LawnApp::DoRegister (LawnApp.cpp:3230)
    public void doRegister() {
    }

    // LawnApp::DoRegisterError (LawnApp.cpp:3235)
    public void doRegisterError() {
    }

    // LawnApp::CanDoRegisterDialog (LawnApp.cpp:3240)
    public boolean canDoRegisterDialog() {
        return false;
    }

    // LawnApp::DoNeedRegisterDialog (LawnApp.cpp:3245)
    public void doNeedRegisterDialog() {
    }

    // LawnApp::NeedRegister (LawnApp.cpp:3260)
    public boolean needRegister() {
        return false;
    }

    // LawnApp::UpdateRegisterInfo (LawnApp.cpp:3265)
    public void updateRegisterInfo() {
    }

    // LawnApp::ConfirmCheckForUpdates / CheckForUpdates (LawnApp.h:137-138) — empty upstream.
    public void confirmCheckForUpdates() {
    }

    public void checkForUpdates() {
    }

    // LawnApp.h helpers that are compile-time constants upstream.
    public boolean isRegistered() {
        return false;
    }

    public boolean isExpired() {
        return false;
    }

    public boolean isDRMConnected() {
        return false;
    }

    public String getCurrentLevelName() {
        return "Unknown";
    }

    // =========================================================================
    // Free helpers (LawnApp.cpp:100-112, 3195-3204)
    // =========================================================================

    // LawnGetCurrentLevelName (LawnApp.cpp:3195)
    public static String lawnGetCurrentLevelName() {
        if (gLawnApp == null) {
            return "Before App";
        }
        if (gLawnApp.mGameScene == GameScenes.SCENE_LOADING) {
            return "Game Loading";
        }
        if (gLawnApp.mGameScene == GameScenes.SCENE_MENU) {
            return "Game Selector";
        }
        if (gLawnApp.mGameScene == GameScenes.SCENE_AWARD) {
            return "Award Screen";
        }
        if (gLawnApp.mGameScene == GameScenes.SCENE_CHALLENGE) {
            return "Challenge Screen";
        }
        if (gLawnApp.mGameScene == GameScenes.SCENE_CREDIT) {
            return "Credits";
        }
        if (gLawnApp.mBoard == null) {
            return "Not Playing";
        }

        if (gLawnApp.isFirstTimeAdventureMode()) {
            return getStageString(gLawnApp.mBoard.mLevel);
        }
        if (gLawnApp.isAdventureMode()) {
            return "F" + getStageString(gLawnApp.mBoard.mLevel);
        }

        return gLawnApp.getCurrentChallengeDef().mChallengeName;
    }

    // LawnGetCloseRequest (LawnApp.cpp:100)
    public static boolean lawnGetCloseRequest() {
        if (gLawnApp == null) {
            return false;
        }

        return gLawnApp.mCloseRequest;
    }

    // LawnHasUsedCheatKeys (LawnApp.cpp:107)
    public static boolean lawnHasUsedCheatKeys() {
        return gLawnApp != null && gLawnApp.mPlayerInfo != null && gLawnApp.mPlayerInfo.mHasUsedCheatKeys != 0;
    }

    // =========================================================================
    // Internal helpers
    // =========================================================================

    private PvzpResourceManager pvzpResources() {
        return (PvzpResourceManager) mResourceManager;
    }

    // =========================================================================
    // Resource / achievement convenience (used pervasively by the lawn entities)
    // =========================================================================

    /**
     * Ported from the {@code IMAGE_*} resource globals (Resources.h): resolves a
     * {@link Resources} image id (or an {@link ImageID} id) to the loaded image.
     */
    public Image getImage(int theId) {
        return Resources.getImageById(theId);
    }

    /** Ported from the {@code FONT_*} resource globals (Resources.h). */
    public Font getFont(int theId) {
        return Resources.getFontById(theId);
    }

    // ReportAchievement::GiveAchievement (Lawn/Widget/AchievementsScreen.cpp)
    public void giveAchievement(int theAchievement, boolean theForceGive) {
        ReportAchievement.giveAchievement(this, theAchievement, theForceGive);
    }

    /**
     * PORT-NOTE: alias for {@link #reanimationGetID} used by entities already written against the
     * mistyped name ({@code LawnMower.java}/{@code GridItem.java}); it forwards to the upstream-named
     * method so there is a single implementation.
     */
    public int reanimationGetId(Reanimation theReanimation) {
        return reanimationGetID(theReanimation);
    }
}
