/*
 * Tests for ChallengeScreen / ChallengeDefinition static tables, lock/enablement logic and layout.
 */
package cn.enaium.fabricpvz.portable.lawn.widget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import cn.enaium.fabricpvz.portable.lawn.ChallengePage;
import cn.enaium.fabricpvz.portable.lawn.GameMode;
import cn.enaium.fabricpvz.portable.lawn.LawnApp;
import cn.enaium.fabricpvz.portable.sexy.ButtonWidget;

/**
 * Exercises the pure (asset-free) surface of {@link ChallengeScreen}: the 72-entry
 * {@code gChallengeDefs} table, index alignment, page membership, the trophy lock rules
 * and the thumbnail layout constants.
 */
class ChallengeScreenTest {

    @Test
    void tableHasSeventyTwoEntriesIndexAlignedToGameMode() {
        assertEquals(72, ChallengeScreen.NUM_CHALLENGE_MODES);
        assertEquals(ChallengeScreen.NUM_CHALLENGE_MODES, ChallengeScreen.gChallengeDefs.length);

        for (int i = 0; i < ChallengeScreen.gChallengeDefs.length; i++) {
            // gChallengeDefs is strictly index-aligned: def index == GameMode - GAMEMODE_SURVIVAL_NORMAL_STAGE_1.
            assertEquals(i + 1, ChallengeScreen.gChallengeDefs[i].mChallengeMode,
                    "def " + i + " should open GameMode " + (i + 1));
            assertEquals(i, ChallengeScreen.getChallengeDefinition(i).mChallengeMode - 1);
        }
    }

    /**
     * PORT-NOTE (deviation): upstream banks 11 survival / 21 limbo rows; the hidden-content
     * enablement moves the four endless variants onto the survival page's endless row (15/17).
     */
    @Test
    void pageMembershipCounts() {
        assertEquals(15, countOnPage(ChallengePage.CHALLENGE_PAGE_SURVIVAL));
        assertEquals(20, countOnPage(ChallengePage.CHALLENGE_PAGE_CHALLENGE));
        assertEquals(17, countOnPage(ChallengePage.CHALLENGE_PAGE_LIMBO));
        assertEquals(20, countOnPage(ChallengePage.CHALLENGE_PAGE_PUZZLE));
        assertEquals(72, countOnPage(ChallengePage.CHALLENGE_PAGE_SURVIVAL)
                + countOnPage(ChallengePage.CHALLENGE_PAGE_CHALLENGE)
                + countOnPage(ChallengePage.CHALLENGE_PAGE_LIMBO)
                + countOnPage(ChallengePage.CHALLENGE_PAGE_PUZZLE));
    }

    @Test
    void rawLiteralNamesAreKept() {
        assertEquals("Squirrel", ChallengeScreen.getChallengeDefinition(GameMode.GAMEMODE_CHALLENGE_SQUIRREL - 1).mChallengeName);
        assertEquals("Tree of Wisdom", ChallengeScreen.getChallengeDefinition(GameMode.GAMEMODE_TREE_OF_WISDOM - 1).mChallengeName);
        assertEquals("Upsell", ChallengeScreen.getChallengeDefinition(GameMode.GAMEMODE_UPSELL - 1).mChallengeName);
        assertEquals("Intro", ChallengeScreen.getChallengeDefinition(GameMode.GAMEMODE_INTRO - 1).mChallengeName);
    }

    @Test
    void entryEnablementTable() {
        for (int i = 0; i < ChallengeScreen.gChallengeDefs.length; i++) {
            // Unlocked (no pending trophy): every entry is enabled.
            assertTrue(ChallengeScreen.isEntryEnabled(i, 0),
                    "entry " + i + " (mode " + ChallengeScreen.gChallengeDefs[i].mChallengeMode + ") must be enabled");

            // A pending trophy requirement always disables the entry.
            assertFalse(ChallengeScreen.isEntryEnabled(i, 1), "entry " + i + " should stay locked");
            assertFalse(ChallengeScreen.isEntryEnabled(i, 10), "entry " + i + " should stay locked");
        }
    }

    @Test
    void layoutGeometry() {
        // ChallengeScreen.cpp:166-170 -> survival rows are 145 px apart from y=125, other pages 119 from y=93.
        assertEquals(38, ChallengeScreen.challengeButtonX(ChallengePage.CHALLENGE_PAGE_CHALLENGE, 0));
        assertEquals(193, ChallengeScreen.challengeButtonX(ChallengePage.CHALLENGE_PAGE_CHALLENGE, 1));
        assertEquals(658, ChallengeScreen.challengeButtonX(ChallengePage.CHALLENGE_PAGE_CHALLENGE, 4));
        assertEquals(125, ChallengeScreen.challengeButtonY(ChallengePage.CHALLENGE_PAGE_SURVIVAL, 0));
        assertEquals(415, ChallengeScreen.challengeButtonY(ChallengePage.CHALLENGE_PAGE_SURVIVAL, 2));
        assertEquals(93, ChallengeScreen.challengeButtonY(ChallengePage.CHALLENGE_PAGE_CHALLENGE, 0));
        assertEquals(450, ChallengeScreen.challengeButtonY(ChallengePage.CHALLENGE_PAGE_CHALLENGE, 3));
        assertEquals(93, ChallengeScreen.challengeButtonY(ChallengePage.CHALLENGE_PAGE_PUZZLE, 0));

        assertEquals(104, ChallengeScreen.CHALLENGE_BUTTON_WIDTH);
        assertEquals(115, ChallengeScreen.CHALLENGE_BUTTON_HEIGHT);

        // The page switcher wears the back button's look: (200 + 100*page, 568, 111, 26).
        assertEquals(200, ChallengeScreen.CHALLENGE_PAGE_BUTTON_X);
        assertEquals(100, ChallengeScreen.CHALLENGE_PAGE_BUTTON_PITCH);
        assertEquals(568, ChallengeScreen.CHALLENGE_PAGE_BUTTON_Y);
        assertEquals(111, ChallengeScreen.CHALLENGE_PAGE_BUTTON_WIDTH);
        assertEquals(26, ChallengeScreen.CHALLENGE_PAGE_BUTTON_HEIGHT);

        // ChallengeScreen.cpp:129-139 -> back button at (18, 568, 111, 26).
        assertEquals(18, ChallengeScreen.CHALLENGE_BACK_BUTTON_X);
        assertEquals(568, ChallengeScreen.CHALLENGE_BACK_BUTTON_Y);
        assertEquals(111, ChallengeScreen.CHALLENGE_BACK_BUTTON_WIDTH);
        assertEquals(26, ChallengeScreen.CHALLENGE_BACK_BUTTON_HEIGHT);
    }

    /**
     * PORT-NOTE (deviation): the limbo page uses its own 6-column grid (port addition) — six
     * 104px thumbnails centered (x = 48, pitch 120, right edge 648 + 104 = 752), rows 0-1 for the
     * 12 playable entries and a separated showcase row at y=380 for the four featured entries.
     */
    @Test
    void limboPageLayoutGeometry() {
        assertEquals(48, ChallengeScreen.challengeButtonX(ChallengePage.CHALLENGE_PAGE_LIMBO, 0));
        assertEquals(168, ChallengeScreen.challengeButtonX(ChallengePage.CHALLENGE_PAGE_LIMBO, 1));
        assertEquals(648, ChallengeScreen.challengeButtonX(ChallengePage.CHALLENGE_PAGE_LIMBO, 5));
        assertEquals(93, ChallengeScreen.challengeButtonY(ChallengePage.CHALLENGE_PAGE_LIMBO, 0));
        assertEquals(212, ChallengeScreen.challengeButtonY(ChallengePage.CHALLENGE_PAGE_LIMBO, 1));
        assertEquals(380, ChallengeScreen.challengeButtonY(ChallengePage.CHALLENGE_PAGE_LIMBO, 2),
                "the showcase row, separated from row 1 (212 + 115 = 327)");
    }

    /**
     * PORT-NOTE (deviation): the re-laid-out limbo page shows 16 of its 17 entries — 12 playable
     * in rows 0-1 (6 per row), the four showcase entries (zen garden / tree of wisdom / upsell /
     * intro) centered on the separated row; the squirrel tech demo stays off the page (upstream
     * parked it in intro's cell, never visible in retail).
     */
    @Test
    void limboPageShowsTwelvePlayableAndFourShowcaseEntries() {
        ChallengeScreen aScreen = newScreen(ChallengePage.CHALLENGE_PAGE_LIMBO);

        int aPlayable = 0;
        int aShowcase = 0;
        for (int i = 0; i < ChallengeScreen.NUM_CHALLENGE_MODES; i++) {
            ChallengeDefinition aDef = ChallengeScreen.getChallengeDefinition(i);
            if (aDef.mPage != ChallengePage.CHALLENGE_PAGE_LIMBO || !aScreen.mChallengeButtons[i].mVisible) {
                continue;
            }
            if (aDef.mRow == 2) {
                aShowcase++;
                assertEquals(380, aScreen.mChallengeButtons[i].mY, aDef.mChallengeName + " on the showcase row");
                assertTrue(aDef.mCol >= 1 && aDef.mCol <= 4, aDef.mChallengeName + " centered (cols 1-4)");
            } else {
                aPlayable++;
                assertEquals(93 + aDef.mRow * 119, aScreen.mChallengeButtons[i].mY,
                        aDef.mChallengeName + " on playable row " + aDef.mRow);
                assertTrue(aDef.mCol >= 0 && aDef.mCol <= 5, aDef.mChallengeName + " in the 6-column grid");
            }
        }
        assertEquals(12, aPlayable, "twelve playable entries in two 6-column rows");
        assertEquals(4, aShowcase, "four showcase entries on the separated row");
        assertFalse(aScreen.mChallengeButtons[GameMode.GAMEMODE_CHALLENGE_SQUIRREL - 1].mVisible,
                "the squirrel tech demo stays off the page");
    }

    /**
     * PORT-NOTE (deviation): the switcher lives on the mini-game and limbo pages only — upstream
     * showed it there with cheat keys (ChallengeScreen.cpp:356-360) and revealed the limbo button
     * only after the five-click easter egg; the hidden-content enablement drops both gates. The
     * survival and puzzle pages keep no switcher and no buttons.
     */
    @Test
    void theSwitcherShowsOnlyOnMinigameAndLimboPages() {
        ChallengeScreen aSurvival = newScreen(ChallengePage.CHALLENGE_PAGE_SURVIVAL);
        assertEquals(0, countVisiblePageButtons(aSurvival), "no switcher on the survival page");
        assertFalse(aSurvival.showPageButtons());

        ChallengeScreen aPuzzle = newScreen(ChallengePage.CHALLENGE_PAGE_PUZZLE);
        assertEquals(0, countVisiblePageButtons(aPuzzle), "no switcher on the puzzle page");
        assertFalse(aPuzzle.showPageButtons());

        ChallengeScreen aMinigame = newScreen(ChallengePage.CHALLENGE_PAGE_CHALLENGE);
        assertEquals(2, countVisiblePageButtons(aMinigame), "page 1 + the limbo button");
        assertTrue(aMinigame.showPageButtons(), "no cheat keys required");

        ChallengeScreen aLimbo = newScreen(ChallengePage.CHALLENGE_PAGE_LIMBO);
        assertEquals(2, countVisiblePageButtons(aLimbo), "the way back from the limbo page");
        assertTrue(aLimbo.showPageButtons());
    }

    /** Both visible buttons carry the numbered [PAGE_X] label: the limbo button reads as page two
     * (the retail table's LIMBO_PAGE_BUTTON text is not used any more). */
    @Test
    void pageButtonsCarryNumberedLabels() {
        LawnApp anApp = new LawnApp();
        cn.enaium.fabricpvz.portable.test.HeadlessTestApp.initialize(anApp);
        anApp.mStringProperties.put("PAGE_X", "Page {PAGE}");
        ChallengeScreen aScreen = new ChallengeScreen(anApp, ChallengePage.CHALLENGE_PAGE_CHALLENGE);

        assertEquals("Page 1", aScreen.mPageButton[ChallengePage.CHALLENGE_PAGE_CHALLENGE].mLabel);
        assertEquals("Page 2", aScreen.mPageButton[ChallengePage.CHALLENGE_PAGE_LIMBO].mLabel,
                "the limbo button reads as page two");
        assertFalse(aScreen.mPageButton[ChallengePage.CHALLENGE_PAGE_SURVIVAL].mVisible);
        assertFalse(aScreen.mPageButton[ChallengePage.CHALLENGE_PAGE_PUZZLE].mVisible);
    }

    /** The limbo page is reachable from the switcher and its entries are all unlocked. */
    @Test
    void limboPageIsReachableAndUnlocked() {
        ChallengeScreen aScreen = newScreen(ChallengePage.CHALLENGE_PAGE_CHALLENGE);

        aScreen.buttonDepress(ChallengeScreen.ChallengeScreen_Page + ChallengePage.CHALLENGE_PAGE_LIMBO);
        assertEquals(ChallengePage.CHALLENGE_PAGE_LIMBO, aScreen.mPageIndex, "the switcher navigates");

        int aVisible = 0;
        for (int i = 0; i < ChallengeScreen.NUM_CHALLENGE_MODES; i++) {
            ChallengeDefinition aDef = ChallengeScreen.getChallengeDefinition(i);
            if (aDef.mPage == ChallengePage.CHALLENGE_PAGE_LIMBO) {
                if (aScreen.mChallengeButtons[i].mVisible) {
                    aVisible++;
                }
                assertEquals(0, aScreen.moreTrophiesNeeded(i),
                        "limbo entry " + aDef.mChallengeName + " stays unlocked");
            }
        }
        assertEquals(16, aVisible, "the re-laid-out limbo page shows 16 of its 17 entries");
    }

    /** The four surfaced endless variants follow the pool endless entry's lock rule exactly. */
    @Test
    void endlessVariantsFollowThePoolEndlessLockRule() {
        ChallengeScreen aScreen = newScreen(ChallengePage.CHALLENGE_PAGE_SURVIVAL);

        int aPoolNeeded = aScreen.moreTrophiesNeeded(GameMode.GAMEMODE_SURVIVAL_ENDLESS_STAGE_3 - 1);
        int[] anEndlessStages = {
                GameMode.GAMEMODE_SURVIVAL_ENDLESS_STAGE_1, GameMode.GAMEMODE_SURVIVAL_ENDLESS_STAGE_2,
                GameMode.GAMEMODE_SURVIVAL_ENDLESS_STAGE_4, GameMode.GAMEMODE_SURVIVAL_ENDLESS_STAGE_5};
        for (int aStage : anEndlessStages) {
            assertEquals(aPoolNeeded, aScreen.moreTrophiesNeeded(aStage - 1),
                    "stage " + aStage + " locked like the pool endless entry");
        }
    }

    /** Builds a headless screen on the given page (stub images/audio via the shared fixture). */
    private ChallengeScreen newScreen(int thePage) {
        LawnApp anApp = new cn.enaium.fabricpvz.portable.lawn.LawnApp();
        cn.enaium.fabricpvz.portable.test.HeadlessTestApp.initialize(anApp);
        return new ChallengeScreen(anApp, thePage);
    }

    private static int countVisiblePageButtons(ChallengeScreen theScreen) {
        int aCount = 0;
        for (ButtonWidget aButton : theScreen.mPageButton) {
            if (aButton != null && aButton.mVisible) {
                aCount++;
            }
        }
        return aCount;
    }

    /**
     * PORT-NOTE (deviation): the limbo total is a port addition — the re-laid-out page counters
     * its 12 playable entries; upstream showed no counter there (total 0).
     */
    @Test
    void trophyTotalsMatchEachPage() {
        assertEquals(10, ChallengeScreen.trophyTotalForPage(ChallengePage.CHALLENGE_PAGE_SURVIVAL));
        assertEquals(20, ChallengeScreen.trophyTotalForPage(ChallengePage.CHALLENGE_PAGE_CHALLENGE));
        assertEquals(18, ChallengeScreen.trophyTotalForPage(ChallengePage.CHALLENGE_PAGE_PUZZLE));
        assertEquals(12, ChallengeScreen.trophyTotalForPage(ChallengePage.CHALLENGE_PAGE_LIMBO),
                "the limbo page counters its playable entries");
        assertEquals(0, ChallengeScreen.trophyTotalForPage(-1));
    }

    /**
     * PORT-NOTE (deviation): the limbo showcase entries are not winnable challenges — never a
     * trophy badge, never counted. The record-isolation half is asserted in LawnApp-side tests of
     * getNumTrophies; here the predicate itself.
     */
    @Test
    void showcaseEntriesAreExactlyTheFourUtilityModes() {
        int[] aShowcase = {GameMode.GAMEMODE_CHALLENGE_ZEN_GARDEN, GameMode.GAMEMODE_TREE_OF_WISDOM,
                GameMode.GAMEMODE_UPSELL, GameMode.GAMEMODE_INTRO};
        for (int aMode : aShowcase) {
            assertTrue(ChallengeScreen.isShowcaseEntry(aMode), "mode " + aMode + " is showcase");
        }

        int[] aPlayable = {GameMode.GAMEMODE_CHALLENGE_SUNNY_DAY, GameMode.GAMEMODE_CHALLENGE_ICE,
                GameMode.GAMEMODE_CHALLENGE_SQUIRREL, GameMode.GAMEMODE_SURVIVAL_ENDLESS_STAGE_3,
                GameMode.GAMEMODE_CHALLENGE_FINAL_BOSS};
        for (int aMode : aPlayable) {
            assertFalse(ChallengeScreen.isShowcaseEntry(aMode), "mode " + aMode + " is playable");
        }
    }

    /** Whatever their record slots hold, the showcase entries never count toward any page's
     * trophy total (getNumTrophies filters them); a playable entry with a record does count. */
    @Test
    void showcaseEntriesNeverCountTowardTrophies() {
        ChallengeScreen aScreen = newScreen(ChallengePage.CHALLENGE_PAGE_LIMBO);
        LawnApp anApp = aScreen.mApp;

        anApp.mPlayerInfo.mChallengeRecords[GameMode.GAMEMODE_CHALLENGE_ZEN_GARDEN - 1] = 100;
        anApp.mPlayerInfo.mChallengeRecords[GameMode.GAMEMODE_TREE_OF_WISDOM - 1] = 100;
        anApp.mPlayerInfo.mChallengeRecords[GameMode.GAMEMODE_UPSELL - 1] = 100;
        anApp.mPlayerInfo.mChallengeRecords[GameMode.GAMEMODE_INTRO - 1] = 100;
        anApp.mPlayerInfo.mChallengeRecords[GameMode.GAMEMODE_CHALLENGE_SUNNY_DAY - 1] = 1;

        assertEquals(1, anApp.getNumTrophies(ChallengePage.CHALLENGE_PAGE_LIMBO),
                "only the playable entry's record counts");
    }

    /** The raw-literal showcase names resolve the retail table's same-named keys when banked
     * (port addition: they were never visible upstream), falling back to the literal otherwise. */
    @Test
    void rawNamesPreferTheBankedLocalizationKey() {
        ChallengeScreen aScreen = newScreen(ChallengePage.CHALLENGE_PAGE_LIMBO);
        LawnApp anApp = aScreen.mApp;

        anApp.mStringProperties.put("TREE_OF_WISDOM", "智慧树");
        anApp.mStringProperties.put("UPSELL", "追加销售");
        anApp.mStringProperties.put("INTRO", "介绍");
        anApp.mStringProperties.put("SURVIVAL_DAY_NORMAL", "\n白天(普通)\n");
        assertEquals("智慧树", ChallengeScreen.resolveChallengeName("Tree of Wisdom"));
        assertEquals("追加销售", ChallengeScreen.resolveChallengeName("Upsell"));
        assertEquals("介绍", ChallengeScreen.resolveChallengeName("Intro"));
        assertEquals("Squirrel", ChallengeScreen.resolveChallengeName("Squirrel"),
                "no SQUIRREL key banked here - the literal falls back");

        anApp.mStringProperties.remove("TREE_OF_WISDOM");
        assertEquals("Tree of Wisdom", ChallengeScreen.resolveChallengeName("Tree of Wisdom"),
                "no key - the upstream literal falls back");
        assertEquals("白天(普通)", ChallengeScreen.resolveChallengeName("[SURVIVAL_DAY_NORMAL]")
                        .replace("\n", ""),
                "bracketed names keep the plain translate path");
    }

    private static int countOnPage(int thePage) {
        int aCount = 0;
        for (ChallengeDefinition aDef : ChallengeScreen.gChallengeDefs) {
            if (aDef.mPage == thePage) {
                aCount++;
            }
        }
        return aCount;
    }
}
