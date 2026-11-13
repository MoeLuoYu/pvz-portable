/*
 * Headless tests for the fifteen hidden cheat words new to this port
 * (cheatplant/lazyclick/wingame/killzom/killplant/killall/cherryboom/fast/love/froze/
 * retrack/dotrack/fillhoe/final/nextlevel), matched by the same TypingCheck machinery as the
 * upstream words and dispatched from {@code BoardInput.doHiddenCheatCheck}.
 *
 * The fixture mirrors BoardTest's: a headless LawnApp whose reanimations look loaded, plus a real
 * level-1 board, so the cheat effects run against real Board/Zombie/Plant objects.
 */
package cn.enaium.fabricpvz.portable.lawn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import cn.enaium.fabricpvz.portable.lawn.system.TypingCheck;
import cn.enaium.fabricpvz.portable.lawn.widget.CheatDialog;
import cn.enaium.fabricpvz.portable.pvzp.TestAssets;
import cn.enaium.fabricpvz.portable.sexy.Common;
import cn.enaium.fabricpvz.portable.sexy.KeyCodes;
import cn.enaium.fabricpvz.portable.test.HeadlessTestApp;

class HiddenCheatCodesTest {

    /** A headless adventure app whose reanimations look loaded (BoardTest's LoadedReanimApp). */
    private static final class LoadedReanimApp extends LawnApp {
        int mPreNewGameCount;

        @Override
        public void preNewGame(int theGameMode, boolean theLookForSavedGame) {
            mPreNewGameCount++; // the real restart would build a level; the test only records it
        }

        @Override
        public cn.enaium.fabricpvz.portable.pvzp.reanim.Reanimation addReanimation(float theX, float theY,
                int theRenderOrder, int theReanimationType) {
            cn.enaium.fabricpvz.portable.pvzp.reanim.Reanimation aReanim =
                    super.addReanimation(theX, theY, theRenderOrder, theReanimationType);
            HeadlessTestApp.stubLoadedTracks(aReanim);
            return aReanim;
        }
    }

    private static LawnApp newAdventureApp() {
        LawnApp app = new LoadedReanimApp();
        LawnApp.gLawnApp = app;
        app.mGameMode = GameMode.GAMEMODE_ADVENTURE;
        app.mPlayerInfo = app.mProfileMgr.addProfile("HiddenCheatCodesTest");
        app.mPlayerInfo.mLevel = 1;
        app.mPlayerInfo.mFinishedAdventure = 0;
        app.mAppRandSeed = 0x5EED;
        HeadlessTestApp.initialize(app);
        app.initTypingChecks();
        return app;
    }

    private static Board newPlayingBoard() {
        Common.sRand(0x5EED);
        Board board = new Board(newAdventureApp());
        board.initLevel();
        board.mApp.mGameScene = GameScenes.SCENE_PLAYING;
        return board;
    }

    /** Types a word into the dispatcher the same way the key handler does: one KeyCode per char. */
    private static void typeWord(Board board, String theWord) {
        for (int i = 0; i < theWord.length(); i++) {
            BoardInput.doTypingCheck(board, KeyCodes.getKeyCodeFromName(theWord.substring(i, i + 1)));
        }
    }

    // -------------------------------------------------------------------------------------
    // the TypingCheck phrases themselves
    // -------------------------------------------------------------------------------------

    @Test
    void everyHiddenWordMatchesItsOwnPhrase() {
        LawnApp app = newAdventureApp();
        String[] words = {"cheatplant", "lazyclick", "wingame", "killzom", "killplant", "killall",
                "cherryboom", "fast", "love", "froze"};
        TypingCheck[] checks = {app.mCheatPlantCheck, app.mLazyClickCheck, app.mWinGameCheck,
                app.mKillZomCheck, app.mKillPlantCheck, app.mKillAllCheck, app.mCherryBoomCheck,
                app.mFastCheck, app.mLoveCheck, app.mFrozeCheck, app.mReTrackCheck,
                app.mDoTrackCheck, app.mFillHoeCheck, app.mFinalCheck, app.mNextLevelCheck};

        for (int i = 0; i < words.length; i++) {
            TypingCheck aCheck = checks[i];
            // a full match fires once and clears the buffer
            for (int j = 0; j < words[i].length() - 1; j++) {
                assertFalse(aCheck.check(KeyCodes.getKeyCodeFromName(words[i].substring(j, j + 1))),
                        "prefix of " + words[i] + " must not fire");
            }
            assertTrue(aCheck.check(
                    KeyCodes.getKeyCodeFromName(words[i].substring(words[i].length() - 1))),
                    words[i] + " must fire on its last char");
            assertFalse(aCheck.check(), "a fired check must have cleared its buffer");
        }
    }

    @Test
    void noCheatWordIsASuffixOfAnotherCheatWord() {
        // a phrase that ends with another phrase would fire the wrong cheat when the longer word is
        // typed (the buffer only holds the phrase length), so the word list must be suffix-free.
        String[] words = {"mustache", "moustache", "trickedout", "tricked out", "future", "pinata",
                "dance", "daisies", "sukhbir", "cheatplant", "lazyclick", "wingame", "killzom",
                "killplant", "killall", "cherryboom", "fast", "love", "froze", "retrack",
                "dotrack", "fillhoe", "final", "nextlevel"};
        for (int i = 0; i < words.length; i++) {
            for (int j = 0; j < words.length; j++) {
                if (i == j || words[j].length() >= words[i].length()) {
                    continue;
                }
                assertNotEquals(words[j], words[i].substring(words[i].length() - words[j].length()),
                        words[j] + " is a suffix of " + words[i]);
            }
        }
    }

    // -------------------------------------------------------------------------------------
    // the board effects
    // -------------------------------------------------------------------------------------

    @Test
    void cheatPlantTogglesFreePlanting() {
        Board board = newPlayingBoard();

        typeWord(board, "cheatplant");
        assertTrue(board.mApp.mEasyPlantingCheat);
        typeWord(board, "cheatplant");
        assertFalse(board.mApp.mEasyPlantingCheat);
    }

    @Test
    void lazyClickTogglesAutoCollectAndCollectsSunOnTheField() {
        Board board = newPlayingBoard();
        Coin aSun = board.addCoin(200, 200, CoinType.COIN_SUN, CoinMotion.COIN_MOTION_FROM_SKY);
        assertFalse(aSun.mIsBeingCollected);

        typeWord(board, "lazyclick");
        assertTrue(board.mApp.mAutoCollectCheat);
        // every sun already on the field is collected at once
        assertTrue(aSun.mIsBeingCollected);
    }

    @Test
    void lazyClickAutoCollectsNewlySpawnedSun() {
        Board board = newPlayingBoard();
        typeWord(board, "lazyclick");

        Coin aSun = board.addCoin(200, 200, CoinType.COIN_SUN, CoinMotion.COIN_MOTION_FROM_SKY);
        aSun.update();
        assertTrue(aSun.mIsBeingCollected);

        typeWord(board, "lazyclick");
        assertFalse(board.mApp.mAutoCollectCheat);
    }

    @Test
    void winGameGrantsTheLevelVictoryLoot() {
        Board board = newPlayingBoard();
        assertFalse(board.mLevelAwardSpawned);

        typeWord(board, "wingame");
        assertEquals(BoardResult.BOARDRESULT_WON, board.mApp.mBoardResult);
        assertTrue(board.mLevelAwardSpawned, "the level award must be marked as dropped");
        // a level-1 first play-through award is the final seed packet, spawned and auto-collected
        boolean aAwardCollected = false;
        for (Coin aCoin : board.mCoins) {
            if (aCoin.isLevelAward() && aCoin.mIsBeingCollected) {
                aAwardCollected = true;
            }
        }
        assertTrue(aAwardCollected, "the award coin must be on the field and collected");
    }

    @Test
    void wingameOnSurvivalAdvancesTheStageWithoutAnAwardCoin() {
        LawnApp app = newAdventureApp();
        app.mGameMode = GameMode.GAMEMODE_SURVIVAL_NORMAL_STAGE_1;
        Common.sRand(0x5EED);
        Board board = new Board(app);
        board.initLevel();
        app.mGameScene = GameScenes.SCENE_PLAYING;
        assertTrue(board.isSurvivalStageWithRepick(), "an early survival stage repicks");
        board.mCurrentWave = 2;
        board.mHugeWaveCountDown = 750; // a flag wave is being announced

        typeWord(board, "wingame");
        assertEquals(BoardResult.BOARDRESULT_WON, board.mApp.mBoardResult);
        // the board is in the exact natural stage-end state: waves spent, flag prep cancelled,
        // award marked dropped, the stage-repick countdown running and no collectible awarded
        assertEquals(board.mNumWaves, board.mCurrentWave);
        assertEquals(0, board.mHugeWaveCountDown);
        assertTrue(board.mLevelAwardSpawned);
        assertEquals(500, board.mNextSurvivalStageCounter);
        for (Coin aCoin : board.mCoins) {
            assertTrue(aCoin.mDead || !aCoin.isLevelAward(), "survival stages grant no award coin");
        }
    }

    @Test
    void nextLevelWinsTheLevel() {
        Board board = newPlayingBoard();

        typeWord(board, "nextlevel");
        assertEquals(BoardResult.BOARDRESULT_WON, board.mApp.mBoardResult);
    }

    @Test
    void retrackRestoresMissingLawnMowers() {
        Board board = newPlayingBoard();
        // the mowers come in with the level-start cutscene, not with initLevel
        board.initLawnMowers();
        int aLivingMowers = 0;
        for (LawnMower aMower : board.mLawnMowers) {
            if (!aMower.mDead) {
                aLivingMowers++;
            }
        }
        assertTrue(aLivingMowers > 0, "a day lawn starts with mowers");
        // consume the first mower the way a zombie run-over would (mDead), then restore
        LawnMower aLostMower = null;
        for (LawnMower aMower : board.mLawnMowers) {
            if (!aMower.mDead) {
                aMower.mDead = true;
                aLostMower = aMower;
                break;
            }
        }

        typeWord(board, "retrack");
        int aMowersAfter = 0;
        LawnMower aRestoredMower = null;
        for (LawnMower aMower : board.mLawnMowers) {
            if (!aMower.mDead) {
                aMowersAfter++;
                if (aMower.mRow == aLostMower.mRow) {
                    aRestoredMower = aMower;
                }
            }
        }
        assertEquals(aLivingMowers, aMowersAfter);
        assertNotNull(aRestoredMower, "the row that lost its mower must carry one again");
        // the restored mower rolls in from off-board like the bonus mower, not parked off-screen
        assertEquals(LawnMowerState.MOWER_ROLLING_IN, aRestoredMower.mMowerState);
    }

    @Test
    void doTrackStartsEveryReadyMower() {
        Board board = newPlayingBoard();

        typeWord(board, "dotrack");
        for (LawnMower aMower : board.mLawnMowers) {
            if (!aMower.mDead) {
                assertEquals(LawnMowerState.MOWER_TRIGGERED, aMower.mMowerState);
            }
        }
    }

    @Test
    void fillHoeFillsEveryCrater() {
        Board board = newPlayingBoard();
        board.addACrater(3, 2);
        board.addACrater(6, 1);
        assertNotNull(board.getCraterAt(3, 2));

        typeWord(board, "fillhoe");
        assertNull(board.getCraterAt(3, 2));
        assertNull(board.getCraterAt(6, 1));
    }

    @Test
    void finalJumpsToTheLastWave() {
        Board board = newPlayingBoard();
        assertTrue(board.mNumWaves > 2);
        board.mCurrentWave = 2;

        typeWord(board, "final");
        assertEquals(board.mNumWaves - 1, board.mCurrentWave);
        assertEquals(1, board.mZombieCountDown);

        // the next spawn tick brings the final wave up
        board.updateZombieSpawning();
        assertEquals(board.mNumWaves, board.mCurrentWave);
    }

    @Test
    void killZomKillsEveryZombieWithItsDeathAnimation(@TempDir Path theTempDir) throws Exception {
        // the damage death runs the head-drop / reanim-driven path, which the headless stub tracks
        // cannot serve (getTrackPosition indexes an empty array) — retail assets required
        Optional<Path> aRoot = TestAssets.resourceRoot();
        Assumptions.assumeTrue(aRoot.isPresent(), "retail assets required for the damage-death path");
        Board board = ZombieTest.newRealBoard(Files.createDirectories(theTempDir.resolve("savedata")),
                aRoot.get());
        LawnApp app = board.mApp;
        try {
            app.initTypingChecks();
            app.mGameScene = GameScenes.SCENE_PLAYING;
            Zombie aZombie1 = board.addZombieInRow(ZombieType.ZOMBIE_NORMAL, 2, 0);
            Zombie aZombie2 = board.addZombieInRow(ZombieType.ZOMBIE_NORMAL, 3, 0);
            assertFalse(aZombie1.isDeadOrDying());

            typeWord(board, "killzom");
            assertTrue(aZombie1.isDeadOrDying(), "the lethal hit must start the death animation");
            assertTrue(aZombie2.isDeadOrDying());

            for (int i = 0; i < 600 && !aZombie1.mDead; i++) {
                aZombie1.update();
            }
            assertTrue(aZombie1.mDead, "the death animation must run to the removal");
        } finally {
            app.shutdown();
            LawnApp.gLawnApp = null;
        }
    }

    @Test
    void killPlantKillsEveryPlant() {
        Board board = newPlayingBoard();
        Plant aPlant1 = board.addPlant(0, 0, SeedType.SEED_PEASHOOTER, SeedType.SEED_NONE);
        Plant aPlant2 = board.addPlant(3, 2, SeedType.SEED_SUNFLOWER, SeedType.SEED_NONE);
        assertFalse(aPlant1.mDead);

        typeWord(board, "killplant");
        assertTrue(aPlant1.mDead);
        assertTrue(aPlant2.mDead);
    }

    @Test
    void killAllClearsPlantsAndZombies(@TempDir Path theTempDir) throws Exception {
        Optional<Path> aRoot = TestAssets.resourceRoot();
        Assumptions.assumeTrue(aRoot.isPresent(), "retail assets required for the damage-death path");
        Board board = ZombieTest.newRealBoard(Files.createDirectories(theTempDir.resolve("savedata")),
                aRoot.get());
        LawnApp app = board.mApp;
        try {
            app.initTypingChecks();
            app.mGameScene = GameScenes.SCENE_PLAYING;
            Zombie aZombie = board.addZombieInRow(ZombieType.ZOMBIE_NORMAL, 2, 0);
            Plant aPlant = board.addPlant(0, 0, SeedType.SEED_PEASHOOTER, SeedType.SEED_NONE);

            typeWord(board, "killall");
            assertTrue(aZombie.isDeadOrDying());
            assertTrue(aPlant.mDead);
        } finally {
            app.shutdown();
            LawnApp.gLawnApp = null;
        }
    }

    @Test
    void cherryBoomStackPlantsCherryBombsOnEveryCell() {
        Board board = newPlayingBoard();
        // something already planted there gets stacked over
        board.addPlant(2, 2, SeedType.SEED_SUNFLOWER, SeedType.SEED_NONE);

        typeWord(board, "cherryboom");
        int aCherries = 0;
        for (Plant aPlant : board.mPlants) {
            if (!aPlant.mDead && aPlant.mSeedType == SeedType.SEED_CHERRYBOMB) {
                aCherries++;
            }
        }
        // 9 columns x 5 rows on a day lawn, the sunflower included
        assertEquals(45, aCherries);
    }

    @Test
    void fastTogglesDoubleSpeed() {
        Board board = newPlayingBoard();
        LawnApp.gCheatFastMo = false;

        typeWord(board, "fast");
        assertTrue(LawnApp.gCheatFastMo);
        typeWord(board, "fast");
        assertFalse(LawnApp.gCheatFastMo);
    }

    @Test
    void fastSpeedEndsWithTheLevel() {
        Board board = newPlayingBoard();
        LawnApp.gCheatFastMo = false;
        typeWord(board, "fast");
        assertTrue(LawnApp.gCheatFastMo);

        board.fadeOutLevel();
        assertFalse(LawnApp.gCheatFastMo, "the 2x speed must fall back to normal as the level ends");
    }

    @Test
    void loveMindControlsEveryZombie() {
        Board board = newPlayingBoard();
        Zombie aZombie1 = board.addZombie(ZombieType.ZOMBIE_NORMAL, Zombie.ZOMBIE_WAVE_DEBUG);
        Zombie aZombie2 = board.addZombie(ZombieType.ZOMBIE_NORMAL, Zombie.ZOMBIE_WAVE_DEBUG);
        assertFalse(aZombie1.mMindControlled);

        typeWord(board, "love");
        assertTrue(aZombie1.mMindControlled);
        assertTrue(aZombie2.mMindControlled);
    }

    @Test
    void frozeChillsEveryZombie() {
        Board board = newPlayingBoard();
        Zombie aZombie1 = board.addZombie(ZombieType.ZOMBIE_NORMAL, Zombie.ZOMBIE_WAVE_DEBUG);
        Zombie aZombie2 = board.addZombie(ZombieType.ZOMBIE_NORMAL, Zombie.ZOMBIE_WAVE_DEBUG);

        typeWord(board, "froze");
        assertTrue(aZombie1.mChilledCounter > 0 || aZombie1.mIceTrapCounter > 0
                || aZombie1.isDeadOrDying());
        assertTrue(aZombie2.mChilledCounter > 0 || aZombie2.mIceTrapCounter > 0
                || aZombie2.isDeadOrDying());
    }

    // -------------------------------------------------------------------------------------
    // the unlockall title-screen cheat
    // -------------------------------------------------------------------------------------

    @Test
    void unlockAllPushesTheSaveToTheFullyUnlockedState() {
        LawnApp app = newAdventureApp();
        assertFalse(app.hasFinishedAdventure());
        assertFalse(app.hasBeatenChallenge(GameMode.GAMEMODE_CHALLENGE_WAR_AND_PEAS));

        app.unlockAllCheat();

        // adventure finished, every page unlocked, 999990 coins; the progress stands at F1-1 (the
        // second play-through's first level), which is what Dave's seed pick rides on
        assertTrue(app.hasFinishedAdventure());
        assertEquals(1, app.mPlayerInfo.mLevel);
        assertFalse(app.isFirstTimeAdventureMode());
        assertEquals(1, app.mPlayerInfo.mHasUnlockedMinigames);
        assertEquals(1, app.mPlayerInfo.mHasUnlockedPuzzleMode);
        assertEquals(1, app.mPlayerInfo.mHasUnlockedSurvivalMode);
        assertEquals(999990, app.mPlayerInfo.mCoins);

        // every non-endless challenge won: survival by flags, the rest by record, endless untouched
        assertEquals(GameConstants.SURVIVAL_NORMAL_FLAGS,
                app.mPlayerInfo.mChallengeRecords[GameMode.GAMEMODE_SURVIVAL_NORMAL_STAGE_1 - 1]);
        assertEquals(GameConstants.SURVIVAL_HARD_FLAGS,
                app.mPlayerInfo.mChallengeRecords[GameMode.GAMEMODE_SURVIVAL_HARD_STAGE_1 - 1]);
        assertEquals(1, app.mPlayerInfo.mChallengeRecords[GameMode.GAMEMODE_CHALLENGE_WAR_AND_PEAS - 1]);
        assertEquals(1, app.mPlayerInfo.mChallengeRecords[GameMode.GAMEMODE_SCARY_POTTER_1 - 1]);
        assertEquals(1, app.mPlayerInfo.mChallengeRecords[GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_1 - 1]);
        assertEquals(0, app.mPlayerInfo.mChallengeRecords[GameMode.GAMEMODE_SURVIVAL_ENDLESS_STAGE_1 - 1]);
        assertEquals(0, app.mPlayerInfo.mChallengeRecords[GameMode.GAMEMODE_SCARY_POTTER_ENDLESS - 1]);
        assertEquals(0, app.mPlayerInfo.mChallengeRecords[GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_ENDLESS - 1]);
        assertTrue(app.hasBeatenChallenge(GameMode.GAMEMODE_CHALLENGE_WAR_AND_PEAS));
        assertTrue(app.hasBeatenChallenge(GameMode.GAMEMODE_SURVIVAL_NORMAL_STAGE_1));
        assertFalse(app.hasBeatenChallenge(GameMode.GAMEMODE_SURVIVAL_ENDLESS_STAGE_1));

        // the tree of wisdom stands at 500 (its height lives in its own record slot)
        assertEquals(500, app.mPlayerInfo.mChallengeRecords[GameMode.GAMEMODE_TREE_OF_WISDOM - 1]);

        // the store is fully bought: consumables at 100, tree food at 10, everything else owned
        assertEquals(cn.enaium.fabricpvz.portable.lawn.system.PlayerInfo.PURCHASE_COUNT_OFFSET + 100,
                app.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_FERTILIZER]);
        assertEquals(cn.enaium.fabricpvz.portable.lawn.system.PlayerInfo.PURCHASE_COUNT_OFFSET + 100,
                app.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_BUG_SPRAY]);
        assertEquals(cn.enaium.fabricpvz.portable.lawn.system.PlayerInfo.PURCHASE_COUNT_OFFSET + 100,
                app.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_CHOCOLATE]);
        assertEquals(cn.enaium.fabricpvz.portable.lawn.system.PlayerInfo.PURCHASE_COUNT_OFFSET + 100,
                app.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_TREE_FOOD]);
        assertEquals(1, app.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_PLANT_GATLINGPEA]);
        assertEquals(1, app.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_POOL_CLEANER]);
        // all four slot upgrades: the seed bank holds purchases + 6 slots = 10
        assertEquals(4, app.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_PACKET_UPGRADE]);

        // every achievement earned, and the 48 trophies make the gold sunflower fall out
        for (boolean aEarned : app.mPlayerInfo.mEarnedAchievements) {
            assertTrue(aEarned);
        }
        assertTrue(app.earnedGoldTrophy(), "winning all non-endless challenges grants the gold sunflower");
        assertEquals(0, app.trophiesNeedForGoldSunflower());
    }

    /** The badge image must exist in the retail pak: the Chinese GOTY ships different containers. */
    @Test
    void imageLockResolvesFromTheChallengeScreenGroup(@TempDir Path theTempDir) throws Exception {
        Optional<Path> aRoot = TestAssets.resourceRoot();
        Assumptions.assumeTrue(aRoot.isPresent(), "retail assets required for the pak-content probe");
        LawnApp app = ScreenResourceLoadingTest.boot(Files.createDirectories(theTempDir.resolve("savedata")),
                aRoot.get());
        try {
            // the real path of crazyDavePickSeeds: the delayed group loads AND binds through
            // pvzpLoadResources; a plain extract without the load leaves every image null
            assertTrue(((cn.enaium.fabricpvz.portable.pvzp.PvzpResourceManager) app.mResourceManager)
                    .pvzpLoadResources("DelayLoad_ChallengeScreen"));
            assertNotNull(Resources.getImageById(Resources.IMAGE_LOCK_ID),
                    "IMAGE_LOCK must resolve from DelayLoad_ChallengeScreen");
        } finally {
            app.shutdown();
            LawnApp.gLawnApp = null;
        }
    }

    // -------------------------------------------------------------------------------------
    // the F1 level-jump dialog
    // -------------------------------------------------------------------------------------

    @Test
    void f1OpensTheLevelJumpDialogAndPausesTheLevel() {
        Board board = newPlayingBoard();
        assertFalse(board.mPaused);
        assertNull(board.mApp.getDialog(Dialogs.DIALOG_CHEAT));

        board.mApp.f1Pressed();

        // the dialog is up, seeded with the current stage string, and the level behind it pauses
        assertNotNull(board.mApp.getDialog(Dialogs.DIALOG_CHEAT));
        assertTrue(board.mPaused, "the level must pause while the jump dialog is up");

        // cancel closes the dialog and resumes the level
        board.mApp.finishCheatDialog(false);
        assertNull(board.mApp.getDialog(Dialogs.DIALOG_CHEAT));
        assertFalse(board.mPaused, "cancelling the jump dialog must resume the level");
    }

    @Test
    void f1IsIgnoredWithoutABoardOrUnderAnotherDialog() {
        Board board = newPlayingBoard();
        board.mApp.mGameScene = GameScenes.SCENE_LEVEL_INTRO;
        board.mApp.f1Pressed();
        assertNull(board.mApp.getDialog(Dialogs.DIALOG_CHEAT), "no jump dialog outside SCENE_PLAYING");

        Board aPlayingBoard = newPlayingBoard();
        aPlayingBoard.mApp.f1Pressed();
        assertNotNull(aPlayingBoard.mApp.getDialog(Dialogs.DIALOG_CHEAT));
        // a second press under the open dialog must not re-open or re-pause
        aPlayingBoard.mApp.f1Pressed();
        assertNotNull(aPlayingBoard.mApp.getDialog(Dialogs.DIALOG_CHEAT));
    }

    @Test
    void f1OkRestartsIntoTheEnteredLevel() {
        Board board = newPlayingBoard();
        LoadedReanimApp app = (LoadedReanimApp) board.mApp;
        board.mApp.f1Pressed();

        // an invalid level string opens the error dialog on top and keeps the level paused
        CheatDialog aDialog = (CheatDialog) board.mApp.getDialog(Dialogs.DIALOG_CHEAT);
        aDialog.mLevelEditWidget.setText("-5", true);
        board.mApp.finishCheatDialog(true);
        assertNotNull(board.mApp.getDialog(Dialogs.DIALOG_CHEAT), "an invalid level keeps the dialog");
        assertNotNull(board.mApp.getDialog(Dialogs.DIALOG_CHEATERROR));
        assertTrue(board.mPaused);

        // dismiss the error, then a valid level applies the cheat and restarts (preNewGame recorded)
        board.mApp.killDialog(Dialogs.DIALOG_CHEATERROR);
        aDialog.mLevelEditWidget.setText("2-3", true);
        board.mApp.finishCheatDialog(true);
        assertNull(board.mApp.getDialog(Dialogs.DIALOG_CHEAT));
        assertFalse(board.mPaused, "the last closed modal resumes the level");
        assertEquals(1, app.mPreNewGameCount, "the OK path restarts into the entered level");
    }

    // -------------------------------------------------------------------------------------
    // the advice captions
    // -------------------------------------------------------------------------------------

    @Test
    void cheatWordsShowTheirAdviceCaptions() {
        Board board = newPlayingBoard();

        typeWord(board, "cheatplant");
        assertTrue(board.mAdvice.mLabel.contains("ADVICE_EASY_PLANTING_CHEAT_ON"));
        typeWord(board, "cheatplant");
        assertTrue(board.mAdvice.mLabel.contains("ADVICE_EASY_PLANTING_CHEAT_OFF"));

        typeWord(board, "lazyclick");
        assertTrue(board.mAdvice.mLabel.contains("ADVICE_LAZY_CLICK_ON"));
        typeWord(board, "lazyclick");
        assertTrue(board.mAdvice.mLabel.contains("ADVICE_LAZY_CLICK_OFF"));

        typeWord(board, "fast");
        assertTrue(board.mAdvice.mLabel.contains("ADVICE_2X_ON"));
        typeWord(board, "fast");
        assertTrue(board.mAdvice.mLabel.contains("ADVICE_2X_OFF"));

        board.addPlant(0, 0, SeedType.SEED_PEASHOOTER, SeedType.SEED_NONE);
        typeWord(board, "killplant");
        assertTrue(board.mAdvice.mLabel.contains("ADVICE_KILL_PLANTS"));

        typeWord(board, "killall");
        assertTrue(board.mAdvice.mLabel.contains("ADVICE_KILL_ALL"));
    }

    // -------------------------------------------------------------------------------------
    // gating
    // -------------------------------------------------------------------------------------

    @Test
    void hiddenCheatsDoNothingOutsidePlayingScene() {
        Board board = newPlayingBoard();
        board.mApp.mGameScene = GameScenes.SCENE_LEVEL_INTRO;
        Zombie aZombie = board.addZombie(ZombieType.ZOMBIE_NORMAL, Zombie.ZOMBIE_WAVE_DEBUG);
        Plant aPlant = board.addPlant(0, 0, SeedType.SEED_PEASHOOTER, SeedType.SEED_NONE);
        int aWaveBefore = board.mCurrentWave;

        typeWord(board, "killall");
        assertFalse(aZombie.isDeadOrDying());
        assertFalse(aPlant.mDead);
        assertFalse(board.mApp.mEasyPlantingCheat);
        assertFalse(board.mApp.mAutoCollectCheat);
        assertFalse(LawnApp.gCheatFastMo);
        assertFalse(board.mLevelAwardSpawned);
        assertEquals(aWaveBefore, board.mCurrentWave); // untouched by "final"
        assertNotEquals(BoardResult.BOARDRESULT_WON, board.mApp.mBoardResult);
    }
}
