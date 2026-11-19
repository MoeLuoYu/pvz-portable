package cn.enaium.fabricpvz.trainermod;

import cn.enaium.fabricpvz.api.lawn.LawnEvents;
import cn.enaium.fabricpvz.portable.lawn.AdviceType;
import cn.enaium.fabricpvz.portable.lawn.Board;
import cn.enaium.fabricpvz.portable.lawn.Coin;
import cn.enaium.fabricpvz.portable.lawn.DamageFlags;
import cn.enaium.fabricpvz.portable.lawn.Dialogs;
import cn.enaium.fabricpvz.portable.lawn.DrawVariation;
import cn.enaium.fabricpvz.portable.lawn.GameScenes;
import cn.enaium.fabricpvz.portable.lawn.GridItemType;
import cn.enaium.fabricpvz.portable.lawn.RenderLayer;
import cn.enaium.fabricpvz.portable.lawn.GridItemState;
import cn.enaium.fabricpvz.portable.lawn.LawnCommon;
import cn.enaium.fabricpvz.portable.lawn.LawnEditWidget;
import cn.enaium.fabricpvz.portable.lawn.LawnMower;
import cn.enaium.fabricpvz.portable.lawn.LawnMowerState;
import cn.enaium.fabricpvz.portable.lawn.MessageStyle;
import cn.enaium.fabricpvz.portable.lawn.Plant;
import cn.enaium.fabricpvz.portable.lawn.PlantRowType;
import cn.enaium.fabricpvz.portable.lawn.PlantState;
import cn.enaium.fabricpvz.portable.lawn.SeedType;
import cn.enaium.fabricpvz.portable.lawn.PottedPlantAge;
import cn.enaium.fabricpvz.portable.lawn.SoundType;
import cn.enaium.fabricpvz.portable.lawn.StoreItem;
import cn.enaium.fabricpvz.portable.lawn.Zombie;
import cn.enaium.fabricpvz.portable.lawn.ZenGarden;
import cn.enaium.fabricpvz.portable.lawn.system.PottedPlant;
import cn.enaium.fabricpvz.portable.lawn.system.PlayerInfo;
import cn.enaium.fabricpvz.portable.lawn.SeedPacket;
import cn.enaium.fabricpvz.portable.lawn.widget.GameButton;
import cn.enaium.fabricpvz.portable.lawn.widget.GameSelector;
import cn.enaium.fabricpvz.portable.lawn.widget.LawnDialog;
import cn.enaium.fabricpvz.portable.lawn.widget.LawnStoneButton;
import cn.enaium.fabricpvz.portable.sexy.Checkbox;
import cn.enaium.fabricpvz.portable.sexy.CheckboxListener;
import cn.enaium.fabricpvz.portable.sexy.Dialog;
import cn.enaium.fabricpvz.portable.sexy.EditListener;
import cn.enaium.fabricpvz.portable.sexy.Slider;
import cn.enaium.fabricpvz.portable.sexy.SliderListener;
import cn.enaium.fabricpvz.portable.sexy.Widget;
import cn.enaium.fabricpvz.portable.sexy.WidgetManager;
import cn.enaium.fabricpvz.portable.sexy.graphics.Font;
import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;
import cn.enaium.fabricpvz.portable.sexy.graphics.Image;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * 修改器模组：左下角**可拖动的原生石头按钮**，点击展开 **PvZ 原生对话框**操作，
 * 对话框底部"收 起"按钮收回折叠（回到悬浮按钮）。零 Mixin。
 *
 * <p>全部走公开面：
 * <ul>
 *   <li>悬浮按钮——{@link TrainerButton}（{@code LawnStoneButton} 子类）：
 *     绘制走游戏的 {@code GameButton.drawStoneButton}（IMAGE_BUTTON_LEFT/MIDDLE/RIGHT
 *     原生三段石钮贴图，悬停/按下状态齐全）。拖动用标准 Sexy 派发：
 *     mouseDown 记抓取偏移，TICK 里以 {@code WidgetManager.mLastMouseX/Y}（public）
 *     跟随鼠标，mouseUp 按位移量区分"点击"（展开对话框）与"拖动"；松手派发给
 *     last-down widget（即使指针已移出按钮也可靠收到）。</li>
 *   <li>对话框——{@link TrainerDialog}（{@code LawnDialog} 子类）：原生棕框/金色标题、
 *     原生 Checkbox（OPTIONS_CHECKBOX0/1 美术）与原生石钮；modal=true 自动暂停棋盘
 *     （{@code modalOpen}/{@code modalClose} 既有机制），底部"收 起"是
 *     {@code BUTTONS_FOOTER} 模式的标准 footer 按钮。</li>
 *   <li>置顶维护——关卡重开时棋盘会排到按钮之上，TICK 检测按钮不在列表末位就
 *     摘下重加；{@code mVisible=false} 不参与命中测试实现场景门控。</li>
 * </ul>
 */
public final class TrainerMod implements ModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("pvz-trainer-mod");

    /** 悬浮按钮不走监听派发（点击/拖动语义自理），传一个空壳监听避免 NPE。 */
    private static final cn.enaium.fabricpvz.portable.sexy.ButtonListener NOOP_LISTENER =
            new cn.enaium.fabricpvz.portable.sexy.ButtonListener() {
                @Override
                public void buttonPress(int theId) {
                }

                @Override
                public void buttonDepress(int theId) {
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
            };

    /** 无限阳光强制写入的数值。 */
    private static final int INFINITE_SUN_VALUE = 9990;
    /** 秒杀伤害与标志：与 Board.killAllZombiesInRadius 的非燃烧路径同参（Board.cpp:9312），
     * 但去掉 DAMAGE_DOESNT_LEAVE_BODY——正常致死留尸体。 */
    private static final int KILL_DAMAGE = 1800;
    private static final int KILL_FLAGS = DamageFlags.DAMAGE_HITS_SHIELD_AND_BODY;

    /** 本模组的对话框 id：游戏 Dialogs 常量只用到 5x，取高位避开。 */
    private static final int TRAINER_DIALOG_ID = 900;
    /** 配置对话框 id（修改器本体 900 的相邻高位段）。 */
    private static final int TRAINER_CONFIG_DIALOG_ID = 901;
    /** 设置金币对话框 id。 */
    private static final int TRAINER_COIN_DIALOG_ID = 902;
    /** 设置智慧树高度对话框 id。 */
    private static final int TRAINER_TREE_HEIGHT_DIALOG_ID = 903;
    /** 对话框内自建按钮/复选框的 id 段：避开 Dialog 的 1000/1001。 */
    private static final int ID_KILL_ALL = 2005;
    private static final int ID_CHECK_SUN = 2001;
    private static final int ID_CHECK_PLANT = 2002;
    private static final int ID_CHECK_COLLECT = 2003;
    private static final int ID_CHECK_PAUSE_SPAWN = 2006;
    private static final int ID_NEXT_WAVE = 2007;
    private static final int ID_ADVANCE_FLAG = 2008;
    private static final int ID_KILL_PLANTS = 2009;
    private static final int ID_CLEAR_MOWERS = 2010;
    private static final int ID_RESTORE_MOWERS = 2011;
    private static final int ID_START_MOWERS = 2012;
    private static final int ID_SKIP_LEVEL = 2013;
    private static final int ID_TAB = 2014;
    private static final int ID_WIN_LEVEL = 2016;
    private static final int ID_EFFECT_SUKHBIR = 2030;
    private static final int ID_EFFECT_FUTURE = 2031;
    private static final int ID_EFFECT_MUSTACHE = 2032;
    private static final int ID_EFFECT_TRICKEDOUT = 2033;
    private static final int ID_EFFECT_DAISIES = 2034;
    private static final int ID_EFFECT_DANCE = 2035;
    private static final int ID_EFFECT_PINATA = 2036;
    private static final int ID_EFFECT_KONAMI = 2037;
    private static final int ID_UNLOCK_GOLD_SUNFLOWER = 2017;
    private static final int ID_TMP_UNLOCK_MODES = 2018;
    private static final int ID_SET_COINS = 2019;
    private static final int ID_TREE_HEIGHT = 2020;
    private static final int ID_ADD_SPROUT = 2022;
    private static final int ID_ADD_MARIGOLD = 2023;
    private static final int ID_ADD_BIG_PLANT = 2024;
    private static final int ID_CHECK_AUTO_PAUSE = 2101;
    private static final int ID_CHECK_ALWAYS_ON_TOP = 2102;
    private static final int ID_CHECK_INFINITE_COINS = 2103;
    private static final int ID_CHECK_INFINITE_FERTILIZER = 2104;
    private static final int ID_CHECK_INFINITE_BUG_SPRAY = 2105;
    private static final int ID_CHECK_INFINITE_CHOCOLATE = 2106;
    private static final int ID_CHECK_INFINITE_TREE_FOOD = 2107;
    private static final int ID_CHECK_COB_NO_CD = 2117;
    private static final int ID_CHECK_MAGNET_NO_CD = 2118;
    private static final int ID_CHECK_POTATO_NO_CD = 2119;
    private static final int ID_CHECK_CHOMPER_NO_CD = 2120;
    private static final int ID_CHECK_PLANT_INVINCIBLE = 2121;
    private static final int ID_CHECK_PLANT_FRAGILE = 2122;
    private static final int ID_CHECK_NO_CRATER = 2123;
    private static final int ID_CHECK_MUSHROOM_AWAKE = 2124;
    private static final int ID_CHECK_REMOVE_FOG = 2125;
    private static final int ID_CHECK_VASE_XRAY = 2126;
    // 特效页"关卡机制复刻"开关：开启后把 mGameMode 压到对应挑战模式，
    // 游戏自己的逐帧检查随即复刻该模式的机制；互斥
    private static final int ID_CHECK_MODE_SEED_RAIN = 2127;
    private static final int ID_CHECK_MODE_MINI_ZOMBIE = 2128;
    private static final int ID_CHECK_MODE_COLUMN = 2129;
    private static final int ID_CHECK_MODE_ZOMBIE_SPEED = 2130;
    private static final int ID_CHECK_MODE_WHACK = 2131;
    private static final int ID_CHECK_MODE_NEWTON = 2132;
    private static final int ID_CHECK_MODE_GRAVE_DANGER = 2133;
    private static final int ID_CHECK_MODE_STORMY_NIGHT = 2134;
    private static final int ID_CHECK_MODE_BUNGEE_BLITZ = 2135;
    private static final int ID_FIELD_SPEED = 2140;
    private static final int ID_CHECK_ZOMBIE_EXPLODE = 2142;
    private static final int ID_CHECK_ZOMBIE_NEVER_EXPLODE = 2143;
    private static final int ID_CHECK_ZOMBIE_NO_CHILL = 2144;
    private static final int ID_CHECK_ZOMBIE_NO_BUTTER = 2145;
    private static final int ID_CHECK_ZOMBIE_HIDE = 2146;
    private static final int ID_CHECK_ZOMBIE_SHOW = 2147;
    private static final int ID_CHECK_ZOMBIE_NO_DANCERS = 2148;
    private static final int ID_CHECK_ZOMBIE_NO_ICE_TRAIL = 2149;
    private static final int ID_CHECK_ZOMBIE_INVINCIBLE = 2150;
    private static final int ID_CHECK_ZOMBIE_WEAK = 2151;
    private static final int ID_CHECK_ZOMBIE_NO_IMP_THROW = 2152;
    private static final int ID_CHECK_ZOMBIE_GARG_EDIBLE = 2153;
    private static final int ID_CHECK_ZOMBIE_FROZEN = 2154;
    private static final int ID_CHECK_ZOMBIE_BOSS_FROZEN = 2155;
    private static final int ID_CHECK_ZOMBIE_BALLOON_BOOM = 2156;
    private static final int ID_CHECK_ZOMBIE_VACUUM = 2157;
    private static final int ID_ZOMBIE_VACUUM_COL = 2158;
    private static final int ID_ZOMBIE_OP = 2159;
    private static final int ID_ZOMBIE_APPLY = 2160;
    private static final int ID_FIELD_ROW = 2025;
    private static final int ID_FIELD_COL = 2026;
    private static final int ID_FIELD_SEED = 2027;
    private static final int ID_FIELD_PLANT = 2028;
    private static final int ID_FIELD_ZOMBIE = 2029;
    private static final int ID_FIELD_ZOMBIE_PLACE = 2038;
    private static final int ID_FIELD_ITEM = 2039;
    private static final int ID_FIELD_ITEM_PLACE = 2040;
    private static final int ID_FIELD_MUSIC = 2043;
    private static final int ID_FIELD_MUSIC_APPLY = 2044;
    private static final int ID_FIELD_CLEAR_GRAVES = 2045;
    private static final int ID_SLOT_COUNT = 2046;
    private static final int ID_SLOT_PICK = 2048;
    private static final int ID_SLOT_SEED = 2049;
    private static final int ID_SLOT_APPLY = 2050;
    private static final int ID_CARD_PICK = 2051;
    private static final int ID_CARD_ATTACK = 2052;
    private static final int ID_CARD_PRICE = 2053;
    private static final int ID_CARD_COOLDOWN = 2054;
    private static final int ID_SET_SUN = 2061;
    private static final int ID_CHECK_NO_SUN_DROPS = 2162;
    private static final int ID_CHECK_NO_COIN_DROPS = 2163;
    private static final int ID_CHECK_ANY_PLANTING = 2164;
    private static final int TRAINER_SLOT_COUNT_DIALOG_ID = 904;
    private static final int TRAINER_ATTACK_DIALOG_ID = 905;
    private static final int TRAINER_PRICE_DIALOG_ID = 906;
    private static final int TRAINER_COOLDOWN_DIALOG_ID = 907;
    private static final int TRAINER_SUN_DIALOG_ID = 908;
    private static final int ID_CHECK_HIDE_SEEDBANK = 2109;
    private static final int ID_CHECK_SHOW_SHOVEL = 2110;
    private static final int ID_CHECK_NO_COOLDOWN = 2112;
    private static final int ID_CHECK_HIDE_MENU_BUTTON = 2114;

    /** 暂停出怪的按住值：倒计时低于它时顶回（预告在 5 触发，600 永远到不了出怪点）。 */
    private static final int PAUSE_SPAWN_HOLD = 600;

    /** 配置文件：启动目录 config/ 下的 JSON（与游戏本体设置注册表同一文件夹）。 */
    private static final String CONFIG_RELATIVE_PATH = "config/pvz-trainer-mod.json";
    /** 展开修改器时自动暂停（modal 打开即暂停的既有机制开关）。 */
    private static boolean sAutoPauseOnOpen = true;
    /** 悬浮按钮始终置顶（关闭后会被选项之类的后开窗口覆盖层级）。 */
    private static boolean sAlwaysOnTop = true;
    /** 主菜单 X+C 组合键的上次触发时间（防按键自动重复连发）。 */
    private static long sLastComboTriggerMs;
    /** 场地页的当前选择（行/列 1 起，种子/僵尸/物品/场景/音乐为引擎常量或序号）。 */
    private static int sFieldRow = 1;
    private static int sFieldCol = 1;
    private static int sFieldSeed;
    private static int sFieldZombie;
    private static int sFieldItem;
    private static int sFieldTune = 1;
    /** 关卡页的倍速（1-10）：每帧在正常 tick 之外额外推进 updateGame（SPEED 模式同款）。 */
    private static int sSpeedMult = 1;
    /** 种子/僵尸的可用 id 与名字（首次使用时按定义表构建）。 */
    private static int[] sSeedIds;
    private static String[] sSeedNames;
    private static int[] sZombieIds;
    private static String[] sZombieNames;

    private static boolean infiniteSun;
    private static boolean freePlanting;
    private static boolean autoCollect;
    private static boolean pauseSpawning;
    /** 关卡页开关：屏蔽阳光/金币币种的掉落生成（Board.addCoin 注入过滤）。 */
    private static boolean noSunDrops;
    private static boolean noCoinDrops;
    /** 关卡页开关：任意种植——无视地形（花盆/莲叶/草皮/墓碑/弹坑/水面等）。 */
    private static boolean anyPlanting;
    /** 无限金币：逐 tick 把当前存档金币顶到 999990（unlockall 的同一数值）。 */
    private static boolean infiniteCoins;
    /** 无限消耗品（花园）：逐 tick 把对应数量顶到 100（unlockall 的同一数值）。 */
    private static boolean infiniteFertilizer;
    private static boolean infiniteBugSpray;
    private static boolean infiniteChocolate;
    private static boolean infiniteTreeFood;
    /** 卡槽页开关与选择（第一格序号 / 第一格种子 / 卡片定义改动的目标植物）。 */
    private static boolean noCooldown;
    private static int sSlotPickIndex;
    private static int sSlotSeed;
    private static int sCardSeed;
    /** 植物页开关：逐 tick 重置冷却/血量/睡眠与清除弹坑。 */
    private static boolean cobNoCooldown;
    private static boolean magnetNoCooldown;
    private static boolean potatoNoCooldown;
    private static boolean chomperNoCooldown;
    private static boolean plantInvincible;
    private static boolean plantFragile;
    private static boolean noCrater;
    private static boolean mushroomAwake;
    /** 场地页开关：取消迷雾绘制与花瓶内容透视（由 Board/GridItem 的 Mixin 应用）。 */
    private static boolean removeFog;
    private static boolean vaseXray;
    /** 特效页"特性复刻"九开关：相互独立，不触碰 mGameMode——逐帧特性（雨滴/闪电/
     * 种子掉落/双倍推进/锤子光标）由悬浮按钮 update 驱动，静态判定点（出怪缩小/
     * 整列种植/抛物线/墓碑生成/空降出怪/雨雾绘制/锤击命中）由注入点 OR 入开关。
     * 种子雨与暴风雨共用 Challenge.mChallengeStateCounter（上游同一暂存字段），二者互斥。 */
    private static boolean seedRain;
    private static boolean miniZombie;
    private static boolean columnPlant;
    private static boolean zombieSpeed;
    private static boolean whackHammer;
    private static boolean newtonGravity;
    private static boolean graveDanger;
    private static boolean stormyNight;
    private static boolean bungeeBlitz;
    /** 僵尸页的 15 个开关（多数由 TICK 每帧驱动，两个判定类由注入点读取）。 */
    private static boolean zombieExplode;
    private static boolean zombieNeverExplode;
    private static boolean zombieNoChill;
    private static boolean zombieNoButter;
    private static boolean zombieHide;
    private static boolean zombieShow;
    private static boolean zombieNoDancers;
    private static boolean zombieNoIceTrail;
    private static boolean zombieInvincible;
    private static boolean zombieWeak;
    private static boolean zombieNoImpThrow;
    private static boolean zombieGargEdible;
    private static boolean zombieFrozen;
    private static boolean zombieBossFrozen;
    private static boolean zombieBalloonBoom;
    private static boolean zombieVacuum;
    /** 吸怪目标列（1-9）。 */
    private static int sVacuumCol = 1;
    /** 批量操作的当前选择（ZOMBIE_OP_NAMES 序号）。 */
    private static int sZombieOpIndex;

    /** 僵尸页各开关（ZombieBehaviourA/ZombieBehaviourB 注入点读取）。 */
    public static boolean zombieNeverExplodeEnabled() {
        return zombieNeverExplode;
    }

    public static boolean zombieNoDancersEnabled() {
        return zombieNoDancers;
    }

    public static boolean zombieNoImpThrowEnabled() {
        return zombieNoImpThrow;
    }

    public static boolean zombieGargEdibleEnabled() {
        return zombieGargEdible;
    }

    public static boolean zombieInvincibleEnabled() {
        return zombieInvincible;
    }

    /** 僵尸页"僵尸不动"开关（TrainerZombieMixin 的 isImmobilizied 注入读取）。 */
    public static boolean zombieFrozenEnabled() {
        return zombieFrozen;
    }

    /** 僵尸页"隐形切换"开关（隐形战争关 = 现形；TrainerZombieMixin 的 draw 注入读取）。 */
    /** 僵尸页"僵尸现形"开关（隐形战争关的绘制判定注入读取）。 */
    public static boolean zombieShowEnabled() {
        return zombieShow;
    }

    /** 批量操作名（与 sZombieOpIndex 同序）。 */
    private static final String[] ZOMBIE_OP_NAMES =
            {"移除", "迷惑", "冻结", "减速", "黄油", "吹走", "变黑", "变大", "变小"};

    /** 挑战模式的本地化名（定义表名字键优先过 lawnstrings，缺键回退原始键名——
     *  limbo 页的键在部分资源包里没有收录，不显示 &lt;Missing …&gt;）。 */
    private static String challengeName(int theMode) {
        var aDef = cn.enaium.fabricpvz.portable.lawn.Challenge.getChallengeDefinition(theMode);
        return cn.enaium.fabricpvz.portable.pvzp.PvzpStringFile.tryTranslate(aDef.mChallengeName)
                .orElse(aDef.mChallengeName.replace("[", "").replace("]", ""));
    }

    /** 批量操作：对全场活僵尸执行所选操作，返回执行数量。 */
    private static int applyZombieOperation(Board theBoard, int theOp) {
        int aCount = 0;
        // 迷惑/冻结/变大/变小不作用于僵王（boss 行为状态机不支持这些状态）
        boolean aSkipBoss = theOp == 1 || theOp == 2 || theOp == 7 || theOp == 8;
        for (Zombie aZombie : theBoard.mZombies) {
            if (aZombie.mDead) {
                continue;
            }
            if (aSkipBoss && aZombie.mZombieType
                    == cn.enaium.fabricpvz.portable.lawn.ZombieType.ZOMBIE_BOSS) {
                continue;
            }
            switch (theOp) {
                case 0 -> aZombie.takeDamage(KILL_DAMAGE, KILL_FLAGS); // 移除：正常致死留尸体
                case 1 -> {
                    if (!aZombie.isDeadOrDying()) {
                        aZombie.startMindControlled(); // 迷惑：mindcontrol 同款
                    }
                }
                case 2 -> {
                    if (!aZombie.isDeadOrDying() && !aZombie.mMindControlled) {
                        aZombie.mIceTrapCounter = 300; // 冻结：冰封定身（排除已迷惑的）
                        aZombie.applyChill(true);
                    }
                }
                case 3 -> {
                    if (!aZombie.isDeadOrDying()) {
                        aZombie.applyChill(false); // 减速
                    }
                }
                case 4 -> {
                    if (!aZombie.isDeadOrDying()) {
                        aZombie.applyButter(); // 黄油：applyButter 定身（黄油贴图照常）
                    }
                }
                case 5 -> {
                    if (!aZombie.isDeadOrDying()) {
                        aZombie.mBlowingAway = true; // 吹走：mBlowingAway 推到 850 后 dieWithLoot
                    }
                }
                case 6 -> {
                    if (!aZombie.isDeadOrDying()) {
                        aZombie.applyBurn(); // 变黑：火焰致死（烧焦动画）
                    }
                }
                case 7 -> {
                    if (!aZombie.isDeadOrDying()) {
                        aZombie.mScaleZombie = Math.min(3.0f, aZombie.mScaleZombie * 1.5f); // 变大
                    }
                }
                case 8 -> {
                    if (!aZombie.isDeadOrDying()) {
                        aZombie.mScaleZombie = Math.max(0.3f, aZombie.mScaleZombie / 1.5f); // 变小
                    }
                }
                default -> {
                }
            }
            aCount++;
        }
        return aCount;
    }
    /** 各逐帧特性已初始化/维护的棋盘：换关卡重放初始化，开关关闭时还原。 */
    private static Board sStormBoard;
    private static Board sSeedRainBoard;
    private static Board sWhackBoard;

    /** 场地页"去除浓雾"开关（TrainerBoardMixin 读取）。 */
    public static boolean removeFogEnabled() {
        return removeFog;
    }

    /** 场地页"花瓶透视"开关（TrainerGridItemMixin 读取）。 */
    public static boolean vaseXrayEnabled() {
        return vaseXray;
    }

    /** 关卡页"暂停出怪"开关（TrainerChallengeMixin 的打僵尸出怪注入读取）。 */
    public static boolean pauseSpawningEnabled() {
        return pauseSpawning;
    }

    /**
     * 是否在修改器排除页（悬浮按钮隐藏、展开的修改器窗口自动收起）：
     * 加载画面、戴夫商店、图鉴、制作人员场景（清单页与 MV 同属
     * CreditScreen）、信件页（AwardScreen 的两种僵尸信类型）与三选关页
     * （迷你游戏/益智模式/生存模式的关卡列表 ChallengeScreen）。
     */
    private static boolean isOnExcludedPage() {
        if (sApp == null) {
            return false;
        }
        boolean aLetterPage = sApp.mAwardScreen != null
                && (sApp.mAwardScreen.mAwardType
                        == cn.enaium.fabricpvz.portable.lawn.AwardType.AWARD_CREDITS_ZOMBIENOTE
                || sApp.mAwardScreen.mAwardType
                        == cn.enaium.fabricpvz.portable.lawn.AwardType.AWARD_HELP_ZOMBIENOTE);
        return sApp.mGameScene == GameScenes.SCENE_LOADING
                || sApp.getDialog(cn.enaium.fabricpvz.portable.lawn.Dialogs.DIALOG_STORE) != null
                || sApp.getDialog(cn.enaium.fabricpvz.portable.lawn.Dialogs.DIALOG_ALMANAC) != null
                || sApp.mCreditScreen != null
                || aLetterPage
                || sApp.mChallengeScreen != null;
    }

    /** 关卡页"不掉阳光/不掉金币"开关（TrainerBoardCoinMixin 读取）。 */
    public static boolean noSunDropsEnabled() {
        return noSunDrops;
    }

    public static boolean noCoinDropsEnabled() {
        return noCoinDrops;
    }

    /** 关卡页"任意种植"开关（TrainerBoardPlantMixin 读取）。 */
    public static boolean anyPlantingEnabled() {
        return anyPlanting;
    }

    // 特效页九个特性复刻开关（各 Mixin 注入点与每帧驱动读取）
    public static boolean seedRainEnabled() {
        return seedRain;
    }

    public static boolean miniZombieEnabled() {
        return miniZombie;
    }

    public static boolean columnPlantEnabled() {
        return columnPlant;
    }

    public static boolean zombieSpeedEnabled() {
        return zombieSpeed;
    }

    public static boolean whackHammerEnabled() {
        return whackHammer;
    }

    public static boolean newtonGravityEnabled() {
        return newtonGravity;
    }

    public static boolean graveDangerEnabled() {
        return graveDanger;
    }

    public static boolean stormyNightEnabled() {
        return stormyNight;
    }

    public static boolean bungeeBlitzEnabled() {
        return bungeeBlitz;
    }

    /** 打僵尸开关的锤子光标装配（Challenge.startLevel 的 whack 分支同款）。 */
    private static void setupWhackHammer(Board theBoard) {
        theBoard.mCursorObject.mCursorType =
                cn.enaium.fabricpvz.portable.lawn.CursorType.CURSOR_TYPE_HAMMER;
        cn.enaium.fabricpvz.portable.pvzp.reanim.Reanimator.reanimatorEnsureDefinitionLoaded(
                cn.enaium.fabricpvz.portable.lawn.ReanimationType.REANIM_HAMMER, true);
        sApp.removeReanimation(theBoard.mCursorObject.mReanimCursorID);
        var aHammer = sApp.addReanimation(-25.0f, 16.0f, 0,
                cn.enaium.fabricpvz.portable.lawn.ReanimationType.REANIM_HAMMER);
        aHammer.mIsAttachment = true;
        aHammer.playReanim("anim_whack_zombie",
                cn.enaium.fabricpvz.portable.lawn.ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 0, 24.0f);
        aHammer.mAnimTime = 1.0f;
        theBoard.mCursorObject.mReanimCursorID = sApp.reanimationGetID(aHammer);
    }

    private static cn.enaium.fabricpvz.portable.lawn.LawnApp sApp;
    private static Board sBoard;
    /** 悬浮按钮；游戏初始化完成即挂进 WidgetManager，常驻所有界面。 */
    private static TrainerButton sButton;
    /** 对话框打开中（抑制悬浮按钮显示与拖拽歧义）。 */
    private static boolean sDialogOpen;
    /** 免费种植/自动收集已同步进哪个棋盘——换关卡即重新推送一次。 */
    private static Board sLastSyncedBoard;
    /** 单次会话内记住上次打开的标签页与每页各自的滚动位置（关闭时写回）。 */
    private static int sLastTab;
    private static final int[] sTabScrollOffsets = new int[TrainerDialog.PAGE_NAMES.length];
    /** 单次会话内记住修改器对话框收起时的位置（下次展开原地打开）。 */
    private static int sLastDialogX;
    private static int sLastDialogY;
    private static boolean sDialogPositionSaved;
    /** 展开修改器前管理器的焦点（关闭时由 PreModalInfo 恢复到它）。 */
    private static cn.enaium.fabricpvz.portable.sexy.Widget sPrevFocus;

    @Override
    public void onInitialize() {
        LOGGER.info("pvz-trainer-mod 已加载：常驻悬浮按钮（可拖动），点击展开原生对话框");
        loadConfig();
        // 资源装载完成后立即挂按钮（mWidgetManager 在 SexyAppBase 构造器里创建，此时已就绪）
        cn.enaium.fabricpvz.api.lifecycle.GameLifecycleEvents.INITIALIZED.register(TrainerMod::createButton);
        LawnEvents.TICK.register(TrainerMod::onTick);
    }

    // -------------------------------------------------------------------------------------
    // 配置（config/pvz-trainer-mod.json）与配置入口
    // -------------------------------------------------------------------------------------

    /** 读取配置；缺失或解析失败用默认值（两个开关都是开）。 */
    private static void loadConfig() {
        try {
            String aJson = java.nio.file.Files.readString(
                    java.nio.file.Paths.get(cn.enaium.fabricpvz.portable.sexy.Common
                            .getResourcePath(CONFIG_RELATIVE_PATH)));
            sAutoPauseOnOpen = !aJson.matches("(?s).*\"autoPauseOnOpen\"\\s*:\\s*false.*");
            sAlwaysOnTop = !aJson.matches("(?s).*\"alwaysOnTop\"\\s*:\\s*false.*");
        } catch (IOException | RuntimeException ignored) {
            // 文件缺失/非法：保持默认值
        }
    }

    /** 保存配置（config 文件夹缺失时自动创建）。 */
    private static void saveConfig() {
        try {
            java.nio.file.Path aPath = java.nio.file.Paths.get(
                    cn.enaium.fabricpvz.portable.sexy.Common.getResourcePath(CONFIG_RELATIVE_PATH));
            if (aPath.getParent() != null) {
                java.nio.file.Files.createDirectories(aPath.getParent());
            }
            java.nio.file.Files.writeString(aPath, "{\n"
                    + "  \"autoPauseOnOpen\": " + sAutoPauseOnOpen + ",\n"
                    + "  \"alwaysOnTop\": " + sAlwaysOnTop + "\n}\n");
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("保存配置失败", e);
        }
    }

    /** 打开配置对话框（主菜单 X+C 与 ModMenu 的"配置"按钮共用入口）。 */
    static void openConfigDialog() {
        if (sApp == null || sApp.getDialog(TRAINER_CONFIG_DIALOG_ID) != null) {
            return;
        }
        sApp.addDialog(TRAINER_CONFIG_DIALOG_ID, new TrainerConfigDialog());
    }

    /** 主菜单 X+C 组合键（GameSelector.keyDown 经 Mixin 转发到这里）。 */
    public static void onGameSelectorKey(int theKey) {
        if (sApp == null || sApp.mGameScene != GameScenes.SCENE_MENU) {
            return;
        }
        if (theKey != 'X' && theKey != 'C') {
            return; // 字母键按 SdlKeys 的映射即 ASCII 大写
        }
        WidgetManager aManager = sApp.mWidgetManager;
        if (aManager == null) {
            return;
        }
        boolean aXHeld = aManager.mKeyDown['X'];
        boolean aCHeld = aManager.mKeyDown['C'];
        if (!aXHeld || !aCHeld) {
            return;
        }
        long aNow = System.currentTimeMillis();
        if (aNow - sLastComboTriggerMs < 800) {
            return; // 按住期间的自动重复不连发
        }
        sLastComboTriggerMs = aNow;
        openConfigDialog();
    }

    private static void createButton() {
        if (sButton != null) {
            return;
        }
        if (sApp == null) {
            sApp = cn.enaium.fabricpvz.portable.lawn.LawnApp.gLawnApp; // 游戏公开静态实例
        }
        if (sApp == null) {
            return;
        }
        WidgetManager aManager = sApp.mWidgetManager;
        if (aManager == null) {
            return;
        }
        // INITIALIZED 时 LoadingImages 组尚未装载（渐进式装载），此时不能量贴图尺寸——
        // 先挂零尺寸空按钮，update() 里等贴图就绪再定型
        sButton = new TrainerButton();
        aManager.addWidget(sButton);
    }

    private static void onTick(Board theBoard, int theTick) {
        sBoard = theBoard;
        sApp = theBoard.mApp;
        if (sButton == null) {
            createButton(); // INITIALIZED 未及创建时的兜底
        }
        boolean aPlaying = sApp.mGameScene == GameScenes.SCENE_PLAYING;

        // 免费种植/自动收集与关卡内的隐码（cheatplant/lazyclick）操作同一批标志位：
        // 新关卡开始时把菜单里预设的开关推送进去，此后每 tick 回读，双向保持一致
        if (aPlaying) {
            if (theBoard != sLastSyncedBoard) {
                sLastSyncedBoard = theBoard;
                theBoard.mApp.mEasyPlantingCheat = freePlanting;
                theBoard.mApp.mAutoCollectCheat = autoCollect;
            } else {
                freePlanting = theBoard.mApp.mEasyPlantingCheat;
                autoCollect = theBoard.mApp.mAutoCollectCheat;
            }

            // 僵尸页开关的每帧驱动（TICK 每 updateGame 恰一次，倍速下按倍数生效）
            boolean aInvisiLevel = sApp.mGameMode
                    == cn.enaium.fabricpvz.portable.lawn.GameMode.GAMEMODE_CHALLENGE_INVISIGHOUL;
            float aVacuumX = zombieVacuum
                    ? theBoard.gridToPixelX(sVacuumCol - 1, 0) : 0.0f;
            for (Zombie aZombie : theBoard.mZombies) {
                if (aZombie.mDead) {
                    continue;
                }
                // "僵尸隐形"：仅非隐形战争关生效——勾选 mVisible=false 隐形，取消恢复；
                // 隐形战争关的隐形由 draw 的模式判定实现，不受 mVisible 影响
                if (!aInvisiLevel) {
                    aZombie.mVisible = !zombieHide;
                }
                // 屏蔽寒冰减速：清零后必须重算动画速率——减速是动画速率减半（移动由
                // 动画驱动），直接赋 0 绕过了"递减到 0 才调 updateAnimSpeed"的恢复路径
                if (zombieNoChill && aZombie.mChilledCounter > 0) {
                    aZombie.mChilledCounter = 0;
                    aZombie.updateAnimSpeed();
                }
                // 屏蔽黄油：同理要调 removeButter（黄油贴图与定身一并解除）
                if (zombieNoButter && aZombie.mButteredCounter > 0) {
                    aZombie.mButteredCounter = 0;
                    aZombie.removeButter();
                }
                if (zombieNoIceTrail) {
                    theBoard.mIceMinX[aZombie.mRow] = cn.enaium.fabricpvz.portable.lawn.GameConstants.BOARD_ICE_START;
                    theBoard.mIceTimer[aZombie.mRow] = 0;
                }
                if (zombieWeak && !aZombie.isDeadOrDying()) {
                    aZombie.mBodyHealth = Math.min(aZombie.mBodyHealth, 1);
                    aZombie.mHelmHealth = Math.min(aZombie.mHelmHealth, 1);
                    aZombie.mShieldHealth = Math.min(aZombie.mShieldHealth, 1);
                    aZombie.mFlyingHealth = Math.min(aZombie.mFlyingHealth, 1);
                }
                if (zombieBossFrozen && aZombie.mZombieType == cn.enaium.fabricpvz.portable.lawn.ZombieType.ZOMBIE_BOSS
                        && aZombie.mZombiePhase == cn.enaium.fabricpvz.portable.lawn.ZombiePhase.PHASE_BOSS_IDLE
                        && aZombie.mPhaseCounter > 0) {
                    aZombie.mPhaseCounter = Math.max(aZombie.mPhaseCounter, 100); // 顶住待机计时，不进入攻击
                }
                if (zombieBalloonBoom && !aZombie.isDeadOrDying()
                        && aZombie.mZombiePhase == cn.enaium.fabricpvz.portable.lawn.ZombiePhase.PHASE_BALLOON_FLYING) {
                    aZombie.takeDamage(KILL_DAMAGE, KILL_FLAGS); // 飞行伤害打爆气球，余伤落本体
                }
                if (zombieVacuum && !aZombie.isDeadOrDying()
                        && aZombie.mZombieType != cn.enaium.fabricpvz.portable.lawn.ZombieType.ZOMBIE_BOSS) {
                    float aDelta = aVacuumX - aZombie.mPosX;
                    aZombie.mPosX += Math.max(-8.0f, Math.min(8.0f, aDelta)); // 每帧最多吸 8px
                }
            }
            if (zombieExplode) {
                for (Zombie aZombie : theBoard.mZombies) {
                    if (aZombie.mDead
                            || aZombie.mZombieType != cn.enaium.fabricpvz.portable.lawn.ZombieType.ZOMBIE_JACK_IN_THE_BOX
                            || aZombie.mZombiePhase != cn.enaium.fabricpvz.portable.lawn.ZombiePhase.PHASE_JACK_IN_THE_BOX_RUNNING) {
                        continue;
                    }
                    aZombie.mPhaseCounter = 0; // 立即进入爆箱节奏
                }
            }
        }

        // 持续生效项：逐 tick 强制写回
        if (infiniteSun && aPlaying) {
            theBoard.mSunMoney = INFINITE_SUN_VALUE;
        }
        // 暂停出怪：出怪倒计时低于按住值时顶回，冻结在出怪点之前
        // （只压低、不抬高——开局的长倒计时与自然快进照常，解开后从按住值继续）
        if (pauseSpawning && aPlaying && theBoard.mZombieCountDown < PAUSE_SPAWN_HOLD) {
            theBoard.mZombieCountDown = PAUSE_SPAWN_HOLD;
        }
        // 无限金币：逐 tick 把当前存档的金币顶到 999990（unlockall 的同一数值）
        if (infiniteCoins && aPlaying && theBoard.mApp.mPlayerInfo != null) {
            theBoard.mApp.mPlayerInfo.mCoins = 999990;
        }
        // 无限消耗品（花园）：逐 tick 把对应数量顶到 100（unlockall 的同一数值）
        PlayerInfo aPlayer = theBoard.mApp.mPlayerInfo;
        if (aPlayer != null) {
            if (infiniteFertilizer) {
                aPlayer.mPurchases[StoreItem.STORE_ITEM_FERTILIZER] = PlayerInfo.PURCHASE_COUNT_OFFSET + 100;
            }
            if (infiniteBugSpray) {
                aPlayer.mPurchases[StoreItem.STORE_ITEM_BUG_SPRAY] = PlayerInfo.PURCHASE_COUNT_OFFSET + 100;
            }
            if (infiniteChocolate) {
                aPlayer.mPurchases[StoreItem.STORE_ITEM_CHOCOLATE] = PlayerInfo.PURCHASE_COUNT_OFFSET + 100;
            }
            if (infiniteTreeFood) {
                aPlayer.mPurchases[StoreItem.STORE_ITEM_TREE_FOOD] = PlayerInfo.PURCHASE_COUNT_OFFSET + 100;
            }
        }
        // 卡片无冷却：拾起种子时卡片即被 deactivate（阳光路径），保持全部卡槽
        // 恒为可用（mActive=true、无刷新状态）——种下后也一样，不会进入冷却
        if (noCooldown && aPlaying) {
            for (SeedPacket aPacket : theBoard.mSeedBank.mSeedPackets) {
                if (aPacket != null && aPacket.mPacketType != cn.enaium.fabricpvz.portable.lawn.SeedType.SEED_NONE
                        && !aPacket.mActive) {
                    aPacket.mActive = true;
                    aPacket.mRefreshing = false;
                    aPacket.mRefreshCounter = 0;
                }
            }
        }
        // 植物页：逐 tick 重置冷却状态机 / 血量 / 睡眠与清除弹坑
        if (aPlaying) {
            boolean aInvincible = plantInvincible && !plantFragile;
            boolean aFragile = plantFragile;
            if (aInvincible || aFragile || mushroomAwake || cobNoCooldown || magnetNoCooldown
                    || potatoNoCooldown || chomperNoCooldown) {
                for (Plant aPlant : theBoard.mPlants) {
                    if (aPlant.mDead) {
                        continue;
                    }
                    if (aInvincible) {
                        aPlant.mPlantHealth = 4000;
                    } else if (aFragile) {
                        aPlant.mPlantHealth = 1;
                    }
                    if (mushroomAwake) {
                        aPlant.mIsAsleep = false;
                    }
                    if (cobNoCooldown && aPlant.mSeedType == SeedType.SEED_COBCANNON
                            && (aPlant.mState == PlantState.STATE_COBCANNON_ARMING
                                || aPlant.mState == PlantState.STATE_COBCANNON_LOADING)) {
                        aPlant.mStateCountdown = 0;
                    }
                    if (magnetNoCooldown && aPlant.mSeedType == SeedType.SEED_MAGNETSHROOM
                            && aPlant.mState == PlantState.STATE_MAGNETSHROOM_CHARGING) {
                        aPlant.mStateCountdown = 0;
                    }
                    if (potatoNoCooldown && aPlant.mSeedType == SeedType.SEED_POTATOMINE
                            && aPlant.mState != PlantState.STATE_POTATO_ARMED) {
                        aPlant.mStateCountdown = 0;
                    }
                    if (chomperNoCooldown && aPlant.mSeedType == SeedType.SEED_CHOMPER
                            && (aPlant.mState == PlantState.STATE_CHOMPER_BITING
                                || aPlant.mState == PlantState.STATE_CHOMPER_BITING_GOT_ONE
                                || aPlant.mState == PlantState.STATE_CHOMPER_BITING_MISSED
                                || aPlant.mState == PlantState.STATE_CHOMPER_DIGESTING
                                || aPlant.mState == PlantState.STATE_CHOMPER_SWALLOWING)) {
                        aPlant.mStateCountdown = 0;
                    }
                }
            }
            // 不留弹坑：出现即清除（fillhoe 的同款清除路径）
            if (noCrater) {
                for (var aGridItem : theBoard.mGridItems) {
                    if (!aGridItem.mDead
                            && aGridItem.mGridItemType == GridItemType.GRIDITEM_CRATER) {
                        aGridItem.gridItemDie();
                    }
                }
            }
        }
    }

    /** 原生石钮贴图的高度——按钮控件必须与此同高，否则绘制被 clipRect 裁掉底边。未装载时 0。 */
    private static int stoneArtHeight() {
        Image aLeft = cn.enaium.fabricpvz.portable.lawn.Resources
                .getImageById(cn.enaium.fabricpvz.portable.lawn.ImageID.IMAGE_BUTTON_LEFT);
        return aLeft != null ? aLeft.getHeight() : 0;
    }

    /** 左 + N×中 + 右 的整块石钮宽度——drawStoneButton 按贴图原生尺寸拼接，宽了/窄了都会裁边。未装载时 0。 */
    private static int stoneButtonWidth(int theMiddleRepeats) {
        Image aLeft = cn.enaium.fabricpvz.portable.lawn.Resources
                .getImageById(cn.enaium.fabricpvz.portable.lawn.ImageID.IMAGE_BUTTON_LEFT);
        Image aMiddle = cn.enaium.fabricpvz.portable.lawn.Resources
                .getImageById(cn.enaium.fabricpvz.portable.lawn.ImageID.IMAGE_BUTTON_MIDDLE);
        Image aRight = cn.enaium.fabricpvz.portable.lawn.Resources
                .getImageById(cn.enaium.fabricpvz.portable.lawn.ImageID.IMAGE_BUTTON_RIGHT);
        if (aLeft == null || aMiddle == null || aRight == null) {
            return 0;
        }
        return aLeft.getWidth() + aRight.getWidth() + theMiddleRepeats * aMiddle.getWidth();
    }

    /** 不超过 theMaxWidth 的最大"左帽+N 中块+右帽"宽度（整数个中块，石钮拼接不留空）。 */
    private static int stoneSnappedWidth(int theMaxWidth) {
        Image aLeft = cn.enaium.fabricpvz.portable.lawn.Resources
                .getImageById(cn.enaium.fabricpvz.portable.lawn.ImageID.IMAGE_BUTTON_LEFT);
        Image aMiddle = cn.enaium.fabricpvz.portable.lawn.Resources
                .getImageById(cn.enaium.fabricpvz.portable.lawn.ImageID.IMAGE_BUTTON_MIDDLE);
        Image aRight = cn.enaium.fabricpvz.portable.lawn.Resources
                .getImageById(cn.enaium.fabricpvz.portable.lawn.ImageID.IMAGE_BUTTON_RIGHT);
        if (aLeft == null || aMiddle == null || aRight == null) {
            return theMaxWidth;
        }
        int aCaps = aLeft.getWidth() + aRight.getWidth();
        int aRepeats = Math.max(2, (theMaxWidth - aCaps) / aMiddle.getWidth());
        return aCaps + aRepeats * aMiddle.getWidth();
    }

    /** 预拼的一条完整石钮（left+1×middle+right），整条缩放绘制用；未装载或拼失败时 null。 */
    private static Image sStoneStrip;

    private static Image stoneStrip() {
        if (sStoneStrip != null) {
            return sStoneStrip;
        }
        Image aLeft = cn.enaium.fabricpvz.portable.lawn.Resources
                .getImageById(cn.enaium.fabricpvz.portable.lawn.ImageID.IMAGE_BUTTON_LEFT);
        Image aMiddle = cn.enaium.fabricpvz.portable.lawn.Resources
                .getImageById(cn.enaium.fabricpvz.portable.lawn.ImageID.IMAGE_BUTTON_MIDDLE);
        Image aRight = cn.enaium.fabricpvz.portable.lawn.Resources
                .getImageById(cn.enaium.fabricpvz.portable.lawn.ImageID.IMAGE_BUTTON_RIGHT);
        if (aLeft == null || aMiddle == null || aRight == null) {
            return null;
        }
        try {
            cn.enaium.fabricpvz.portable.sexy.graphics.MemoryImage anImage =
                    new cn.enaium.fabricpvz.portable.sexy.graphics.MemoryImage();
            anImage.create(aLeft.getWidth() + aMiddle.getWidth() + aRight.getWidth(),
                    aLeft.getHeight());
            Graphics aG = new Graphics(anImage);
            aG.drawImage(aLeft, 0, 0);
            aG.drawImage(aMiddle, aLeft.getWidth(), 0);
            aG.drawImage(aRight, aLeft.getWidth() + aMiddle.getWidth(), 0);
            sStoneStrip = anImage;
        } catch (RuntimeException e) {
            LOGGER.warn("石钮条拼接失败", e);
        }
        return sStoneStrip;
    }

    private static void show(Board theBoard, String theMessage) {
        LOGGER.info(theMessage);
        // 字幕只打到进行中的棋盘上；菜单里只记日志（sBoard 可能是已退出的陈旧棋盘）
        if (theBoard == null || sApp == null || sApp.mGameScene != GameScenes.SCENE_PLAYING) {
            return;
        }
        // 不带方括号的字符串不经翻译，原样显示——无需语言键
        theBoard.displayAdvice(theMessage, MessageStyle.MESSAGE_STYLE_BIG_MIDDLE_FAST,
                AdviceType.ADVICE_NONE);
    }

    private static String onOff(boolean theOn) {
        return theOn ? "开" : "关";
    }

    private static void killAllZombies(Board theBoard) {
        int aKilled = 0;
        for (Zombie aZombie : theBoard.mZombies) {
            if (!aZombie.mDead) {
                aZombie.takeDamage(KILL_DAMAGE, KILL_FLAGS); // 正常致死路径，播放死亡动画
                aKilled++;
            }
        }
        show(theBoard, "秒杀全场僵尸 ×" + aKilled);
    }

    /**
     * 立即进入下一波：把出怪倒计时压到 5（走 5→0 的自然节拍，旗波会先弹"一大波"预告再出怪）；
     * 最后一波已出完（生存模式等待收尾）时不动作。
     */
    private static void spawnNextWave(Board theBoard) {
        if (theBoard.mCurrentWave >= theBoard.mNumWaves) {
            show(theBoard, "已无更多波次");
            return;
        }
        theBoard.mZombieCountDown = Math.min(theBoard.mZombieCountDown, 5);
        show(theBoard, "下一波来了！");
    }

    /**
     * 推进关卡进程：连续出怪直到刷完下一面旗（旗号 % 旗距 == 旗距−1 的波，与
     * BoardWaves.isFlagWave 同规则），每一波都走游戏自己的 {@link Board#spawnZombieWave}
     * 并复刻出怪后的记账（见 {@link #spawnWaveWithBookkeeping}）；全部波次已出完时不动作。
     * 旗距通常 10，一次最多连刷 9 个普通波 + 1 个旗波。
     */
    private static void advanceToNextFlag(Board theBoard) {
        if (theBoard.mCurrentWave >= theBoard.mNumWaves) {
            show(theBoard, "已无更多波次");
            return;
        }
        int aSpawned = 0;
        while (theBoard.mCurrentWave < theBoard.mNumWaves) {
            int aWaveNumber = theBoard.mCurrentWave; // 本轮要出的这一波
            boolean aWaveIsFlag = isFlagWave(theBoard, aWaveNumber);
            spawnWaveWithBookkeeping(theBoard, aWaveNumber);
            aSpawned++;
            if (aWaveIsFlag) {
                break; // 旗波出完即停
            }
        }
        show(theBoard, "推进关卡：已刷新 " + aSpawned + " 波");
    }

    /** 直接出 {@code aJustSpawnedWave} 号波并复刻调度器出怪后的记账（BoardWaves.updateZombieSpawning 的 countdown==0 段同参数）。 */
    private static void spawnWaveWithBookkeeping(Board theBoard, int aJustSpawnedWave) {
        theBoard.mHugeWaveCountDown = 0; // "一大波"宣告的 750 倒数会挡住出怪，直接清掉
        theBoard.clearAdvice(AdviceType.ADVICE_HUGE_WAVE);
        theBoard.spawnZombieWave();

        theBoard.mZombieHealthWaveStart = theBoard.totalZombiesHealthInWave(aJustSpawnedWave);
        if (theBoard.mCurrentWave == theBoard.mNumWaves && theBoard.mApp.isSurvivalMode()) {
            theBoard.mZombieHealthToNextWave = 0;
            theBoard.mZombieCountDown = 5500; // ZOMBIE_COUNTDOWN_BEFORE_REPICK + 1
        } else if (isFlagWave(theBoard, aJustSpawnedWave)
                && !(theBoard.mApp.isWallnutBowlingLevel()
                    || theBoard.mApp.mGameMode == cn.enaium.fabricpvz.portable.lawn.GameMode.GAMEMODE_CHALLENGE_LAST_STAND)) {
            theBoard.mZombieHealthToNextWave = 0;
            theBoard.mZombieCountDown = 4500; // ZOMBIE_COUNTDOWN_BEFORE_FLAG
        } else {
            theBoard.mZombieHealthToNextWave =
                    (int) (0.55f * theBoard.mZombieHealthWaveStart); // randRangeFloat(0.5,0.65) 的中值
            theBoard.mZombieCountDown = 2500; // ZOMBIE_COUNTDOWN
        }
        theBoard.mZombieCountDownStart = theBoard.mZombieCountDown;
    }

    /** 旗波判定（BoardWaves.isFlagWave 的同款规则，包级私有故在此复刻）。 */
    private static boolean isFlagWave(Board theBoard, int theWaveNumber) {
        if (theBoard.mApp.isFirstTimeAdventureMode() && theBoard.mLevel == 1) {
            return false;
        }
        int aWavesPerFlag = (theBoard.mApp.isFirstTimeAdventureMode() && theBoard.mNumWaves < 10)
                ? theBoard.mNumWaves : 10;
        return theWaveNumber % aWavesPerFlag == aWavesPerFlag - 1;
    }

    /** 杀死全场植物（killplant 隐码的同款路径：每株植物走自己的 die，带各自的移除特效）。 */
    private static void killAllPlants(Board theBoard) {
        int aKilled = 0;
        for (Plant aPlant : theBoard.mPlants) {
            if (!aPlant.mDead) {
                aPlant.die();
                aKilled++;
            }
        }
        show(theBoard, "杀死全场植物 ×" + aKilled);
    }

    /** 清除全部小推车：直接置 mDead（不走 die() 的"奖励小推车滚入"补偿）。 */
    private static void clearLawnMowers(Board theBoard) {
        int aCleared = 0;
        for (LawnMower aMower : theBoard.mLawnMowers) {
            if (!aMower.mDead) {
                aMower.mDead = true;
                sApp.removeReanimation(aMower.mReanimID);
                aCleared++;
            }
        }
        show(theBoard, "清除全部小推车 ×" + aCleared);
    }

    /**
     * 恢复全部小推车（retrack 隐码的同款规则）：缺车的行补一辆新车，跳过从不放车的土路行
     * （Board::InitLawnMowers 的资格规则），新车从画面外滚入（LawnMower::Die 的奖励车流程）。
     */
    private static void restoreLawnMowers(Board theBoard) {
        boolean aRestored = false;
        for (int aRow = 0; aRow < Board.MAX_GRID_SIZE_Y; aRow++) {
            if (theBoard.mPlantRow[aRow] == PlantRowType.PLANTROW_DIRT) {
                continue;
            }
            boolean aHasMower = false;
            for (LawnMower aMower : theBoard.mLawnMowers) {
                if (!aMower.mDead && aMower.mRow == aRow) {
                    aHasMower = true;
                    break;
                }
            }
            if (!aHasMower) {
                LawnMower aMower = theBoard.mLawnMowers.dataArrayAlloc();
                aMower.lawnMowerInitialize(aRow);
                aMower.mMowerState = LawnMowerState.MOWER_ROLLING_IN;
                aRestored = true;
            }
        }
        if (aRestored) {
            sApp.playSample(SoundType.SOUND_ZAMBONI);
        }
        show(theBoard, aRestored ? "已恢复缺失的小推车" : "每行都有小推车");
    }

    /** 启动所有小推车（dotrack 隐码的同款路径：LawnMower::StartMower）。 */
    private static void startLawnMowers(Board theBoard) {
        int aStarted = 0;
        for (LawnMower aMower : theBoard.mLawnMowers) {
            if (!aMower.mDead && aMower.mMowerState == LawnMowerState.MOWER_READY) {
                aMower.startMower();
                aStarted++;
            }
        }
        show(theBoard, "启动小推车 ×" + aStarted);
    }

    /** 跳关器（F1 的同款入口：原版隐藏的"Enter New Level"对话框，栈上叠加打开）。 */
    private static void openSkipLevelDialog() {
        if (sApp.mBoard == null || sApp.mGameScene != GameScenes.SCENE_PLAYING) {
            return;
        }
        if (sApp.getDialog(Dialogs.DIALOG_CHEAT) != null) {
            return;
        }
        sApp.doCheatDialog();
    }

    // -------------------------------------------------------------------------------------
    // 存档页
    // -------------------------------------------------------------------------------------

    /**
     * 解锁金向日葵：金向日葵的判定是"冒险通关且三选关页奖杯 ≥48"（LawnApp.earnedGoldTrophy，
     * 奖杯来自挑战记录表而非成就）——所以对齐 unlockall 的挑战记录段：冒险标记二周目、
     * 全部非无尽挑战的记录置为满解锁值（只升不降），存档并刷新主菜单选关状态。
     */
    private static void unlockGoldSunflower() {
        var aPlayer = sApp.mPlayerInfo;
        if (aPlayer == null) {
            return;
        }
        aPlayer.mFinishedAdventure = Math.max(aPlayer.mFinishedAdventure, 2);
        for (int aMode = cn.enaium.fabricpvz.portable.lawn.GameMode.GAMEMODE_SURVIVAL_NORMAL_STAGE_1;
                aMode < cn.enaium.fabricpvz.portable.lawn.GameMode.NUM_GAME_MODES; aMode++) {
            int aIndex = aMode - cn.enaium.fabricpvz.portable.lawn.GameMode.GAMEMODE_SURVIVAL_NORMAL_STAGE_1;
            if (sApp.isSurvivalEndless(aMode) || sApp.isEndlessScaryPotter(aMode)
                    || sApp.isEndlessIZombie(aMode)) {
                continue;
            }
            if (aMode == cn.enaium.fabricpvz.portable.lawn.GameMode.GAMEMODE_TREE_OF_WISDOM) {
                // 智慧树高度住在自己的记录槽：只升不降
                aPlayer.mChallengeRecords[aIndex] =
                        Math.max(aPlayer.mChallengeRecords[aIndex], 500);
                continue;
            }
            int aUnlock = 1;
            if (sApp.isSurvivalNormal(aMode)) {
                aUnlock = cn.enaium.fabricpvz.portable.lawn.GameConstants.SURVIVAL_NORMAL_FLAGS;
            } else if (sApp.isSurvivalHard(aMode)) {
                aUnlock = cn.enaium.fabricpvz.portable.lawn.GameConstants.SURVIVAL_HARD_FLAGS;
            }
            aPlayer.mChallengeRecords[aIndex] =
                    Math.max(aPlayer.mChallengeRecords[aIndex], aUnlock);
        }
        sApp.mProfileMgr.save();
        refreshSelectorUnlocks();
        show(sBoard, "金向日葵已解锁");
    }

    /**
     * 临时解锁所有模式（tmpunlock 隐码的同款路径）：翻转选关器的可用性 cheat
     * （mUnlockSelectorCheat），重算选关状态后清掉三模式锁——仅主菜单可用。
     */
    private static void tmpUnlockAllModes() {
        GameSelector aSelector = sApp.mGameSelector;
        if (aSelector == null) {
            show(sBoard, "请在主菜单使用");
            return;
        }
        aSelector.mUnlockSelectorCheat = !aSelector.mUnlockSelectorCheat;
        aSelector.syncProfile(false);
        if (aSelector.mUnlockSelectorCheat) {
            // syncProfile 重算模式锁，之后清掉（上游选关器 'c' 调试键同款）
            aSelector.mMinigamesLocked = false;
            aSelector.mPuzzleLocked = false;
            aSelector.mSurvivalLocked = false;
        }
        aSelector.syncButtons();
        show(sBoard, "临时解锁所有模式：" + onOff(aSelector.mUnlockSelectorCheat));
    }

    /** 设置金币：写入当前存档并存档。 */
    private static void setCoins(int theCoins) {
        var aPlayer = sApp.mPlayerInfo;
        if (aPlayer == null) {
            return;
        }
        aPlayer.mCoins = Math.max(0, Math.min(9999999, theCoins));
        sApp.mProfileMgr.save();
        show(sBoard, "金币已设置为 " + aPlayer.mCoins);
    }

    /** "设置阳光"：直接写当前棋盘的阳光数（与无限阳光同一上限 9990）。 */
    private static void setSun(int theSun) {
        Board aBoard = sBoard;
        if (aBoard == null) {
            return;
        }
        aBoard.mSunMoney = Math.max(0, Math.min(9990, theSun));
        show(aBoard, "阳光已设置为 " + aBoard.mSunMoney);
    }

    /**
     * 刷新主菜单的选关解锁状态（选关器缓存显示状态，解锁后不重算的话按钮/奖杯要到
     * 离开菜单才更新——unlockall/tmpunlock 隐码的收尾同款）。
     */
    private static void refreshSelectorUnlocks() {
        GameSelector aSelector = sApp.mGameSelector;
        if (aSelector == null) {
            return;
        }
        aSelector.syncProfile(false);
        aSelector.syncButtons();
    }

    // -------------------------------------------------------------------------------------
    // 特效页
    // -------------------------------------------------------------------------------------

    /**
     * 切换原版打字隐码的视觉效果（与 BoardInput.doTypingCheck 同一判定与反馈路径）：
     * pinata/dance/daisies 有解锁门槛（canDo* 不满足时蜂鸣 + [CANT_USE_CODE] 提示），
     * mustache 附带成就，konami 只播彩蛋音效。
     */
    private static void toggleEffect(String theCheat) {
        Board aBoard = sBoard;
        if (aBoard == null) {
            show(sBoard, "请在关卡内使用");
            return;
        }
        switch (theCheat) {
            case "SUKHBIR" -> aBoard.setSukhbirMode(!aBoard.mSukhbirMode);
            case "FUTURE" -> aBoard.setFutureMode(!aBoard.mFutureMode);
            case "MUSTACHE" -> {
                aBoard.setMustacheMode(!aBoard.mMustacheMode);
                cn.enaium.fabricpvz.portable.lawn.widget.ReportAchievement.giveAchievement(sApp,
                        cn.enaium.fabricpvz.portable.lawn.AchievementId.MustacheMode, true);
            }
            case "TRICKEDOUT" -> aBoard.setSuperMowerMode(!aBoard.mSuperMowerMode);
            case "DAISIES" -> {
                if (sApp.canDoDaisyMode()) {
                    aBoard.setDaisyMode(!aBoard.mDaisyMode);
                } else {
                    aBoard.displayAdvice("[CANT_USE_CODE]", MessageStyle.MESSAGE_STYLE_BIG_MIDDLE_FAST,
                            AdviceType.ADVICE_NONE);
                    sApp.playSample(cn.enaium.fabricpvz.portable.lawn.Resources.SOUND_BUZZER_ID);
                }
            }
            case "DANCE" -> {
                if (sApp.canDoDanceMode()) {
                    aBoard.setDanceMode(!aBoard.mDanceMode);
                } else {
                    aBoard.displayAdvice("[CANT_USE_CODE]", MessageStyle.MESSAGE_STYLE_BIG_MIDDLE_FAST,
                            AdviceType.ADVICE_NONE);
                    sApp.playSample(cn.enaium.fabricpvz.portable.lawn.Resources.SOUND_BUZZER_ID);
                }
            }
            case "PINATA" -> {
                if (sApp.canDoPinataMode()) {
                    aBoard.setPinataMode(!aBoard.mPinataMode);
                } else {
                    aBoard.displayAdvice("[CANT_USE_CODE]", MessageStyle.MESSAGE_STYLE_BIG_MIDDLE_FAST,
                            AdviceType.ADVICE_NONE);
                    sApp.playSample(cn.enaium.fabricpvz.portable.lawn.Resources.SOUND_BUZZER_ID);
                }
            }
            case "KONAMI" -> sApp.playFoley(cn.enaium.fabricpvz.portable.pvzp.fx.FoleyType.FOLEY_DROP);
            default -> {
            }
        }
    }

    // -------------------------------------------------------------------------------------
    // 花园页
    // -------------------------------------------------------------------------------------

    /** 新增一株盆栽（花园调试键 m/+ 的同款路径）：花园满员则不添加；朝向随机。 */
    private static void addPottedPlant(boolean theMarigold, boolean theFullGrown) {
        if (sApp.mZenGarden == null || sBoard == null) {
            show(sBoard, "请在花园里使用");
            return;
        }
        if (sApp.mZenGarden.isZenGardenFull(true)) {
            show(sBoard, "当前花园已满");
            return;
        }
        PottedPlant aPottedPlant = new PottedPlant();
        if (theMarigold) {
            aPottedPlant.initializePottedPlant(cn.enaium.fabricpvz.portable.lawn.SeedType.SEED_MARIGOLD);
            // 金盏花随机颜色（白/橙/红/浅绿四色变体）
            aPottedPlant.mDrawVariation = cn.enaium.fabricpvz.portable.pvzp.PvzpCommon.randRangeInt(
                    DrawVariation.VARIATION_MARIGOLD_WHITE,
                    DrawVariation.VARIATION_MARIGOLD_LIGHT_GREEN);
        } else {
            aPottedPlant.initializePottedPlant(ZenGarden.pickRandomSeedType());
            if (theFullGrown) {
                aPottedPlant.mPlantAge = PottedPlantAge.PLANTAGE_FULL;
            }
        }
        sApp.mZenGarden.addPottedPlant(aPottedPlant);
        show(sBoard, theMarigold ? "已添加一株金盏花幼苗"
                : theFullGrown ? "已添加一株状态良好的大花" : "已添加一株随机幼苗");
    }

    /**
     * 设置智慧树高度：写入智慧树的挑战记录槽（只认 0-500 上限的由调用方钳制），并确保
     * 智慧树已购买解锁（mPurchases[STORE_ITEM_TREE_OF_WISDOM]）——不存在则自动解锁。
     */
    private static void setTreeHeight(int theHeight) {
        var aPlayer = sApp.mPlayerInfo;
        if (aPlayer == null) {
            return;
        }
        int aHeight = Math.max(0, Math.min(500, theHeight));
        aPlayer.mPurchases[StoreItem.STORE_ITEM_TREE_OF_WISDOM] =
                Math.max(aPlayer.mPurchases[StoreItem.STORE_ITEM_TREE_OF_WISDOM], 1);
        int anIndex = cn.enaium.fabricpvz.portable.lawn.GameMode.GAMEMODE_TREE_OF_WISDOM
                - cn.enaium.fabricpvz.portable.lawn.GameMode.GAMEMODE_SURVIVAL_NORMAL_STAGE_1;
        aPlayer.mChallengeRecords[anIndex] = aHeight;
        sApp.mProfileMgr.save();
        refreshSelectorUnlocks();
        show(sBoard, "智慧树高度已设置为 " + aHeight);
    }

    // -------------------------------------------------------------------------------------
    // 场地页
    // -------------------------------------------------------------------------------------

    /** 种子/僵尸的可用 id 与名字（按定义表构建，惰性缓存）。 */
    private static void buildSeedAndZombieLists() {
        if (sSeedIds != null) {
            return;
        }
        java.util.List<Integer> aSeedIds = new java.util.ArrayList<>();
        java.util.List<String> aSeedNames = new java.util.ArrayList<>();
        for (int i = 0; i < cn.enaium.fabricpvz.portable.lawn.SeedType.MAX_SEED_TYPES; i++) {
            if (cn.enaium.fabricpvz.portable.lawn.Plant.getPlantDefinition(i) != null) {
                aSeedIds.add(i);
                aSeedNames.add(cn.enaium.fabricpvz.portable.lawn.Plant.getNameString(i,
                        cn.enaium.fabricpvz.portable.lawn.SeedType.SEED_NONE));
            }
        }
        sSeedIds = aSeedIds.stream().mapToInt(Integer::intValue).toArray();
        sSeedNames = aSeedNames.toArray(new String[0]);

        java.util.List<Integer> aZombieIds = new java.util.ArrayList<>();
        java.util.List<String> aZombieNames = new java.util.ArrayList<>();
        for (int i = 0; i < cn.enaium.fabricpvz.portable.lawn.ZombieType.MAX_ZOMBIE_TYPES; i++) {
            var aDef = cn.enaium.fabricpvz.portable.lawn.Zombie.getZombieDefinition(i);
            if (aDef != null) {
                aZombieIds.add(i);
                // 与植物列表同源：定义表键名过 PvzpStringFile 翻译（优先 lawnstrings
                // 本地化，查不到回退代码内置英文名）
                aZombieNames.add(cn.enaium.fabricpvz.portable.pvzp.PvzpStringFile.translate(
                        "[" + aDef.mZombieName + "]"));
            }
        }
        sZombieIds = aZombieIds.stream().mapToInt(Integer::intValue).toArray();
        sZombieNames = aZombieNames.toArray(new String[0]);
    }

    private static String seedName(int theSeedId) {
        buildSeedAndZombieLists();
        for (int i = 0; i < sSeedIds.length; i++) {
            if (sSeedIds[i] == theSeedId) {
                return sSeedNames[i];
            }
        }
        return "种子 " + theSeedId;
    }

    /** 清空场上全部墓碑（gridItemDie 走各自的移除特效）。 */
    private static void clearGraveStones() {
        Board aBoard = sBoard;
        if (aBoard == null) {
            show(sBoard, "请在关卡内使用");
            return;
        }
        int aCleared = 0;
        for (var aGridItem : aBoard.mGridItems) {
            if (!aGridItem.mDead
                    && aGridItem.mGridItemType == cn.enaium.fabricpvz.portable.lawn.GridItemType.GRIDITEM_GRAVESTONE) {
                aGridItem.gridItemDie();
                aCleared++;
            }
        }
        show(sBoard, "已清空墓碑 ×" + aCleared);
    }

    /**
     * 直接过关（wingame 隐码的同款流程，BoardInput.winGameCheat 的复刻）：把棋盘推进到
     * 自然胜利的同一状态——波次制关卡（生存/绝对末日/无尽我是僵尸/谜题中期）清场后走
     * COIN_NONE + FadeOutLevel；其余关卡在场地中央刷出本关的奖励并立即收取，走奖币的
     * 发散与过关淡出仪式。
     */
    private static void winLevel(Board theBoard) {
        if (theBoard.hasLevelAwardDropped()) {
            return;
        }
        theBoard.mApp.mBoardResult = cn.enaium.fabricpvz.portable.lawn.BoardResult.BOARDRESULT_WON;

        if (theBoard.isSurvivalStageWithRepick() || theBoard.isLastStandStageWithRepick()
                || cn.enaium.fabricpvz.portable.lawn.LawnApp.isEndlessIZombie(theBoard.mApp.mGameMode)
                || (theBoard.mApp.isScaryPotterLevel() && !theBoard.isFinalScaryPotterStage())) {
            theBoard.mCurrentWave = theBoard.mNumWaves;
            theBoard.mHugeWaveCountDown = 0;
            theBoard.clearAdvice(AdviceType.ADVICE_HUGE_WAVE);
            theBoard.removeAllZombies();
            theBoard.mLevelAwardSpawned = true;
            theBoard.fadeOutLevel();
            return;
        }

        theBoard.mHugeWaveCountDown = 0;
        theBoard.clearAdvice(AdviceType.ADVICE_HUGE_WAVE);
        theBoard.mLevelAwardSpawned = true;
        int aCoinType = cn.enaium.fabricpvz.portable.lawn.ZombieStatus.levelAwardCoinType(theBoard);
        if (aCoinType == cn.enaium.fabricpvz.portable.lawn.CoinType.COIN_NONE) {
            return;
        }
        theBoard.mApp.playFoley(cn.enaium.fabricpvz.portable.pvzp.fx.FoleyType.FOLEY_SPAWN_SUN);
        Coin aAward = theBoard.addCoin(400, 300, aCoinType,
                cn.enaium.fabricpvz.portable.lawn.CoinMotion.COIN_MOTION_COIN);
        aAward.collect();
        show(theBoard, "直接过关！");
    }

    // -------------------------------------------------------------------------------------

    /**
     * 悬浮按钮：原生石头钮（drawStoneButton），可拖动，点击展开对话框。
     * 不走 ButtonWidget 的监听派发（监听器是空壳），点击/拖动语义完全自理。
     */
    private static final class TrainerButton extends LawnStoneButton {

        private static final int DRAG_THRESHOLD = 5;

        // 与局内菜单按钮（Board.mMenuButton，Board.java:369 的 681,-10,117,46）对齐：
        // 同宽 117，默认摆在其正下方（菜单按钮屏幕底边 y = -10 + 46 = 36，留 4px 间隙）
        private static final int MENU_BUTTON_WIDTH = 117;
        private static final int MENU_BUTTON_X = 681;
        private static final int MENU_BUTTON_SCREEN_BOTTOM = 36;
        private static final int GAP_BELOW_MENU = 4;

        private boolean mDragging;
        private boolean mDragged;
        private int mGrabOffsetX;
        private int mGrabOffsetY;

        TrainerButton() {
            super(null, 7001, NOOP_LISTENER);
            setLabel("修改器");
            mHasAlpha = true;
            mHasTransparencies = true;
            mDoFinger = true;
            // 贴图未装载（INITIALIZED 阶段）绝不绘制：drawStoneButton 会在裁剪前解引用 null 贴图
            mVisible = false;
        }

        @Override
        public void draw(Graphics g) {
            // drawStoneButton 会解引用按钮贴图与标签字体：LoadingImages 先于 LoadingFonts
            // 装载（LawnApp.java:2156-2157），两者都可能短暂为 null——缺任何一个都不画
            if (stoneArtHeight() == 0 || cn.enaium.fabricpvz.portable.lawn.Resources
                    .getFontById(cn.enaium.fabricpvz.portable.lawn.Resources.FONT_DWARVENTODCRAFT18GREENINSET_ID) == null) {
                return;
            }
            super.draw(g);
        }

        @Override
        public void mouseDown(int theX, int theY, int theBtnNum, int theClickCount) {
            mIsDown = true;
            mDragged = false;
            // 指针的全局位置 = 按钮位置 + 控件内偏移
            mGrabOffsetX = theX;
            mGrabOffsetY = theY;
            mDragging = true;
        }

        @Override
        public void mouseUp(int theX, int theY, int theBtnNum, int theClickCount) {
            mIsDown = false;
            if (!mDragging) {
                return;
            }
            mDragging = false;
            if (!mDragged) {
                openDialog(); // 位移小于阈值：算点击，展开对话框
            }
        }

        /**
         * 每帧钩子（WidgetManager.updateAll 对所有 widget 派发 update()，与场景/z 序无关）——
         * 常驻按钮的可见性、置顶维护与拖动跟随全在这里做，不依赖关卡内才触发的 TICK。
         */
        @Override
        public void update() {
            WidgetManager aManager = sApp != null ? sApp.mWidgetManager : null;
            if (aManager == null) {
                return;
            }

            // 零尺寸 = 尚未定型（INITIALIZED 时按钮贴图未装载）：每帧探测，就绪即定型。
            // 宽度取局内菜单按钮的 117，默认位置在其正下方
            if (mWidth == 0 || mHeight == 0) {
                int aHeight = stoneArtHeight();
                if (aHeight == 0) {
                    return; // 贴图未装载，下帧再试
                }
                resize(MENU_BUTTON_X, MENU_BUTTON_SCREEN_BOTTOM + GAP_BELOW_MENU,
                        MENU_BUTTON_WIDTH, aHeight);
            }

            // 常驻：除加载画面、自身对话框展开与各排除页（见
            // isOnExcludedPage）外都显示；mVisible=false 不参与命中测试
            boolean aExcluded = isOnExcludedPage();
            mVisible = !aExcluded && !sDialogOpen;

            // 排除页上展开的修改器窗口自动收起（从其他页打开状态跳转过去时）
            if (aExcluded && sDialogOpen && sApp.getDialog(TRAINER_DIALOG_ID) != null) {
                sApp.killDialog(TRAINER_DIALOG_ID);
                sDialogOpen = false;
                if (sAutoPauseOnOpen && sBoard != null
                        && sApp.mGameScene == GameScenes.SCENE_PLAYING) {
                    sBoard.pause(false);
                }
            }

            // 新界面/棋盘会把整屏 widget 重新加入管理器（排到按钮之上）：
            // 按钮不在末位就摘下重加，恢复置顶——配置关闭时不维护，被选项等后开窗口覆盖
            if (sAlwaysOnTop && mVisible && !aManager.mWidgets.isEmpty()
                    && aManager.mWidgets.get(aManager.mWidgets.size() - 1) != this) {
                aManager.removeWidget(this);
                aManager.addWidget(this);
            }

            // 特效页"特性复刻"开关：各特性独立，不触碰 mGameMode——逐帧特性直接调
            // 游戏自己的实现（雨滴溅落/闪电状态机/种子掉落/双倍推进），锤子光标在
            // 这里装配；一次性初始化随棋盘换代重放。静态判定点（出怪缩小/整列种植/
            // 抛物线/墓碑生成/空降出怪/雨雾绘制/锤击命中）由注入点 OR 入开关
            Board aBoard = sApp.mBoard;
            if (aBoard != null && sApp.mGameScene == GameScenes.SCENE_PLAYING) {
                cn.enaium.fabricpvz.portable.lawn.Challenge aChallenge = aBoard.mChallenge;
                if (stormyNight) {
                    // 开启或新关卡：闪电状态机初始态 + 雨声（startLevel 同款）
                    if (sStormBoard != aBoard) {
                        aChallenge.mChallengeState =
                                cn.enaium.fabricpvz.portable.lawn.ChallengeState.STATECHALLENGE_STORM_FLASH_1;
                        aChallenge.mChallengeStateCounter = 400;
                        sApp.playFoley(cn.enaium.fabricpvz.portable.pvzp.fx.FoleyType.FOLEY_RAIN);
                        sStormBoard = aBoard;
                    }
                    aChallenge.updateStormyNight();
                } else if (sStormBoard != null) {
                    sStormBoard = null;
                }
                if (!aBoard.mPaused) {
                    if (seedRain || stormyNight) {
                        aChallenge.updateRain();
                    }
                    if (seedRain) {
                        // 开启或新关卡：掉落定时器（initLevel 同款初值）
                        if (sSeedRainBoard != aBoard) {
                            aChallenge.mChallengeStateCounter = 100;
                            if (!stormyNight) {
                                sApp.playFoley(cn.enaium.fabricpvz.portable.pvzp.fx.FoleyType.FOLEY_RAIN);
                            }
                            sSeedRainBoard = aBoard;
                        }
                        aChallenge.updateRainingSeeds();
                    }
                    if (zombieSpeed) {
                        aBoard.updateGame(); // 僵尸快跑：每帧双倍推进（SPEED 模式同款）
                    }
                    // 倍速滑块：正常 tick 之外额外推进 N-1 次（SPEED 模式同款机制），
                    // 出怪/植物/僵尸/抛射物/阳光全部按倍数加速；拖动实时生效。
                    // 传送带（滚动动画与新卡推入）走 Challenge.update 的独立驱动链，
                    // 不在 updateGame 里——额外推进一次补齐（仅传送带关存在）
                    for (int i = 1; i < sSpeedMult; i++) {
                        aBoard.updateGame();
                        if (aBoard.hasConveyorBeltSeedBank()) {
                            aChallenge.updateConveyorBelt();
                        }
                    }
                }
                if (whackHammer) {
                    // 开启或新关卡：锤子光标（startLevel 同款）
                    if (sWhackBoard != aBoard) {
                        setupWhackHammer(aBoard);
                        sWhackBoard = aBoard;
                    }
                } else if (sWhackBoard != null) {
                    if (sWhackBoard.mCursorObject.mCursorType
                            == cn.enaium.fabricpvz.portable.lawn.CursorType.CURSOR_TYPE_HAMMER) {
                        sWhackBoard.mCursorObject.mCursorType =
                                cn.enaium.fabricpvz.portable.lawn.CursorType.CURSOR_TYPE_NORMAL;
                        sApp.removeReanimation(sWhackBoard.mCursorObject.mReanimCursorID);
                    }
                    sWhackBoard = null;
                }
            }

            // 拖动跟随：按住期间每帧吸附到指针（manager 的 public 全局鼠标坐标）
            if (mDragging) {
                int aNewX = aManager.mLastMouseX - mGrabOffsetX;
                int aNewY = aManager.mLastMouseY - mGrabOffsetY;
                if (Math.abs(aNewX - mX) > DRAG_THRESHOLD || Math.abs(aNewY - mY) > DRAG_THRESHOLD) {
                    mDragged = true;
                }
                if (mDragged) {
                    // 钳制在窗口逻辑尺寸内
                    int aClampedX = Math.max(0, Math.min(aNewX, sApp.mWidth - mWidth));
                    int aClampedY = Math.max(0, Math.min(aNewY, sApp.mHeight - mHeight));
                    resize(aClampedX, aClampedY, mWidth, mHeight);
                }
            }
        }

        private void openDialog() {
            if (sDialogOpen) {
                return;
            }
            sDialogOpen = true;
            sApp.playSample(cn.enaium.fabricpvz.portable.lawn.SoundType.SOUND_GRAVEBUTTON);
            // 记住打开前的真实焦点（图鉴/credits MV 的 CreditScreen 等）
            sPrevFocus = sApp.mWidgetManager.mFocusWidget;
            // 配置项：展开自动暂停 = modal 打开（modalOpen/modalClose 既有机制暂停棋盘）；
            // 关闭时对话框非模态，棋盘在背后继续运行
            sApp.addDialog(TRAINER_DIALOG_ID, new TrainerDialog(sAutoPauseOnOpen));
            // addedToManager 抢的焦点代理会被 addBaseModal 记成"先前焦点"——修正为
            // 打开前的真实焦点，关闭时才能把按键还给图鉴/CreditScreen 等底层界面
            var aList = sApp.mWidgetManager.mPreModalInfoList;
            if (!aList.isEmpty()) {
                aList.get(aList.size() - 1).mPrevFocusWidget = sPrevFocus;
            }
        }
    }

    // -------------------------------------------------------------------------------------

    /**
     * 原生对话框：顶部"关 卡 / 杂 项"两枚标签页石钮，内容区为固定高度的滚动视口——
     * 关卡页（4 个复选框 + 杀死全场僵尸/立即下一波/推进关卡）与杂项页（杀死全场植物/
     * 清除/恢复/启动小推车、跳关器）依次下排，超出视口出滚动条（拖动 + 滚轮）。
     * 底部 footer 按钮"收 起"（BUTTONS_FOOTER 的标准 1000 号按钮）收回折叠。
     * modal=true：打开自动暂停棋盘，关闭自动恢复（modalOpen/modalClose 既有机制）。
     */
    private static final class TrainerDialog extends LawnDialog
            implements CheckboxListener, SliderListener {

        /** 原生 Checkbox 美术是 46×45（NewOptionsDialog 的同名常量），行高必须盖过它。 */
        private static final int CHECKBOX_WIDTH = 46;
        private static final int CHECKBOX_HEIGHT = 45;
        private static final int ROW_HEIGHT = 50;
        /** 滚动条宽。 */
        private static final int SCROLLBAR_WIDTH = 8;

        private final Checkbox mSunCheck;
        private final Checkbox mPlantCheck;
        private final Checkbox mCollectCheck;
        private final Checkbox mPauseCheck;
        private final LawnStoneButton mKillButton;
        private final LawnStoneButton mNextWaveButton;
        private final LawnStoneButton mAdvanceButton;
        private final LawnStoneButton mWinLevelButton;
        private final LawnStoneButton mKillPlantsButton;
        private final LawnStoneButton mClearMowersButton;
        private final LawnStoneButton mRestoreMowersButton;
        private final LawnStoneButton mStartMowersButton;
        private final LawnStoneButton mSkipLevelButton;
        private final LawnStoneButton mUnlockSunflowerButton;
        private final LawnStoneButton mTmpUnlockButton;
        private final LawnStoneButton mSetCoinsButton;
        private final Checkbox mInfiniteCoinsCheck;
        private final Checkbox mInfiniteFertilizerCheck;
        private final Checkbox mInfiniteBugSprayCheck;
        private final Checkbox mInfiniteChocolateCheck;
        private final Checkbox mInfiniteTreeFoodCheck;
        private final LawnStoneButton mTreeHeightButton;
        private final LawnStoneButton mAddSproutButton;
        private final LawnStoneButton mAddMarigoldButton;
        private final LawnStoneButton mAddBigPlantButton;
        /** 特效页：8 枚开关按钮（SUKHBIR/FUTURE/MUSTACHE/TRICKEDOUT/DAISIES/DANCE/PINATA/KONAMI）。 */
        private final LawnStoneButton[] mEffectButtons = new LawnStoneButton[8];
        /** 场地页：行/列滑块（音量滑块同款）与种子/僵尸/物品选择钮、动作钮。 */
        private final Slider mRowSlider;
        private final Slider mColSlider;
        /** 关卡页：倍速滑块（1-10 倍，实时生效）。 */
        private final Slider mSpeedSlider;
        private final Checkbox mNoSunDropsCheck;
        private final Checkbox mNoCoinDropsCheck;
        private final Checkbox mAnyPlantingCheck;
        private final LawnStoneButton mFieldSeedButton;
        private final LawnStoneButton mFieldPlantButton;
        private final LawnStoneButton mFieldZombieButton;
        private final LawnStoneButton mFieldZombiePlaceButton;
        private final LawnStoneButton mFieldItemButton;
        private final LawnStoneButton mFieldItemPlaceButton;
        private final LawnStoneButton mFieldMusicButton;
        private final LawnStoneButton mFieldMusicApplyButton;
        private final LawnStoneButton mFieldClearGravesButton;
        private final Checkbox mRemoveFogCheck;
        private final Checkbox mVaseXrayCheck;
        /** 卡槽页：格数/卡槽/卡片选择与开关。 */
        private final LawnStoneButton mSlotCountButton;
        private final LawnStoneButton mSetSunButton;
        private final Checkbox mHideSeedBankCheck;
        private final Checkbox mShowShovelCheck;
        private final Checkbox mNoCooldownCheck;
        private final Checkbox mHideMenuButtonCheck;
        private final LawnStoneButton mSlotPickButton;
        private final LawnStoneButton mSlotSeedButton;
        private final LawnStoneButton mSlotApplyButton;
        private final LawnStoneButton mCardPickButton;
        private final LawnStoneButton mCardAttackButton;
        private final LawnStoneButton mCardPriceButton;
        private final LawnStoneButton mCardCooldownButton;
        /** 植物页的 8 个开关。 */
        private final Checkbox mCobNoCdCheck;
        private final Checkbox mMagnetNoCdCheck;
        private final Checkbox mPotatoNoCdCheck;
        private final Checkbox mChomperNoCdCheck;
        private final Checkbox mPlantInvincibleCheck;
        private final Checkbox mPlantFragileCheck;
        private final Checkbox mNoCraterCheck;
        private final Checkbox mMushroomAwakeCheck;
        /** 特效按钮的标签，与 mEffectButtons 同序。 */
        private static final String[] EFFECT_NAMES =
                {"SUKHBIR", "FUTURE", "MUSTACHE", "TRICKEDOUT", "DAISIES", "DANCE", "PINATA", "KONAMI"};
        /** 特效页的 9 个关卡机制复刻开关（相互独立，种子雨与暴风雨共用计时器互斥）。 */
        private final Checkbox mSeedRainCheck;
        private final Checkbox mMiniZombieCheck;
        private final Checkbox mColumnCheck;
        private final Checkbox mZombieSpeedCheck;
        private final Checkbox mWhackCheck;
        private final Checkbox mNewtonCheck;
        private final Checkbox mGraveDangerCheck;
        private final Checkbox mStormyNightCheck;
        private final Checkbox mBungeeBlitzCheck;
        /** 僵尸页：15 个开关 + 吸怪列滑块 + 批量操作选择/修改。 */
        private final Checkbox mZombieExplodeCheck;
        private final Checkbox mZombieNeverExplodeCheck;
        private final Checkbox mZombieNoChillCheck;
        private final Checkbox mZombieNoButterCheck;
        private final Checkbox mZombieHideCheck;
        private final Checkbox mZombieShowCheck;
        private final Checkbox mZombieNoDancersCheck;
        private final Checkbox mZombieNoIceTrailCheck;
        private final Checkbox mZombieInvincibleCheck;
        private final Checkbox mZombieWeakCheck;
        private final Checkbox mZombieNoImpThrowCheck;
        private final Checkbox mZombieGargEdibleCheck;
        private final Checkbox mZombieFrozenCheck;
        private final Checkbox mZombieBossFrozenCheck;
        private final Checkbox mZombieBalloonBoomCheck;
        private final Checkbox mZombieVacuumCheck;
        private final Slider mVacuumColSlider;
        private final LawnStoneButton mZombieOpButton;
        private final LawnStoneButton mZombieApplyButton;
        /** 复选框 → 条目文字（视口绘制与居中布局共用，覆盖所有标签页的复选框）。 */
        private final java.util.LinkedHashMap<Checkbox, String> mCheckboxLabels =
                new java.util.LinkedHashMap<>();
        /** 两个标签页的名字与下拉按钮（对话框的固定子控件，不在滚动视口里）。 */
        private static final String[] PAGE_NAMES = {"关 卡", "卡 槽", "植 物", "僵 尸",
                "花 园", "场 地", "特 效", "状 态", "用 户", "其 他"};
        /** 状态页在 PAGE_NAMES 里的序号（纯展示页，没有控件）；僵尸页 = 3；场地页 = 5。 */
        private static final int STATUS_TAB = 7;
        private static final int ZOMBIE_TAB = 3;
        private static final int FIELD_TAB = 5;
        /** 状态页的展示行数。 */
        private static final int STATUS_ROW_COUNT = 12;
        private final LawnStoneButton mTabButton;

        /** 标签页：每页的控件序列（布局时依次下排，页外控件隐藏）。 */
        private final java.util.List<java.util.List<Widget>> mTabPages = new java.util.ArrayList<>();
        /** 打开时恢复上次会话记住的标签页与该页滚动位置。 */
        private int mCurrentTab = sLastTab;
        private int mScrollOffset = sTabScrollOffsets[Math.min(sLastTab, sTabScrollOffsets.length - 1)];
        /** 标签行下缘（视口顶）在对话框里的 y。 */
        private int mContentTop;

        /**
         * 内容滚动视口：复选框与石钮是它的子控件（坐标随滚动重排），mClip 把滚出
         * 视口的内容裁掉。条目文字也画在这里（随内容滚动）。
         */
        private final Widget mViewport = new Widget() {
            {
                mClip = true;
            }

            /**
             * 视口裁剪命中：绘制有 mClip，但引擎的命中递归不看裁剪框——滚出视口的
             * 按钮"残影矩形"延伸到视口外仍会被点中（点收起按钮上方边缘即触发）。
             * 指针不在视口矩形内时，子控件一律不参与命中，点击落回对话框自身。
             */
            @Override
            public Widget getWidgetAtHelper(int theX, int theY, int theFlags, boolean[] theFound,
                    int[] theWidgetX, int[] theWidgetY) {
                if (theX < 0 || theY < 0 || theX >= mWidth || theY >= mHeight) {
                    theFound[0] = false;
                    return null;
                }
                return super.getWidgetAtHelper(theX, theY, theFlags, theFound,
                        theWidgetX, theWidgetY);
            }

            @Override
            public void mouseDown(int theX, int theY, int theBtnNum, int theClickCount) {
                // 点在视口空白区（对话框内、菜单外）也收起下拉菜单
                closeTabMenu();
            }

            @Override
            public void draw(Graphics g) {
                Font aFont = cn.enaium.fabricpvz.portable.lawn.Resources
                        .getFontById(cn.enaium.fabricpvz.portable.lawn.Resources.FONT_BRIANNETOD12_ID);
                g.setFont(aFont);
                // 标签跟随各自复选框的位置（复选框在 layoutContent 里随滚动平移）
                for (var anEntry : mCheckboxLabels.entrySet()) {
                    if (!anEntry.getKey().mVisible) {
                        continue; // 非当前页的复选框不画标签
                    }
                    g.setColor(new cn.enaium.fabricpvz.portable.sexy.Color(232, 218, 160));
                    g.drawString(anEntry.getValue(), anEntry.getKey().mX + CHECKBOX_WIDTH + 10,
                            anEntry.getKey().mY + (CHECKBOX_HEIGHT - aFont.mHeight) / 2
                                    + aFont.mAscent);
                }
                // 场地页：行列滑块的当前值（画在滑块左前方，垂直居中）
                if (mCurrentTab == FIELD_TAB && mRowSlider.mVisible) {
                    g.setColor(new cn.enaium.fabricpvz.portable.sexy.Color(232, 218, 160));
                    g.drawString("行 " + sFieldRow,
                            Math.max(4, mRowSlider.mX - 44),
                            mRowSlider.mY + (mRowSlider.mHeight - aFont.mHeight) / 2 + aFont.mAscent);
                    g.drawString("列 " + sFieldCol,
                            Math.max(4, mColSlider.mX - 44),
                            mColSlider.mY + (mColSlider.mHeight - aFont.mHeight) / 2 + aFont.mAscent);
                }
                // 关卡页：倍速滑块的当前值（画在滑块左前方，垂直居中）
                if (mCurrentTab == 0 && mSpeedSlider.mVisible) {
                    g.setColor(new cn.enaium.fabricpvz.portable.sexy.Color(232, 218, 160));
                    g.drawString("倍速 " + sSpeedMult,
                            Math.max(4, mSpeedSlider.mX - 44),
                            mSpeedSlider.mY + (mSpeedSlider.mHeight - aFont.mHeight) / 2 + aFont.mAscent);
                }
                // 僵尸页：吸怪目标列的当前值（画在滑块左前方，垂直居中）
                if (mCurrentTab == ZOMBIE_TAB && mVacuumColSlider.mVisible) {
                    g.setColor(new cn.enaium.fabricpvz.portable.sexy.Color(232, 218, 160));
                    g.drawString("列 " + sVacuumCol,
                            Math.max(4, mVacuumColSlider.mX - 44),
                            mVacuumColSlider.mY + (mVacuumColSlider.mHeight - aFont.mHeight) / 2 + aFont.mAscent);
                }
                // 状态页：纯展示行（标签：值），随棋盘实时变化
                if (mCurrentTab == STATUS_TAB) {
                    String[] aRows = buildStatusRows();
                    for (int i = 0; i < aRows.length; i++) {
                        g.setColor(new cn.enaium.fabricpvz.portable.sexy.Color(232, 218, 160));
                        g.drawString(aRows[i], 10, i * 20 + (20 - aFont.mHeight) / 2 + aFont.mAscent);
                    }
                }
            }
        };

        /**
         * 焦点代理：模态框自身持有的焦点会在建立模态时被清掉（对话框对自己"位于模态之下"），
         * 子控件不算——用它代持焦点代收滚轮。零尺寸不画、命中测试查不到。
         */
        private final Widget mFocusProxy = new Widget() {
            {
                mVisible = false;
            }

            @Override
            public boolean wantsFocus() {
                return true;
            }

            @Override
            public void mouseWheel(int theDelta) {
                // 与游戏同约定：正值（上滚）内容向上（偏移减小）
                scrollContent(-theDelta * ROW_HEIGHT);
            }
        };

        /** 内容总高与视口高（resize 时算好），滚动条拖动状态；石钮全宽基准。 */
        private int mContentHeight;
        private int mViewportHeight;
        private int mFullContentWidth;
        private boolean mScrollbarDragging;
        private int mThumbGrabOffset;
        /** 对话框拖动（按在自身区域）状态。 */
        private boolean mDialogDragging;
        private int mDialogGrabOffsetX;
        private int mDialogGrabOffsetY;
        /** 打开中的弹出列表（页名菜单/场地页各下拉共用槽位），未打开 null。 */
        private ListPopupWidget mTabMenuWidget;
        /** "界面不支持"提示的恢复时刻与原文（0 = 无待恢复），显示在"修改音乐"钮上。 */
        private long mMusicLabelRestoreAt;
        private String mMusicLabelOriginal;
        /** closeTabMenu 重入守卫（移除过程触发 lostFocus 会再次调进来）。 */
        private boolean mClosingTabMenu;

        TrainerDialog(boolean theModal) {
            super(sApp, TRAINER_DIALOG_ID, theModal, "修改器", "", "收 起", Dialog.BUTTONS_FOOTER);

            mSunCheck = LawnCommon.makeNewCheckbox(ID_CHECK_SUN, this, infiniteSun);
            mPlantCheck = LawnCommon.makeNewCheckbox(ID_CHECK_PLANT, this, freePlanting);
            mCollectCheck = LawnCommon.makeNewCheckbox(ID_CHECK_COLLECT, this, autoCollect);
            mPauseCheck = LawnCommon.makeNewCheckbox(ID_CHECK_PAUSE_SPAWN, this, pauseSpawning);
            mKillButton = GameButton.makeButton(ID_KILL_ALL, this, "杀死全场僵尸");
            mNextWaveButton = GameButton.makeButton(ID_NEXT_WAVE, this, "立即下一波");
            mAdvanceButton = GameButton.makeButton(ID_ADVANCE_FLAG, this, "推进关卡");
            mWinLevelButton = GameButton.makeButton(ID_WIN_LEVEL, this, "直接过关");
            mUnlockSunflowerButton = GameButton.makeButton(ID_UNLOCK_GOLD_SUNFLOWER, this, "解锁金向日葵");
            mTmpUnlockButton = GameButton.makeButton(ID_TMP_UNLOCK_MODES, this, "临时解锁所有模式");
            mSetCoinsButton = GameButton.makeButton(ID_SET_COINS, this, "设置金币");
            mInfiniteCoinsCheck = LawnCommon.makeNewCheckbox(ID_CHECK_INFINITE_COINS, this, infiniteCoins);
            mInfiniteFertilizerCheck = LawnCommon.makeNewCheckbox(ID_CHECK_INFINITE_FERTILIZER, this, infiniteFertilizer);
            mInfiniteBugSprayCheck = LawnCommon.makeNewCheckbox(ID_CHECK_INFINITE_BUG_SPRAY, this, infiniteBugSpray);
            mInfiniteChocolateCheck = LawnCommon.makeNewCheckbox(ID_CHECK_INFINITE_CHOCOLATE, this, infiniteChocolate);
            mInfiniteTreeFoodCheck = LawnCommon.makeNewCheckbox(ID_CHECK_INFINITE_TREE_FOOD, this, infiniteTreeFood);
            int[] anEffectIds = {ID_EFFECT_SUKHBIR, ID_EFFECT_FUTURE, ID_EFFECT_MUSTACHE,
                    ID_EFFECT_TRICKEDOUT, ID_EFFECT_DAISIES, ID_EFFECT_DANCE,
                    ID_EFFECT_PINATA, ID_EFFECT_KONAMI};
            for (int i = 0; i < anEffectIds.length; i++) {
                mEffectButtons[i] = GameButton.makeButton(anEffectIds[i], this, EFFECT_NAMES[i]);
            }
            // 关卡机制复刻开关：初始勾选跟随各自开关状态（对话框重开时保持显示）
            mSeedRainCheck = LawnCommon.makeNewCheckbox(ID_CHECK_MODE_SEED_RAIN, this, seedRain);
            mMiniZombieCheck = LawnCommon.makeNewCheckbox(ID_CHECK_MODE_MINI_ZOMBIE, this, miniZombie);
            mColumnCheck = LawnCommon.makeNewCheckbox(ID_CHECK_MODE_COLUMN, this, columnPlant);
            mZombieSpeedCheck = LawnCommon.makeNewCheckbox(ID_CHECK_MODE_ZOMBIE_SPEED, this, zombieSpeed);
            mWhackCheck = LawnCommon.makeNewCheckbox(ID_CHECK_MODE_WHACK, this, whackHammer);
            mNewtonCheck = LawnCommon.makeNewCheckbox(ID_CHECK_MODE_NEWTON, this, newtonGravity);
            mGraveDangerCheck = LawnCommon.makeNewCheckbox(ID_CHECK_MODE_GRAVE_DANGER, this, graveDanger);
            mStormyNightCheck = LawnCommon.makeNewCheckbox(ID_CHECK_MODE_STORMY_NIGHT, this, stormyNight);
            mBungeeBlitzCheck = LawnCommon.makeNewCheckbox(ID_CHECK_MODE_BUNGEE_BLITZ, this, bungeeBlitz);
            mZombieExplodeCheck = LawnCommon.makeNewCheckbox(ID_CHECK_ZOMBIE_EXPLODE, this, zombieExplode);
            mZombieNeverExplodeCheck = LawnCommon.makeNewCheckbox(ID_CHECK_ZOMBIE_NEVER_EXPLODE, this, zombieNeverExplode);
            mZombieNoChillCheck = LawnCommon.makeNewCheckbox(ID_CHECK_ZOMBIE_NO_CHILL, this, zombieNoChill);
            mZombieNoButterCheck = LawnCommon.makeNewCheckbox(ID_CHECK_ZOMBIE_NO_BUTTER, this, zombieNoButter);
            mZombieHideCheck = LawnCommon.makeNewCheckbox(ID_CHECK_ZOMBIE_HIDE, this, zombieHide);
            mZombieShowCheck = LawnCommon.makeNewCheckbox(ID_CHECK_ZOMBIE_SHOW, this, zombieShow);
            mZombieNoDancersCheck = LawnCommon.makeNewCheckbox(ID_CHECK_ZOMBIE_NO_DANCERS, this, zombieNoDancers);
            mZombieNoIceTrailCheck = LawnCommon.makeNewCheckbox(ID_CHECK_ZOMBIE_NO_ICE_TRAIL, this, zombieNoIceTrail);
            mZombieInvincibleCheck = LawnCommon.makeNewCheckbox(ID_CHECK_ZOMBIE_INVINCIBLE, this, zombieInvincible);
            mZombieWeakCheck = LawnCommon.makeNewCheckbox(ID_CHECK_ZOMBIE_WEAK, this, zombieWeak);
            mZombieNoImpThrowCheck = LawnCommon.makeNewCheckbox(ID_CHECK_ZOMBIE_NO_IMP_THROW, this, zombieNoImpThrow);
            mZombieGargEdibleCheck = LawnCommon.makeNewCheckbox(ID_CHECK_ZOMBIE_GARG_EDIBLE, this, zombieGargEdible);
            mZombieFrozenCheck = LawnCommon.makeNewCheckbox(ID_CHECK_ZOMBIE_FROZEN, this, zombieFrozen);
            mZombieBossFrozenCheck = LawnCommon.makeNewCheckbox(ID_CHECK_ZOMBIE_BOSS_FROZEN, this, zombieBossFrozen);
            mZombieBalloonBoomCheck = LawnCommon.makeNewCheckbox(ID_CHECK_ZOMBIE_BALLOON_BOOM, this, zombieBalloonBoom);
            mZombieVacuumCheck = LawnCommon.makeNewCheckbox(ID_CHECK_ZOMBIE_VACUUM, this, zombieVacuum);
            mVacuumColSlider = new Slider(
                    cn.enaium.fabricpvz.portable.lawn.Resources
                            .getImageById(cn.enaium.fabricpvz.portable.lawn.Resources.IMAGE_OPTIONS_SLIDERSLOT_ID),
                    cn.enaium.fabricpvz.portable.lawn.Resources
                            .getImageById(cn.enaium.fabricpvz.portable.lawn.Resources.IMAGE_OPTIONS_SLIDERKNOB2_ID),
                    ID_ZOMBIE_VACUUM_COL, this);
            mVacuumColSlider.setValue((sVacuumCol - 1) / 8.0);
            mZombieOpButton = GameButton.makeButton(ID_ZOMBIE_OP, this, "所有僵尸：" + ZOMBIE_OP_NAMES[sZombieOpIndex]);
            mZombieApplyButton = GameButton.makeButton(ID_ZOMBIE_APPLY, this, "修 改");
            mTreeHeightButton = GameButton.makeButton(ID_TREE_HEIGHT, this, "设置智慧树高度");
            mAddSproutButton = GameButton.makeButton(ID_ADD_SPROUT, this, "新增一颗随机幼苗");
            mAddMarigoldButton = GameButton.makeButton(ID_ADD_MARIGOLD, this, "新增金盏花幼苗");
            mAddBigPlantButton = GameButton.makeButton(ID_ADD_BIG_PLANT, this, "新增一颗随机大花");
            mRowSlider = new Slider(
                    cn.enaium.fabricpvz.portable.lawn.Resources
                            .getImageById(cn.enaium.fabricpvz.portable.lawn.Resources.IMAGE_OPTIONS_SLIDERSLOT_ID),
                    cn.enaium.fabricpvz.portable.lawn.Resources
                            .getImageById(cn.enaium.fabricpvz.portable.lawn.Resources.IMAGE_OPTIONS_SLIDERKNOB2_ID),
                    ID_FIELD_ROW, this);
            mRowSlider.setValue((sFieldRow - 1) / 5.0);
            mNoSunDropsCheck = LawnCommon.makeNewCheckbox(ID_CHECK_NO_SUN_DROPS, this, noSunDrops);
            mNoCoinDropsCheck = LawnCommon.makeNewCheckbox(ID_CHECK_NO_COIN_DROPS, this, noCoinDrops);
            mAnyPlantingCheck = LawnCommon.makeNewCheckbox(ID_CHECK_ANY_PLANTING, this, anyPlanting);
            mSpeedSlider = new Slider(
                    cn.enaium.fabricpvz.portable.lawn.Resources
                            .getImageById(cn.enaium.fabricpvz.portable.lawn.Resources.IMAGE_OPTIONS_SLIDERSLOT_ID),
                    cn.enaium.fabricpvz.portable.lawn.Resources
                            .getImageById(cn.enaium.fabricpvz.portable.lawn.Resources.IMAGE_OPTIONS_SLIDERKNOB2_ID),
                    ID_FIELD_SPEED, this);
            mSpeedSlider.setValue((sSpeedMult - 1) / 9.0);
            mColSlider = new Slider(
                    cn.enaium.fabricpvz.portable.lawn.Resources
                            .getImageById(cn.enaium.fabricpvz.portable.lawn.Resources.IMAGE_OPTIONS_SLIDERSLOT_ID),
                    cn.enaium.fabricpvz.portable.lawn.Resources
                            .getImageById(cn.enaium.fabricpvz.portable.lawn.Resources.IMAGE_OPTIONS_SLIDERKNOB2_ID),
                    ID_FIELD_COL, this);
            mColSlider.setValue((sFieldCol - 1) / 8.0);
            mFieldSeedButton = GameButton.makeButton(ID_FIELD_SEED, this,
                    "植物：" + cn.enaium.fabricpvz.portable.lawn.Plant.getNameString(sFieldSeed,
                            cn.enaium.fabricpvz.portable.lawn.SeedType.SEED_NONE));
            mFieldPlantButton = GameButton.makeButton(ID_FIELD_PLANT, this, "种 植");
            mFieldZombieButton = GameButton.makeButton(ID_FIELD_ZOMBIE, this, "僵尸：" + sFieldZombie);
            mFieldZombiePlaceButton = GameButton.makeButton(ID_FIELD_ZOMBIE_PLACE, this, "放 置");
            mFieldItemButton = GameButton.makeButton(ID_FIELD_ITEM, this, "物品：墓碑");
            mFieldItemPlaceButton = GameButton.makeButton(ID_FIELD_ITEM_PLACE, this, "放置物品");
            mFieldMusicButton = GameButton.makeButton(ID_FIELD_MUSIC, this, "音乐：Grasswalk");
            mFieldMusicApplyButton = GameButton.makeButton(ID_FIELD_MUSIC_APPLY, this, "修改音乐");
            mFieldClearGravesButton = GameButton.makeButton(ID_FIELD_CLEAR_GRAVES, this, "清空墓碑");
            mRemoveFogCheck = LawnCommon.makeNewCheckbox(ID_CHECK_REMOVE_FOG, this, removeFog);
            mVaseXrayCheck = LawnCommon.makeNewCheckbox(ID_CHECK_VASE_XRAY, this, vaseXray);
            // 复选框条目文字集中登记（视口绘制与居中布局共用）
            mCheckboxLabels.put(mSunCheck, "无限阳光");
            mCheckboxLabels.put(mPlantCheck, "免费种植");
            mCheckboxLabels.put(mCollectCheck, "自动收集");
            mCheckboxLabels.put(mPauseCheck, "暂停出怪");
            mCheckboxLabels.put(mNoSunDropsCheck, "不掉阳光");
            mCheckboxLabels.put(mNoCoinDropsCheck, "不掉金币");
            mCheckboxLabels.put(mAnyPlantingCheck, "任意种植");
            mCheckboxLabels.put(mInfiniteCoinsCheck, "无限金币");
            mCheckboxLabels.put(mInfiniteFertilizerCheck, "无限花肥");
            mCheckboxLabels.put(mInfiniteBugSprayCheck, "无限杀虫剂");
            mCheckboxLabels.put(mInfiniteChocolateCheck, "无限巧克力");
            mCheckboxLabels.put(mInfiniteTreeFoodCheck, "无限树肥");
            mSlotCountButton = GameButton.makeButton(ID_SLOT_COUNT, this, "卡槽格数：10");
            mSetSunButton = GameButton.makeButton(ID_SET_SUN, this, "设置阳光");
            mHideSeedBankCheck = LawnCommon.makeNewCheckbox(ID_CHECK_HIDE_SEEDBANK, this, false);
            mShowShovelCheck = LawnCommon.makeNewCheckbox(ID_CHECK_SHOW_SHOVEL, this, false);
            mNoCooldownCheck = LawnCommon.makeNewCheckbox(ID_CHECK_NO_COOLDOWN, this, noCooldown);
            mHideMenuButtonCheck = LawnCommon.makeNewCheckbox(ID_CHECK_HIDE_MENU_BUTTON, this, false);
            mSlotPickButton = GameButton.makeButton(ID_SLOT_PICK, this, "第一格");
            mSlotSeedButton = GameButton.makeButton(ID_SLOT_SEED, this,
                    "植物：" + cn.enaium.fabricpvz.portable.lawn.Plant.getNameString(sSlotSeed,
                            cn.enaium.fabricpvz.portable.lawn.SeedType.SEED_NONE));
            mSlotApplyButton = GameButton.makeButton(ID_SLOT_APPLY, this, "修 改");
            mCardPickButton = GameButton.makeButton(ID_CARD_PICK, this,
                    "卡片：" + cn.enaium.fabricpvz.portable.lawn.Plant.getNameString(sCardSeed,
                            cn.enaium.fabricpvz.portable.lawn.SeedType.SEED_NONE));
            mCardAttackButton = GameButton.makeButton(ID_CARD_ATTACK, this, "攻速");
            mCardPriceButton = GameButton.makeButton(ID_CARD_PRICE, this, "价格");
            mCardCooldownButton = GameButton.makeButton(ID_CARD_COOLDOWN, this, "冷却");
            mCheckboxLabels.put(mHideSeedBankCheck, "隐藏卡槽");
            mCheckboxLabels.put(mShowShovelCheck, "显示铲子");
            mCheckboxLabels.put(mNoCooldownCheck, "卡片无冷却");
            mCheckboxLabels.put(mHideMenuButtonCheck, "隐藏菜单按钮");
            mCheckboxLabels.put(mRemoveFogCheck, "去除浓雾");
            mCheckboxLabels.put(mVaseXrayCheck, "花瓶透视");
            mCheckboxLabels.put(mSeedRainCheck, challengeName(
                    cn.enaium.fabricpvz.portable.lawn.GameMode.GAMEMODE_CHALLENGE_RAINING_SEEDS));
            mCheckboxLabels.put(mMiniZombieCheck, challengeName(
                    cn.enaium.fabricpvz.portable.lawn.GameMode.GAMEMODE_CHALLENGE_LITTLE_TROUBLE));
            mCheckboxLabels.put(mColumnCheck, challengeName(
                    cn.enaium.fabricpvz.portable.lawn.GameMode.GAMEMODE_CHALLENGE_COLUMN));
            mCheckboxLabels.put(mZombieSpeedCheck, challengeName(
                    cn.enaium.fabricpvz.portable.lawn.GameMode.GAMEMODE_CHALLENGE_SPEED));
            mCheckboxLabels.put(mWhackCheck, challengeName(
                    cn.enaium.fabricpvz.portable.lawn.GameMode.GAMEMODE_CHALLENGE_WHACK_A_ZOMBIE));
            mCheckboxLabels.put(mNewtonCheck, challengeName(
                    cn.enaium.fabricpvz.portable.lawn.GameMode.GAMEMODE_CHALLENGE_HIGH_GRAVITY));
            mCheckboxLabels.put(mGraveDangerCheck, challengeName(
                    cn.enaium.fabricpvz.portable.lawn.GameMode.GAMEMODE_CHALLENGE_GRAVE_DANGER));
            mCheckboxLabels.put(mStormyNightCheck, challengeName(
                    cn.enaium.fabricpvz.portable.lawn.GameMode.GAMEMODE_CHALLENGE_STORMY_NIGHT));
            mCheckboxLabels.put(mBungeeBlitzCheck, challengeName(
                    cn.enaium.fabricpvz.portable.lawn.GameMode.GAMEMODE_CHALLENGE_BUNGEE_BLITZ));
            mCheckboxLabels.put(mZombieExplodeCheck, "立即爆炸");
            mCheckboxLabels.put(mZombieNeverExplodeCheck, "从不爆炸");
            mCheckboxLabels.put(mZombieNoChillCheck, "取消寒冰减速");
            mCheckboxLabels.put(mZombieNoButterCheck, "取消黄油固定");
            mCheckboxLabels.put(mZombieHideCheck, "僵尸隐形");
            mCheckboxLabels.put(mZombieShowCheck, "僵尸现形");
            mCheckboxLabels.put(mZombieNoDancersCheck, "取消泳池三人组");
            mCheckboxLabels.put(mZombieNoIceTrailCheck, "不留冰道");
            mCheckboxLabels.put(mZombieInvincibleCheck, "僵尸无敌");
            mCheckboxLabels.put(mZombieWeakCheck, "僵尸脆弱");
            mCheckboxLabels.put(mZombieNoImpThrowCheck, "不扔小鬼");
            mCheckboxLabels.put(mZombieGargEdibleCheck, "巨人可被吃");
            mCheckboxLabels.put(mZombieFrozenCheck, "僵尸不动");
            mCheckboxLabels.put(mZombieBossFrozenCheck, "僵王不动");
            mCheckboxLabels.put(mZombieBalloonBoomCheck, "气球自爆");
            mCheckboxLabels.put(mZombieVacuumCheck, "吸怪");
            mCobNoCdCheck = LawnCommon.makeNewCheckbox(ID_CHECK_COB_NO_CD, this, cobNoCooldown);
            mMagnetNoCdCheck = LawnCommon.makeNewCheckbox(ID_CHECK_MAGNET_NO_CD, this, magnetNoCooldown);
            mPotatoNoCdCheck = LawnCommon.makeNewCheckbox(ID_CHECK_POTATO_NO_CD, this, potatoNoCooldown);
            mChomperNoCdCheck = LawnCommon.makeNewCheckbox(ID_CHECK_CHOMPER_NO_CD, this, chomperNoCooldown);
            mPlantInvincibleCheck = LawnCommon.makeNewCheckbox(ID_CHECK_PLANT_INVINCIBLE, this, plantInvincible);
            mPlantFragileCheck = LawnCommon.makeNewCheckbox(ID_CHECK_PLANT_FRAGILE, this, plantFragile);
            mNoCraterCheck = LawnCommon.makeNewCheckbox(ID_CHECK_NO_CRATER, this, noCrater);
            mMushroomAwakeCheck = LawnCommon.makeNewCheckbox(ID_CHECK_MUSHROOM_AWAKE, this, mushroomAwake);
            mCheckboxLabels.put(mCobNoCdCheck, "玉米炮无冷却");
            mCheckboxLabels.put(mMagnetNoCdCheck, "磁力菇无冷却");
            mCheckboxLabels.put(mPotatoNoCdCheck, "土豆雷无冷却");
            mCheckboxLabels.put(mChomperNoCdCheck, "大嘴花无冷却");
            mCheckboxLabels.put(mPlantInvincibleCheck, "植物无敌");
            mCheckboxLabels.put(mPlantFragileCheck, "植物脆弱");
            mCheckboxLabels.put(mNoCraterCheck, "不留弹坑");
            mCheckboxLabels.put(mMushroomAwakeCheck, "蘑菇免唤醒");
            mKillPlantsButton = GameButton.makeButton(ID_KILL_PLANTS, this, "杀死全场植物");
            mClearMowersButton = GameButton.makeButton(ID_CLEAR_MOWERS, this, "清除全部小推车");
            mRestoreMowersButton = GameButton.makeButton(ID_RESTORE_MOWERS, this, "恢复全部小推车");
            mStartMowersButton = GameButton.makeButton(ID_START_MOWERS, this, "启动所有小推车");
            mSkipLevelButton = GameButton.makeButton(ID_SKIP_LEVEL, this, "跳关器");
            // 石钮按贴图原生尺寸摆放（drawStoneButton 从 (0,0) 原尺寸拼接，控件矩形小了会被裁）
            int aStoneHeight = stoneArtHeight();
            mInfiniteCoinsCheck.resize(0, 0, CHECKBOX_WIDTH, CHECKBOX_HEIGHT);
            mInfiniteFertilizerCheck.resize(0, 0, CHECKBOX_WIDTH, CHECKBOX_HEIGHT);
            mInfiniteBugSprayCheck.resize(0, 0, CHECKBOX_WIDTH, CHECKBOX_HEIGHT);
            mInfiniteChocolateCheck.resize(0, 0, CHECKBOX_WIDTH, CHECKBOX_HEIGHT);
            mInfiniteTreeFoodCheck.resize(0, 0, CHECKBOX_WIDTH, CHECKBOX_HEIGHT);
            for (LawnStoneButton anEffectButton : mEffectButtons) {
            }
            mRowSlider.resize(0, 0, 150, 34);
            mColSlider.resize(0, 0, 150, 34);
            mSpeedSlider.resize(0, 0, 150, 34);
            mHideSeedBankCheck.resize(0, 0, CHECKBOX_WIDTH, CHECKBOX_HEIGHT);
            mShowShovelCheck.resize(0, 0, CHECKBOX_WIDTH, CHECKBOX_HEIGHT);
            mNoCooldownCheck.resize(0, 0, CHECKBOX_WIDTH, CHECKBOX_HEIGHT);
            mHideMenuButtonCheck.resize(0, 0, CHECKBOX_WIDTH, CHECKBOX_HEIGHT);
            mCobNoCdCheck.resize(0, 0, CHECKBOX_WIDTH, CHECKBOX_HEIGHT);
            mMagnetNoCdCheck.resize(0, 0, CHECKBOX_WIDTH, CHECKBOX_HEIGHT);
            mPotatoNoCdCheck.resize(0, 0, CHECKBOX_WIDTH, CHECKBOX_HEIGHT);
            mChomperNoCdCheck.resize(0, 0, CHECKBOX_WIDTH, CHECKBOX_HEIGHT);
            mPlantInvincibleCheck.resize(0, 0, CHECKBOX_WIDTH, CHECKBOX_HEIGHT);
            mPlantFragileCheck.resize(0, 0, CHECKBOX_WIDTH, CHECKBOX_HEIGHT);
            mNoCraterCheck.resize(0, 0, CHECKBOX_WIDTH, CHECKBOX_HEIGHT);
            mMushroomAwakeCheck.resize(0, 0, CHECKBOX_WIDTH, CHECKBOX_HEIGHT);
            mRemoveFogCheck.resize(0, 0, CHECKBOX_WIDTH, CHECKBOX_HEIGHT);
            mVaseXrayCheck.resize(0, 0, CHECKBOX_WIDTH, CHECKBOX_HEIGHT);
            mSeedRainCheck.resize(0, 0, CHECKBOX_WIDTH, CHECKBOX_HEIGHT);
            mMiniZombieCheck.resize(0, 0, CHECKBOX_WIDTH, CHECKBOX_HEIGHT);
            mColumnCheck.resize(0, 0, CHECKBOX_WIDTH, CHECKBOX_HEIGHT);
            mZombieSpeedCheck.resize(0, 0, CHECKBOX_WIDTH, CHECKBOX_HEIGHT);
            mWhackCheck.resize(0, 0, CHECKBOX_WIDTH, CHECKBOX_HEIGHT);
            mNewtonCheck.resize(0, 0, CHECKBOX_WIDTH, CHECKBOX_HEIGHT);
            mGraveDangerCheck.resize(0, 0, CHECKBOX_WIDTH, CHECKBOX_HEIGHT);
            mStormyNightCheck.resize(0, 0, CHECKBOX_WIDTH, CHECKBOX_HEIGHT);
            mBungeeBlitzCheck.resize(0, 0, CHECKBOX_WIDTH, CHECKBOX_HEIGHT);
            mZombieExplodeCheck.resize(0, 0, CHECKBOX_WIDTH, CHECKBOX_HEIGHT);
            mZombieNeverExplodeCheck.resize(0, 0, CHECKBOX_WIDTH, CHECKBOX_HEIGHT);
            mZombieNoChillCheck.resize(0, 0, CHECKBOX_WIDTH, CHECKBOX_HEIGHT);
            mZombieNoButterCheck.resize(0, 0, CHECKBOX_WIDTH, CHECKBOX_HEIGHT);
            mZombieHideCheck.resize(0, 0, CHECKBOX_WIDTH, CHECKBOX_HEIGHT);
            mZombieShowCheck.resize(0, 0, CHECKBOX_WIDTH, CHECKBOX_HEIGHT);
            mZombieNoDancersCheck.resize(0, 0, CHECKBOX_WIDTH, CHECKBOX_HEIGHT);
            mZombieNoIceTrailCheck.resize(0, 0, CHECKBOX_WIDTH, CHECKBOX_HEIGHT);
            mZombieInvincibleCheck.resize(0, 0, CHECKBOX_WIDTH, CHECKBOX_HEIGHT);
            mZombieWeakCheck.resize(0, 0, CHECKBOX_WIDTH, CHECKBOX_HEIGHT);
            mZombieNoImpThrowCheck.resize(0, 0, CHECKBOX_WIDTH, CHECKBOX_HEIGHT);
            mZombieGargEdibleCheck.resize(0, 0, CHECKBOX_WIDTH, CHECKBOX_HEIGHT);
            mZombieFrozenCheck.resize(0, 0, CHECKBOX_WIDTH, CHECKBOX_HEIGHT);
            mZombieBossFrozenCheck.resize(0, 0, CHECKBOX_WIDTH, CHECKBOX_HEIGHT);
            mZombieBalloonBoomCheck.resize(0, 0, CHECKBOX_WIDTH, CHECKBOX_HEIGHT);
            mZombieVacuumCheck.resize(0, 0, CHECKBOX_WIDTH, CHECKBOX_HEIGHT);
            mVacuumColSlider.resize(0, 0, 150, 34);
            mTabButton = new LawnStoneButton(null, ID_TAB, this) {
                {
                    // 打开时恢复上次会话记住的标签页名（mCurrentTab 已是构造恢复值）
                    setLabel(PAGE_NAMES[mCurrentTab]);
                    mTranslateX = 1;
                    mTranslateY = 1;
                    mHasAlpha = true;
                    mHasTransparencies = true;
                }

                @Override
                public void draw(Graphics g) {
                    if (mBtnNoDraw) {
                        return;
                    }
                    // 全宽石钮：整条缩放到控件矩形（原生 drawStoneButton 按中块整数拼接，
                    // 宽度非整数块会留空）；悬停换亮绿字体，按下文字位移 1px
                    Image aStrip = stoneStrip();
                    if (aStrip == null) {
                        return; // 贴图未装载
                    }
                    boolean isDown = (mIsDown && mIsOver && !mDisabled) ^ mInverted;
                    g.drawImage(aStrip, new cn.enaium.fabricpvz.portable.sexy.Rect(0, 0, mWidth, mHeight),
                            new cn.enaium.fabricpvz.portable.sexy.Rect(0, 0, aStrip.getWidth(),
                                    aStrip.getHeight()));
                    Font aFont = cn.enaium.fabricpvz.portable.lawn.Resources.getFontById(mIsOver
                            ? cn.enaium.fabricpvz.portable.lawn.Resources.FONT_DWARVENTODCRAFT18BRIGHTGREENINSET_ID
                            : cn.enaium.fabricpvz.portable.lawn.Resources.FONT_DWARVENTODCRAFT18GREENINSET_ID);
                    Font aMeasure = cn.enaium.fabricpvz.portable.lawn.Resources
                            .getFontById(cn.enaium.fabricpvz.portable.lawn.Resources.FONT_DWARVENTODCRAFT18GREENINSET_ID);
                    int aFontX = (mWidth - aMeasure.stringWidth(mLabel)) / 2 + 1;
                    int aFontY = (mHeight - (aMeasure.getAscent() / 6 + 1) + aMeasure.getAscent()) / 2 - 4;
                    if (isDown) {
                        aFontX += mTranslateX;
                        aFontY += mTranslateY;
                    }
                    g.setFont(aFont);
                    g.setColor(cn.enaium.fabricpvz.portable.sexy.Color.White);
                    g.drawString(mLabel, aFontX, aFontY);
                }
            };
            // 页序：关卡、卡槽、植物、僵尸、花园、场地、特效、状态、用户、其他。
            // 各页条目规则：滑块最前 → 勾选框 → 组合项（滑块/成对石钮组）→ 孤立石钮；
            // 同类之间按条目字数升序（稳定排序，同长保持定义序）
            mTabPages.add(java.util.List.of(mSpeedSlider,
                    mPlantCheck, mCollectCheck, mPauseCheck, mNoSunDropsCheck, mNoCoinDropsCheck,
                    mAnyPlantingCheck,
                    mWinLevelButton, mAdvanceButton, mNextWaveButton));
            mTabPages.add(java.util.List.of(mSunCheck, mHideSeedBankCheck, mShowShovelCheck,
                    mNoCooldownCheck, mHideMenuButtonCheck,
                    mSlotPickButton, mSlotSeedButton, mSlotApplyButton,
                    mCardPickButton, mCardAttackButton, mCardPriceButton, mCardCooldownButton,
                    mSetSunButton, mSlotCountButton)); // 卡槽页
            java.util.List<Widget> aPlantItems = new java.util.ArrayList<>(java.util.List.of(
                    mPlantInvincibleCheck, mPlantFragileCheck, mNoCraterCheck, mMushroomAwakeCheck,
                    mCobNoCdCheck, mMagnetNoCdCheck, mPotatoNoCdCheck, mChomperNoCdCheck));
            aPlantItems.sort(java.util.Comparator.comparingInt(
                    aWidget -> mCheckboxLabels.get(aWidget).length()));
            mTabPages.add(aPlantItems); // 植物页
            java.util.List<Widget> aZombieItems = new java.util.ArrayList<>(java.util.List.of(
                    mZombieExplodeCheck, mZombieNeverExplodeCheck, mZombieHideCheck, mZombieShowCheck,
                    mZombieNoIceTrailCheck, mZombieInvincibleCheck, mZombieWeakCheck,
                    mZombieNoImpThrowCheck, mZombieFrozenCheck, mZombieBossFrozenCheck,
                    mZombieBalloonBoomCheck, mZombieGargEdibleCheck, mZombieNoChillCheck,
                    mZombieNoButterCheck, mZombieNoDancersCheck));
            aZombieItems.sort(java.util.Comparator.comparingInt(
                    aWidget -> mCheckboxLabels.get(aWidget).length()));
            aZombieItems.add(mZombieVacuumCheck);
            aZombieItems.add(mVacuumColSlider);
            aZombieItems.add(mZombieOpButton);
            aZombieItems.add(mZombieApplyButton);
            mTabPages.add(aZombieItems); // 僵尸页
            java.util.List<Widget> aGardenItems = new java.util.ArrayList<>(java.util.List.of(
                    mInfiniteFertilizerCheck, mInfiniteTreeFoodCheck, mInfiniteBugSprayCheck,
                    mInfiniteChocolateCheck));
            aGardenItems.sort(java.util.Comparator.comparingInt(
                    aWidget -> mCheckboxLabels.get(aWidget).length()));
            aGardenItems.add(mTreeHeightButton);
            aGardenItems.add(mAddMarigoldButton);
            aGardenItems.add(mAddSproutButton);
            aGardenItems.add(mAddBigPlantButton);
            mTabPages.add(aGardenItems); // 花园页
            mTabPages.add(java.util.List.of(mRemoveFogCheck, mVaseXrayCheck,
                    mRowSlider, mColSlider,
                    mFieldSeedButton, mFieldPlantButton,
                    mFieldZombieButton, mFieldZombiePlaceButton,
                    mFieldItemButton, mFieldItemPlaceButton,
                    mFieldMusicButton, mFieldMusicApplyButton,
                    mFieldClearGravesButton)); // 场地页：浓雾/透视在前，行列滑块紧贴种植组合
            java.util.List<Widget> anEffectChecks = new java.util.ArrayList<>(java.util.List.of(
                    mSeedRainCheck, mMiniZombieCheck, mColumnCheck, mZombieSpeedCheck, mWhackCheck,
                    mNewtonCheck, mGraveDangerCheck, mStormyNightCheck, mBungeeBlitzCheck));
            anEffectChecks.sort(java.util.Comparator.comparingInt(
                    aWidget -> mCheckboxLabels.get(aWidget).length()));
            java.util.List<Widget> anEffectButtons = new java.util.ArrayList<>();
            for (LawnStoneButton anEffectButton : mEffectButtons) {
                anEffectButtons.add(anEffectButton);
            }
            anEffectButtons.sort(java.util.Comparator.comparingInt(
                    aWidget -> ((LawnStoneButton) aWidget).mLabel.length()));
            java.util.List<Widget> anEffectItems = new java.util.ArrayList<>(anEffectChecks);
            anEffectItems.addAll(anEffectButtons);
            mTabPages.add(anEffectItems); // 特效页：勾选框在前（按本地化名长度），石钮在后（按英文名长度）
            mTabPages.add(java.util.List.of()); // 状态页：纯展示，无控件
            java.util.List<Widget> anUserItems = new java.util.ArrayList<>();
            anUserItems.add(mInfiniteCoinsCheck);
            java.util.List<Widget> anUserButtons = new java.util.ArrayList<>(java.util.List.of(
                    mSetCoinsButton, mUnlockSunflowerButton, mTmpUnlockButton));
            anUserButtons.sort(java.util.Comparator.comparingInt(
                    aWidget -> ((LawnStoneButton) aWidget).mLabel.length()));
            anUserItems.addAll(anUserButtons);
            mTabPages.add(anUserItems); // 用户页
            java.util.List<Widget> aMiscItems = new java.util.ArrayList<>(java.util.List.of(
                    mKillButton, mKillPlantsButton, mClearMowersButton, mRestoreMowersButton,
                    mStartMowersButton, mSkipLevelButton));
            aMiscItems.sort(java.util.Comparator.comparingInt(
                    aWidget -> ((LawnStoneButton) aWidget).mLabel.length()));
            mTabPages.add(aMiscItems); // 其他页

            // 固定高度（标签行 + 约四行内容）；内容更高，出滚动条滑动。
            // 标签行虽抬到标题下（省 40px），但行高+间距仍把视口顶推下一段，高度补回
            calcSize(80, aStoneHeight + 6 + 4 * ROW_HEIGHT + 56 + aStoneHeight + 6 - 40);

            // addDialog 只在 mWidth==0 时自动摆放，calcSize 后已是非零——自己摆放：
            // 会话内收起过就原地恢复；否则跟随悬浮按钮下方（按钮可拖动，位置跟着走），
            // 两种来源都钳制在窗口边界内
            int aX;
            int aY;
            if (sDialogPositionSaved) {
                aX = sLastDialogX;
                aY = sLastDialogY;
            } else if (sButton != null) {
                aX = sButton.mX;
                aY = sButton.mY + sButton.mHeight + 4;
            } else {
                aX = Math.max(8, (sApp.mWidth - mWidth) / 2);
                aY = Math.max(16, (sApp.mHeight - mHeight) / 2 - 12);
            }
            aX = Math.max(0, Math.min(aX, sApp.mWidth - mWidth));
            aY = Math.max(0, Math.min(aY, sApp.mHeight - mHeight));
            resize(aX, aY, mWidth, mHeight);
        }

        @Override
        public void addedToManager(WidgetManager theWidgetManager) {
            super.addedToManager(theWidgetManager);
            // 复选框与石钮挂进滚动视口（坐标相对视口），标签页钮留在对话框上，焦点代理代收滚轮
            // （可见性由构造期 layoutContent 定好——这里再统一隐藏会把当前页也灭掉）
            for (java.util.List<Widget> aPage : mTabPages) {
                for (Widget aWidget : aPage) {
                    mViewport.addWidget(aWidget);
                }
            }
            addWidget(mViewport);
            addWidget(mTabButton);
            addWidget(mFocusProxy);
            theWidgetManager.setFocus(mFocusProxy);
        }

        @Override
        public void removedFromManager(WidgetManager theWidgetManager) {
            // 关闭（含 killDialog）时记住当前标签页、滚动位置与对话框位置，供下次打开恢复
            sLastTab = mCurrentTab;
            sTabScrollOffsets[Math.min(mCurrentTab, sTabScrollOffsets.length - 1)] = mScrollOffset;
            saveDialogPosition();
            // 焦点归还：addedToManager 里 setFocus(mFocusProxy) 会被 addBaseModal 记为
            // "先前焦点"（下方的图鉴等界面关闭时把焦点恢复到这个已死亡的子控件），
            // 图鉴的 GameButton 因焦点 != 图鉴对话框全部按不动——关闭修改器时把焦点
            // 显式还给仍然在场的最上层界面（图鉴），没有则清空
            if (sPrevFocus != null && sPrevFocus.mWidgetManager != null) {
                theWidgetManager.setFocus(sPrevFocus);
            } else if (theWidgetManager.mBaseModalWidget != null) {
                theWidgetManager.setFocus(theWidgetManager.mBaseModalWidget);
            } else {
                theWidgetManager.setFocus(null);
            }
            sPrevFocus = null;
            for (java.util.List<Widget> aPage : mTabPages) {
                for (Widget aWidget : aPage) {
                    mViewport.removeWidget(aWidget);
                }
            }
            removeWidget(mViewport);
            removeWidget(mTabButton);
            removeWidget(mFocusProxy);
            closeTabMenu(); // 下拉菜单是子控件，removeWidget 里已随之摘除——置空引用
            super.removedFromManager(theWidgetManager);
        }

    /** 场地页弹出列表的公共入口：先关旧的再开新的，位置在锚点按钮下。 */
    private void openFieldPopup(String[] theItems, int theSelectedIndex,
            java.util.function.IntConsumer theOnSelect, LawnStoneButton theAnchor) {
        openListPopup(new ListPopupWidget(theItems, theSelectedIndex, theOnSelect),
                theAnchor.mX, theAnchor.mY + theAnchor.mHeight, theAnchor.mWidth);
    }

    /** 种植：把当前选择的种子（可带模仿者标记）种到当前行列。 */
    private void plantSelectedSeed() {
        Board aBoard = sBoard;
        if (aBoard == null) {
            show(sBoard, "请在关卡内使用");
            return;
        }
        aBoard.addPlant(sFieldCol - 1, sFieldRow - 1, sFieldSeed,
                cn.enaium.fabricpvz.portable.lawn.SeedType.SEED_NONE);
        show(sBoard, "已种植 " + seedName(sFieldSeed) + " 到 " + sFieldRow + " 行 " + sFieldCol + " 列");
    }

    /** 场地页物品列表：墓碑/干草叉（网格物）+ 全部原版可掉落物（CoinType）。 */
    // 不含 COIN_FINAL_SEED_PACKET(7) / COIN_USABLE_SEED_PACKET(16)：放置这两类会触发
    // 原版缺陷导致游戏崩溃
    private static final String[] FIELD_ITEM_NAMES = {"墓碑", "干草叉", "银币", "金币", "钻石",
            "阳光", "小阳光", "大阳光", "钱袋", "巧克力", "礼物",
            "奖杯", "铲子", "图鉴", "车钥匙", "花瓶", "浇水壶", "玉米卷", "纸条",
            "银向日葵奖杯", "金向日葵奖杯", "钻石袋", "奖励巧克力"};
    private static final int[] FIELD_ITEM_COIN_TYPES = {
            cn.enaium.fabricpvz.portable.lawn.CoinType.COIN_SILVER,
            cn.enaium.fabricpvz.portable.lawn.CoinType.COIN_GOLD,
            cn.enaium.fabricpvz.portable.lawn.CoinType.COIN_DIAMOND,
            cn.enaium.fabricpvz.portable.lawn.CoinType.COIN_SUN,
            cn.enaium.fabricpvz.portable.lawn.CoinType.COIN_SMALLSUN,
            cn.enaium.fabricpvz.portable.lawn.CoinType.COIN_LARGESUN,
            cn.enaium.fabricpvz.portable.lawn.CoinType.COIN_AWARD_MONEY_BAG,
            cn.enaium.fabricpvz.portable.lawn.CoinType.COIN_CHOCOLATE,
            cn.enaium.fabricpvz.portable.lawn.CoinType.COIN_PRESENT_PLANT,
            cn.enaium.fabricpvz.portable.lawn.CoinType.COIN_TROPHY,
            cn.enaium.fabricpvz.portable.lawn.CoinType.COIN_SHOVEL,
            cn.enaium.fabricpvz.portable.lawn.CoinType.COIN_ALMANAC,
            cn.enaium.fabricpvz.portable.lawn.CoinType.COIN_CARKEYS,
            cn.enaium.fabricpvz.portable.lawn.CoinType.COIN_VASE,
            cn.enaium.fabricpvz.portable.lawn.CoinType.COIN_WATERING_CAN,
            cn.enaium.fabricpvz.portable.lawn.CoinType.COIN_TACO,
            cn.enaium.fabricpvz.portable.lawn.CoinType.COIN_NOTE,
            cn.enaium.fabricpvz.portable.lawn.CoinType.COIN_AWARD_SILVER_SUNFLOWER,
            cn.enaium.fabricpvz.portable.lawn.CoinType.COIN_AWARD_GOLD_SUNFLOWER,
            cn.enaium.fabricpvz.portable.lawn.CoinType.COIN_AWARD_BAG_DIAMOND,
            cn.enaium.fabricpvz.portable.lawn.CoinType.COIN_AWARD_CHOCOLATE};

    /** 放置物品：墓碑 / 干草叉（网格物）+ 全部原版可掉落物（addCoin）到当前行列。 */
    private void placeSelectedItem() {
        Board aBoard = sBoard;
        if (aBoard == null) {
            show(sBoard, "请在关卡内使用");
            return;
        }
        int aCol = sFieldCol - 1;
        int aRow = sFieldRow - 1;
        String aName;
        if (sFieldItem == 0) {
            aBoard.addAGraveStone(aCol, aRow);
            aName = FIELD_ITEM_NAMES[0];
        } else if (sFieldItem == 1) {
            var aRake = aBoard.mGridItems.dataArrayAlloc();
            aRake.mGridItemType = cn.enaium.fabricpvz.portable.lawn.GridItemType.GRIDITEM_RAKE;
            aRake.mGridX = aCol;
            aRake.mGridY = aRow;
            aRake.mPosX = aBoard.gridToPixelX(aCol, aRow);
            aRake.mPosY = aBoard.gridToPixelY(aCol, aRow);
            aRake.mRenderOrder = Board.makeRenderOrder(RenderLayer.RENDER_LAYER_GRAVE_STONE, aRow, 9);
            aRake.mGridItemReanimID = sApp.reanimationGetID(aBoard.createRakeReanim(aRake.mPosX, aRake.mPosY, 0));
            aRake.mGridItemState = GridItemState.GRIDITEM_STATE_RAKE_ATTRACTING;
            aName = FIELD_ITEM_NAMES[1];
        } else {
            int aCoinType = FIELD_ITEM_COIN_TYPES[sFieldItem - 2];
            aBoard.addCoin(aBoard.gridToPixelX(aCol, aRow), aBoard.gridToPixelY(aCol, aRow),
                    aCoinType, cn.enaium.fabricpvz.portable.lawn.CoinMotion.COIN_MOTION_COIN);
            aName = FIELD_ITEM_NAMES[sFieldItem];
        }
        show(sBoard, aName + " 已放置到 " + sFieldRow + " 行 " + sFieldCol + " 列");
    }

        /**
         * 每帧自检：跳关器/设置金币这类叠加模态框关闭后，管理器留下的焦点指针悬空指向
         * 已移除的输入框（disableWidget 只清"焦点 == 被移除控件本身"的情况），滚轮派发
         * 随之落空——焦点链不再到达管理器时把焦点还给代理，滚动即恢复。
         */
        @Override
        public void update() {
            WidgetManager aManager = mWidgetManager;
            if (aManager == null) {
                return;
            }
            // 弹出列表开着时按在菜单与对话框之外（含棋盘等对话框外区域）：收起。
            // 管理器把 mouseDown 派发给指针下的控件，对话框听不到外面的点击——
            // 用"有按键按下 + 指针在两者之外"的几何检测补上
            if (mTabMenuWidget != null && aManager.mDownButtons != 0) {
                var aMenuPos = mTabMenuWidget.getAbsPos();
                var aDialogPos = this.getAbsPos();
                boolean aInMenu = aManager.mLastMouseX >= aMenuPos.mX
                        && aManager.mLastMouseX < aMenuPos.mX + mTabMenuWidget.mWidth
                        && aManager.mLastMouseY >= aMenuPos.mY
                        && aManager.mLastMouseY < aMenuPos.mY + mTabMenuWidget.mHeight;
                boolean aInDialog = aManager.mLastMouseX >= aDialogPos.mX
                        && aManager.mLastMouseX < aDialogPos.mX + this.mWidth
                        && aManager.mLastMouseY >= aDialogPos.mY
                        && aManager.mLastMouseY < aDialogPos.mY + this.mHeight;
                if (!aInMenu && !aInDialog) {
                    closeTabMenu();
                }
            }
            // 焦点自检只在"本对话框是栈顶界面"时执行：图鉴/商店等叠加模态打开时焦点属于
            // 它们（GameButton.isMouseOver 要求焦点==父对话框），每帧抢回会让叠加界面的
            // 所有石钮按不动。栈顶判定：管理器的基础模态就是本对话框，或本对话框在
            // 控件树末位（无叠加时即最上层）。
            boolean aOnTop = aManager.mBaseModalWidget == this
                    || (!aManager.mWidgets.isEmpty()
                        && aManager.mWidgets.get(aManager.mWidgets.size() - 1) == this);
            if (aOnTop) {
                Widget aFocus = aManager.mFocusWidget;
                boolean aFocusValid = false;
                for (cn.enaium.fabricpvz.portable.sexy.WidgetContainer aWidget = aFocus;
                        aWidget != null; aWidget = aWidget.mParent) {
                    if (aWidget == aManager) {
                        aFocusValid = true;
                        break;
                    }
                }
                if (!aFocusValid) {
                    aManager.setFocus(mFocusProxy);
                }
            }
            // "界面不支持"提示到期，恢复"修改音乐"钮原文字
            if (mMusicLabelRestoreAt > 0 && System.currentTimeMillis() >= mMusicLabelRestoreAt) {
                mMusicLabelRestoreAt = 0;
                mFieldMusicApplyButton.setLabel(mMusicLabelOriginal);
                markDirty();
            }
            if (mCurrentTab == STATUS_TAB) {
                markDirty(); // 状态页实时刷新
            }
        }

        @Override
        public void resize(int theX, int theY, int theWidth, int theHeight) {
            super.resize(theX, theY, theWidth, theHeight); // 基类排标题与 footer 按钮

            int aLeft = getLeft();
            int aTop = getTop();
            // 标签行贴标题但留出呼吸：getTop() 含 99px 头部偏移，标题字形最低到 ~100px；
            // 全宽石钮——尺寸必须显式给足（原生按钮默认 0×0，会被自己的 mClip 裁没）
            int aTabY = Math.max(80, aTop - 20);
            mTabButton.resize(aLeft, aTabY, getWidth(), Math.max(33, stoneArtHeight()));
            mContentTop = aTabY + mTabButton.mHeight + 6;
            // 视口：从标签行下缘到"收 起"按钮上缘再收 26px（底部边距带的实际视觉边界比
            // resizeBase 的公式位置更靠上，贴公式会溢出到石框带上）
            int aFooterTop = mHeight - mContentInsets.mBottom - mBackgroundInsets.mBottom
                    - mKillButton.mHeight + 2;
            mViewportHeight = Math.max(ROW_HEIGHT,
                    aFooterTop - 44 - mContentTop);
            mViewport.resize(aLeft, mContentTop,
                    getWidth() - SCROLLBAR_WIDTH - 4, mViewportHeight);
            // 石钮全宽 = 视口宽再扣除行/列滑块的文字边距（与 layoutContent 的居中基准一致）
            mFullContentWidth = mViewport.mWidth - 60;
            layoutContent();
            if (mScrollOffset > maxContentScroll()) { // 恢复的位置可能超出内容，钳制
                mScrollOffset = maxContentScroll();
                layoutContent();
            }
        }

        /** 状态页的 12 行展示内容（标签：值），棋盘不存在时显示 —。 */
        private String[] buildStatusRows() {
            Board aBoard = sBoard;
            java.util.List<String> aRows = new java.util.ArrayList<>();
            aRows.add("游戏模式：" + gameModeName());
            aRows.add("游戏界面：" + gameSceneName());
            if (aBoard == null) {
                for (String aLabel : new String[] {"进行时间", "完成轮数", "本轮总波数", "已刷新波数",
                        "本波总血量", "刷新触发血量", "刷新倒计时", "大波刷新倒计时",
                        "植物数量", "僵尸数量"}) {
                    aRows.add(aLabel + "：—");
                }
                return aRows.toArray(new String[0]);
            }
            aRows.add("进行时间：" + (aBoard.mMainCounter / 100 / 60) + " 分 "
                    + (aBoard.mMainCounter / 100 % 60) + " 秒");
            aRows.add("完成轮数：" + aBoard.mTotalSpawnedWaves);
            aRows.add("本轮总波数：" + aBoard.mNumWaves);
            aRows.add("已刷新波数：" + aBoard.mCurrentWave);
            aRows.add("本波总血量："
                    + (aBoard.mCurrentWave > 0 ? aBoard.totalZombiesHealthInWave(aBoard.mCurrentWave - 1) : 0));
            aRows.add("刷新触发血量：" + aBoard.mZombieHealthToNextWave);
            aRows.add("刷新倒计时：" + aBoard.mZombieCountDown);
            aRows.add("大波刷新倒计时：" + aBoard.mHugeWaveCountDown);
            int aPlants = 0;
            for (Plant aPlant : aBoard.mPlants) {
                if (!aPlant.mDead) {
                    aPlants++;
                }
            }
            aRows.add("植物数量：" + aPlants);
            int aZombies = 0;
            for (Zombie aZombie : aBoard.mZombies) {
                if (!aZombie.mDead) {
                    aZombies++;
                }
            }
            aRows.add("僵尸数量：" + aZombies);
            return aRows.toArray(new String[0]);
        }

        /** 游戏模式名（选关定义表按模式查，翻译 LawnStrings 键；无定义回落"模式 N"）。 */
        private String gameModeName() {
            for (cn.enaium.fabricpvz.portable.lawn.widget.ChallengeDefinition aDef
                    : cn.enaium.fabricpvz.portable.lawn.widget.ChallengeScreen.gChallengeDefs) {
                if (aDef.mChallengeMode == sApp.mGameMode) {
                    return cn.enaium.fabricpvz.portable.lawn.widget.ChallengeScreen
                            .resolveChallengeName(aDef.mChallengeName);
                }
            }
            return "模式 " + sApp.mGameMode;
        }

        /** 游戏界面名。 */
        private String gameSceneName() {
            return switch (sApp.mGameScene) {
                case GameScenes.SCENE_LOADING -> "加载中";
                case GameScenes.SCENE_MENU -> "主菜单";
                case GameScenes.SCENE_LEVEL_INTRO -> "关卡准备";
                case GameScenes.SCENE_PLAYING -> "关卡进行中";
                case GameScenes.SCENE_ZOMBIES_WON -> "僵尸获胜";
                case GameScenes.SCENE_AWARD -> "奖励结算";
                case GameScenes.SCENE_CREDIT -> "制作人员";
                case GameScenes.SCENE_CHALLENGE -> "挑战选关";
                default -> "场景 " + sApp.mGameScene;
            };
        }

        /** 按滚动偏移重排当前页的子控件（水平居中、y 平移），页外控件隐藏。 */
        private void layoutContent() {
            int aY = -mScrollOffset;
            java.util.List<Widget> aPage = mTabPages.get(mCurrentTab);
            for (Widget aWidget : aPage) {
                // 控件默认 mClip=true，尺寸必须显式给足（0×0 会把绘制全裁掉）：
                // 复选框以"框+文字"整组居中（基准扣除右侧滚动条带，向左微调），
                // 石钮以自身宽在视口内居中
                if (aWidget instanceof Checkbox aCheck) {
                    Font aFont = cn.enaium.fabricpvz.portable.lawn.Resources
                            .getFontById(cn.enaium.fabricpvz.portable.lawn.Resources.FONT_BRIANNETOD12_ID);
                    String aName = mCheckboxLabels.getOrDefault(aCheck, "");
                    int aGroupWidth = CHECKBOX_WIDTH + 10 + aFont.stringWidth(aName);
                    int aCenterWidth = mViewport.mWidth - SCROLLBAR_WIDTH - 2;
                    aCheck.resize(Math.max(0, (aCenterWidth - aGroupWidth) / 2), aY,
                            CHECKBOX_WIDTH, CHECKBOX_HEIGHT);
                } else if (aWidget instanceof Slider aSlider) {
                    // 滑块：标签占左侧 48px，滑块 120px，组整体在视口居中
                    int aGroupWidth = 48 + 120;
                    aSlider.resize(Math.max(0, (mViewport.mWidth - aGroupWidth) / 2) + 48, aY,
                            120, aSlider.mHeight);
                } else {
                    // 石钮铺满：drawStoneButton 按原生三段拼接（非中块整数倍会右侧留空），
                    // 宽度取整到整数个中块再居中；高度用石钮贴图原生高（构造期未定型的
                    // 控件 mHeight 是 0，直接回填会被自己的 mClip 裁没）
                    int aStoneWidth = stoneSnappedWidth(mFullContentWidth);
                    int aStoneHeight = Math.max(aWidget.mHeight, stoneArtHeight());
                    aWidget.resize(Math.max(0, (mViewport.mWidth - aStoneWidth) / 2), aY,
                            aStoneWidth, aStoneHeight);
                }
                // 整行滚出视口的控件不可见——mVisible=false 同时不参与命中测试，
                // 免得点"收 起"等底部区域时误触滚出去的隐藏按钮
                aWidget.mVisible = aY + aWidget.mHeight > 0 && aY < mViewportHeight;
                aY += aWidget instanceof Checkbox ? ROW_HEIGHT : aWidget.mHeight + 10;
            }
            mContentHeight = aY + mScrollOffset - 10; // 去掉最后一项的尾距
            if (mCurrentTab == STATUS_TAB) {
                mContentHeight = STATUS_ROW_COUNT * 20; // 纯展示页按行数计，超出可滚动
            }
            for (int i = 0; i < mTabPages.size(); i++) {
                if (i == mCurrentTab) {
                    continue;
                }
                for (Widget aWidget : mTabPages.get(i)) {
                    aWidget.mVisible = false;
                }
            }
        }

        /** 内容可滚动的最大距离。 */
        private int maxContentScroll() {
            return Math.max(0, mContentHeight - mViewportHeight);
        }

        /** 滚动内容（滚轮正值向上 / 拖动条直接设偏移共用入口），并重排子控件。 */
        private void scrollContent(int theDelta) {
            // 页名下拉菜单开着时吞掉滚轮：滚轮事件只派发给焦点控件（焦点代理还持有），
            // 不拦的话会透传滚动被菜单盖住的页面内容
            if (mTabMenuWidget != null) {
                return;
            }
            mScrollOffset = Math.max(0, Math.min(maxContentScroll(), mScrollOffset + theDelta));
            layoutContent();
            markDirty();
        }

        /** 滚动条 thumb 的高与 y（视口坐标系）。 */
        private int thumbHeight() {
            int aTrack = mViewportHeight - 2;
            if (mContentHeight <= 0) {
                return aTrack;
            }
            return Math.max(24, aTrack * mViewportHeight / mContentHeight);
        }

        private int thumbY() {
            int aMax = maxContentScroll();
            if (aMax == 0) {
                return 1;
            }
            int aTrack = mViewportHeight - 2 - thumbHeight();
            return 1 + aTrack * mScrollOffset / aMax;
        }

        @Override
        public void draw(Graphics g) {
            super.draw(g);

            // 滚动条：内容超出视口才画（轨道贴视口右缘）
            if (maxContentScroll() > 0) {
                int aTrackX = mViewport.mX + mViewport.mWidth + 2;
                g.setColor(new cn.enaium.fabricpvz.portable.sexy.Color(0, 0, 0, 90));
                g.fillRect(aTrackX, mViewport.mY, SCROLLBAR_WIDTH, mViewportHeight);
                g.setColor(new cn.enaium.fabricpvz.portable.sexy.Color(200, 196, 210, 200));
                g.fillRect(aTrackX + 1, mViewport.mY + thumbY(), SCROLLBAR_WIDTH - 2, thumbHeight());
            }
        }

        @Override
        public void mouseDown(int theX, int theY, int theBtnNum, int theClickCount) {
            // 页名菜单开着时点在菜单外（对话框自身区域）：先收起再继续本次点击
            if (mTabMenuWidget != null && !mTabMenuWidget.contains(
                    theX - mTabMenuWidget.mX, theY - mTabMenuWidget.mY)) {
                closeTabMenu();
            }
            // 滚动条区域：抓 thumb 或点轨道跳转（对话框局部坐标）
            int aTrackX = mViewport.mX + mViewport.mWidth + 2;
            if (maxContentScroll() > 0 && theX >= aTrackX
                    && theY >= mViewport.mY && theY < mViewport.mY + mViewportHeight) {
                mScrollbarDragging = true;
                int aThumbTop = mViewport.mY + thumbY();
                mThumbGrabOffset = (theY >= aThumbTop && theY < aThumbTop + thumbHeight())
                        ? theY - aThumbTop : thumbHeight() / 2;
                dragScrollbar(theY);
                return;
            }
            // 按在对话框自身区域（标题/面板等子控件未接住的地方）：开始拖动
            mDialogDragging = true;
            mDialogGrabOffsetX = mWidgetManager.mLastMouseX - mX;
            mDialogGrabOffsetY = mWidgetManager.mLastMouseY - mY;
            super.mouseDown(theX, theY, theBtnNum, theClickCount);
        }

        @Override
        public void mouseDrag(int theX, int theY) {
            if (mScrollbarDragging) {
                dragScrollbar(theY);
                return;
            }
            if (mDialogDragging) {
                // 与悬浮按钮同款：以全局指针位置吸附（mLastMouseX/Y 是 public），钳制在窗口内
                int aX = Math.max(0, Math.min(mWidgetManager.mLastMouseX - mDialogGrabOffsetX,
                        sApp.mWidth - mWidth));
                int aY = Math.max(0, Math.min(mWidgetManager.mLastMouseY - mDialogGrabOffsetY,
                        sApp.mHeight - mHeight));
                resize(aX, aY, mWidth, mHeight);
                markDirty();
                return;
            }
            super.mouseDrag(theX, theY);
        }

        @Override
        public void mouseUp(int theX, int theY, int theBtnNum, int theClickCount) {
            mScrollbarDragging = false;
            if (mDialogDragging) {
                mDialogDragging = false;
                saveDialogPosition();
            }
            super.mouseUp(theX, theY, theBtnNum, theClickCount);
        }

        /** 收起/拖动结束时把当前位置写进会话记忆（下次展开原地恢复）。 */
        private void saveDialogPosition() {
            sLastDialogX = mX;
            sLastDialogY = mY;
            sDialogPositionSaved = true;
        }

        private void dragScrollbar(int theY) {
            int aMax = maxContentScroll();
            if (aMax <= 0) {
                return;
            }
            int aTrack = mViewportHeight - 2 - thumbHeight();
            mScrollOffset = Math.max(0, Math.min(aMax,
                    (theY - mThumbGrabOffset - mViewport.mY - 1) * aMax / aTrack));
            layoutContent();
            markDirty();
        }

        /** 切换标签页：记住旧页滚动位置、恢复新页的，重置滚动、重排内容，并把下拉按钮文字换成当前页名。 */
        private void switchTab(int theTab) {
            sTabScrollOffsets[Math.min(mCurrentTab, sTabScrollOffsets.length - 1)] = mScrollOffset;
            mCurrentTab = theTab;
            mScrollOffset = sTabScrollOffsets[Math.min(theTab, sTabScrollOffsets.length - 1)];
            layoutContent();
            if (mScrollOffset > maxContentScroll()) { // 恢复的位置可能超出新页内容，钳制
                mScrollOffset = maxContentScroll();
                layoutContent();
            }
            mTabButton.setLabel(PAGE_NAMES[mCurrentTab]);
            markDirty();
        }

        /** 打开页名下拉菜单：挂在对话框自身下（关卡内对话框是模态，管理器根上的浮层会被
         * 模态标志剥掉鼠标/绘制权限——主菜单能弹、关卡里弹不出的根源），宽随下拉按钮。 */
        private void openTabMenu() {
            if (mTabMenuWidget != null) {
                return;
            }
            openListPopup(new ListPopupWidget(PAGE_NAMES, mCurrentTab, this::switchTab),
                    mTabButton.mX, mTabButton.mY + mTabButton.mHeight, mTabButton.mWidth);
        }

        /** 在锚点（对话框局部坐标）下方打开弹出列表：水平在对话框内居中，超出下缘改到
         * 锚点上方；焦点交给列表。 */
        private void openListPopup(ListPopupWidget theMenu, int theX, int theY, int theWidth) {
            if (mTabMenuWidget != null) {
                closeTabMenu();
            }
            mTabMenuWidget = theMenu;
            addWidget(theMenu);
            // 水平居中基准扣除对话框自身滚动条带（只影响弹出列表，不碰其他布局）
            int aCenterWidth = mWidth - SCROLLBAR_WIDTH - 2;
            int aX = Math.max(0, (aCenterWidth - theWidth) / 2);
            int aY = theY;
            if (aY + theMenu.mHeight > mHeight - 56) {
                aY = Math.max(0, theY - theMenu.mHeight - theMenu.mHeight / 2 - 8);
            }
            theMenu.resize(aX, aY, theWidth, theMenu.mHeight);
            mWidgetManager.setFocus(theMenu); // 滚轮交给列表滚动
            markDirty();
        }

        /** 收起打开中的弹出列表（幂等 + 重入安全），焦点交还焦点代理。 */
        private void closeTabMenu() {
            if (mTabMenuWidget == null || mClosingTabMenu) {
                return;
            }
            mClosingTabMenu = true;
            try {
                removeWidget(mTabMenuWidget);
                mTabMenuWidget = null;
                if (mWidgetManager != null) {
                    mWidgetManager.setFocus(mFocusProxy);
                }
                markDirty();
            } finally {
                mClosingTabMenu = false;
            }
        }

        /**
         * 弹出列表：对话框的子控件（加在末位 = 盖在其他内容上），行点击回调；超过可见行数
         * 时滚轮滚动（焦点由 openListPopup 交给列表）。页名菜单与场地页各下拉共用。
         */
        private final class ListPopupWidget extends Widget {

            private static final int ROW_HEIGHT = 32;
            private static final int MAX_VISIBLE_ROWS = 6;

            private final String[] mItems;
            private final java.util.function.IntConsumer mOnSelect;
            private final int mSelectedIndex;
            private int mScrollOffset;
            private int mHoverRow = -1;
            private boolean mClosePending;

            ListPopupWidget(String[] theItems, int theSelectedIndex,
                    java.util.function.IntConsumer theOnSelect) {
                mItems = theItems;
                mSelectedIndex = theSelectedIndex;
                mOnSelect = theOnSelect;
                mClip = true;
                Font aFont = cn.enaium.fabricpvz.portable.lawn.Resources
                        .getFontById(cn.enaium.fabricpvz.portable.lawn.Resources.FONT_BRIANNETOD12_ID);
                int aNameWidth = 0;
                for (String aName : theItems) {
                    aNameWidth = Math.max(aNameWidth, aFont.stringWidth(aName));
                }
                resize(0, 0, Math.max(90, aNameWidth + 34),
                        Math.min(theItems.length, MAX_VISIBLE_ROWS) * ROW_HEIGHT);
                // 展开即自动定位：滚动到当前选中行（尽量居中），钳制进滚动范围；
                // 选中项超出视口时不再从顶部开始翻找
                int aVisibleRows = Math.max(1, mHeight / ROW_HEIGHT);
                mScrollOffset = Math.max(0, Math.min(theSelectedIndex - (aVisibleRows - 1) / 2,
                        mItems.length - aVisibleRows));
            }

            private int maxScroll() {
                return Math.max(0, mItems.length - mHeight / ROW_HEIGHT);
            }

            @Override
            public void draw(Graphics g) {
                g.setColor(new cn.enaium.fabricpvz.portable.sexy.Color(28, 26, 36, 245));
                g.fillRect(0, 0, mWidth, mHeight);
                g.setColor(new cn.enaium.fabricpvz.portable.sexy.Color(120, 118, 138));
                g.drawRect(0, 0, mWidth - 1, mHeight - 1);
                Font aFont = cn.enaium.fabricpvz.portable.lawn.Resources
                        .getFontById(cn.enaium.fabricpvz.portable.lawn.Resources.FONT_BRIANNETOD12_ID);
                g.setFont(aFont);
                int aVisible = mHeight / ROW_HEIGHT;
                for (int i = 0; i < aVisible && i + mScrollOffset < mItems.length; i++) {
                    int anIndex = i + mScrollOffset;
                    int aRowY = i * ROW_HEIGHT;
                    if (anIndex == mSelectedIndex) {
                        // 选中行常驻高亮带（比悬停更醒目），与亮色文字配套
                        g.setColor(new cn.enaium.fabricpvz.portable.sexy.Color(255, 200, 70, 120));
                        g.fillRect(2, aRowY + 1, mWidth - 4, ROW_HEIGHT - 2);
                    } else if (anIndex == mHoverRow) {
                        g.setColor(new cn.enaium.fabricpvz.portable.sexy.Color(255, 220, 80, 60));
                        g.fillRect(2, aRowY + 1, mWidth - 4, ROW_HEIGHT - 2);
                    }
                    g.setColor(new cn.enaium.fabricpvz.portable.sexy.Color(
                            anIndex == mSelectedIndex ? 255 : 176,
                            anIndex == mSelectedIndex ? 220 : 176, 190));
                    g.drawString(mItems[anIndex], 10,
                            aRowY + (ROW_HEIGHT - aFont.mHeight) / 2 + aFont.mAscent);
                }
            }

            @Override
            public void mouseWheel(int theDelta) {
                int aMax = maxScroll();
                int aNew = Math.max(0, Math.min(aMax, mScrollOffset - theDelta));
                if (aNew != mScrollOffset) {
                    mScrollOffset = aNew;
                    markDirty();
                }
            }

            @Override
            public void lostFocus() {
                // 取消焦点（点在其他任何控件上）即收起；若本次 lostFocus 由自身移除触发，
                // closeTabMenu 的重入守卫会直接返回
                mClosePending = true;
            }

            @Override
            public void mouseMove(int theX, int theY) {
                int aRow = theY / ROW_HEIGHT + mScrollOffset;
                if (aRow != mHoverRow) {
                    mHoverRow = aRow;
                    markDirty();
                }
            }

            @Override
            public void mouseDown(int theX, int theY, int theBtnNum, int theClickCount) {
                int anIndex = theY / ROW_HEIGHT + mScrollOffset;
                if (anIndex >= 0 && anIndex < mItems.length) {
                    mOnSelect.accept(anIndex);
                }
                // 不能在本 own mouseDown 派发中 removeWidget 自己——widgetRemovedHelper
                // 会在管理器迭代中把 mWidgetManager 置空导致 NPE；标记后由 update() 关闭
                mClosePending = true;
            }

            @Override
            public void update() {
                if (mClosePending && mTabMenuWidget != null) {
                    closeTabMenu();
                }
            }
        }

        // SliderListener：行列滑块（音量滑块同款控件），值 0..1 映射 1..6 行 / 1..9 列
        @Override
        public void sliderVal(int theId, double theVal) {
            if (theId == ID_FIELD_ROW) {
                sFieldRow = 1 + (int) Math.round(theVal * 5);
            } else if (theId == ID_FIELD_COL) {
                sFieldCol = 1 + (int) Math.round(theVal * 8);
            } else if (theId == ID_FIELD_SPEED) {
                sSpeedMult = 1 + (int) Math.round(theVal * 9); // 1..10 倍，拖动实时生效
            } else if (theId == ID_ZOMBIE_VACUUM_COL) {
                sVacuumCol = 1 + (int) Math.round(theVal * 8); // 吸怪目标列 1..9
            }
            markDirty();
        }

        // Dialog 的 ButtonListener：1000 = footer"收 起"（折叠回悬浮按钮），2005+ = 各动作与标签页
        @Override
        public void buttonDepress(int theId) {
            // 点任意按钮（含标签按钮本身）时先收起弹出的下拉菜单
            if (mTabMenuWidget != null && theId != ID_TAB) {
                closeTabMenu();
            }
            if (theId == Dialog.ID_FOOTER) {
                sDialogOpen = false;
                sApp.killDialog(mId);
                // 展开自动暂停开启时，收起 = 回到游戏（棋盘恢复；LawnApp.modalClose 因
                // 暂停对话框等残留不会自动恢复，这里显式恢复一次）
                if (sAutoPauseOnOpen && sBoard != null
                        && sApp.mGameScene == GameScenes.SCENE_PLAYING) {
                    sBoard.pause(false);
                }
                return;
            }
            if (theId == ID_TAB) {
                // 展开中再点 = 收起
                if (mTabMenuWidget != null) {
                    closeTabMenu();
                    return;
                }
                openTabMenu();
                return;
            }
            boolean aInLevel = sBoard != null
                    && sApp.mGameScene == GameScenes.SCENE_PLAYING;
            if (theId == ID_KILL_ALL && aInLevel) {
                killAllZombies(sBoard);
                return;
            }
            if (theId == ID_NEXT_WAVE && aInLevel) {
                spawnNextWave(sBoard);
                return;
            }
            if (theId == ID_ADVANCE_FLAG && aInLevel) {
                advanceToNextFlag(sBoard);
                return;
            }
            if (theId == ID_WIN_LEVEL && aInLevel) {
                winLevel(sBoard);
                return;
            }
            if (theId == ID_FIELD_SEED) {
                buildSeedAndZombieLists();
                int aSelected = 0;
                for (int i = 0; i < sSeedIds.length; i++) {
                    if (sSeedIds[i] == sFieldSeed) {
                        aSelected = i;
                        break;
                    }
                }
                openFieldPopup(sSeedNames, aSelected, aIndex -> {
                    sFieldSeed = sSeedIds[aIndex];
                    mFieldSeedButton.setLabel("植物：" + sSeedNames[aIndex]);
                }, mFieldSeedButton);
                return;
            }
            if (theId == ID_FIELD_PLANT) {
                plantSelectedSeed();
                return;
            }
            if (theId == ID_FIELD_ZOMBIE) {
                buildSeedAndZombieLists();
                int aSelected = 0;
                for (int i = 0; i < sZombieIds.length; i++) {
                    if (sZombieIds[i] == sFieldZombie) {
                        aSelected = i;
                        break;
                    }
                }
                openFieldPopup(sZombieNames, aSelected, aIndex -> {
                    sFieldZombie = sZombieIds[aIndex];
                    mFieldZombieButton.setLabel("僵尸：" + sFieldZombie);
                }, mFieldZombieButton);
                return;
            }
            if (theId == ID_FIELD_ZOMBIE_PLACE && sBoard != null) {
                sBoard.addZombie(sFieldZombie, Zombie.ZOMBIE_WAVE_DEBUG);
                show(sBoard, "已放置僵尸 " + sFieldZombie);
                return;
            }
            if (theId == ID_FIELD_ITEM) {
                openFieldPopup(FIELD_ITEM_NAMES, sFieldItem, aIndex -> {
                    sFieldItem = aIndex;
                    mFieldItemButton.setLabel("物品：" + FIELD_ITEM_NAMES[aIndex]);
                }, mFieldItemButton);
                return;
            }
            if (theId == ID_FIELD_ITEM_PLACE) {
                placeSelectedItem();
                return;
            }
            if (theId == ID_FIELD_MUSIC) {
                String[] aTuneNames = {"Grasswalk（白天）", "Moongrains（黑夜）", "Watery Graves（泳池）",
                        "Rigor Mormist（迷雾）", "Graze the Roof（屋顶）", "Choose Your Seeds（选卡）",
                        "Crazy Dave 主旋律", "ZenGarden（休闲花园）", "Cerebrawl（解谜）", "Loonboon（小游戏）",
                        "Conveyer（传送带）", "Brainiac Maniac（僵王）", "Zombies on Your Lawn（制作名单）"};
                openFieldPopup(aTuneNames, sFieldTune - 1, aIndex -> {
                    sFieldTune = aIndex + 1;
                    mFieldMusicButton.setLabel("音乐：" + aTuneNames[aIndex].split("（")[0]);
                }, mFieldMusicButton);
                return;
            }
            if (theId == ID_FIELD_MUSIC_APPLY) {
                // 商店界面不支持切歌：其 update() 每帧重申 Crazy Dave，切完立刻被改回；
                // credits MV 也不支持：Zombies On Your Lawn 是独立 ogg 流且不可打断，
                // 切歌 stopAllMusic 后再播会叠音。提示显示在"修改音乐"钮上，2 秒后恢复
                if (sApp.getDialog(Dialogs.DIALOG_STORE) != null
                        || sApp.mCreditScreen != null) {
                    mMusicLabelOriginal = mFieldMusicApplyButton.mLabel;
                    mFieldMusicApplyButton.setLabel("界面不支持");
                    mMusicLabelRestoreAt = System.currentTimeMillis() + 2000;
                    return;
                }
                // 用引擎的幂等入口 makeSureMusicIsPlaying（内部先 stopAllMusic 再 playMusic，
                // 正确维护 mCurMusicTune/暂停/爆发等状态字段）。传 -1 走每首曲子自己的
                // 默认 order（共用 MOD 文件，传 0 全变 Grasswalk）
                sApp.mMusic.makeSureMusicIsPlaying(sFieldTune);
                show(sBoard, "音乐已切换");
                return;
            }
            if (theId == ID_FIELD_CLEAR_GRAVES && sBoard != null) {
                clearGraveStones();
                return;
            }
            if (theId == ID_SET_SUN && sBoard != null
                    && sApp.getDialog(TRAINER_SUN_DIALOG_ID) == null) {
                sApp.addDialog(TRAINER_SUN_DIALOG_ID, new NumberInputDialog(TRAINER_SUN_DIALOG_ID,
                        "设置阳光", "请输入阳光数量：", 4, TrainerMod::setSun));
                return;
            }
            if (theId == ID_ZOMBIE_OP) {
                openFieldPopup(ZOMBIE_OP_NAMES, sZombieOpIndex, aIndex -> {
                    sZombieOpIndex = aIndex;
                    mZombieOpButton.setLabel("所有僵尸：" + ZOMBIE_OP_NAMES[aIndex]);
                }, mZombieOpButton);
                return;
            }
            if (theId == ID_ZOMBIE_APPLY && sBoard != null) {
                int aCount = applyZombieOperation(sBoard, sZombieOpIndex);
                show(sBoard, "对所有僵尸执行 " + ZOMBIE_OP_NAMES[sZombieOpIndex] + " ×" + aCount);
                return;
            }
            if (theId == ID_SLOT_COUNT && sBoard != null) {
                sApp.addDialog(TRAINER_SLOT_COUNT_DIALOG_ID, new NumberInputDialog(
                        TRAINER_SLOT_COUNT_DIALOG_ID, "卡槽格数", "请输入卡槽格数（1-10）：", 2,
                        aCount -> {
                            int aCount2 = Math.max(1, Math.min(10, aCount));
                            sApp.mPlayerInfo.mPurchases[cn.enaium.fabricpvz.portable.lawn.StoreItem.STORE_ITEM_PACKET_UPGRADE] =
                                    Math.max(0, aCount2 - 6);
                            sBoard.mSeedBank.mNumPackets = aCount2;
                            mSlotCountButton.setLabel("卡槽格数：" + aCount2);
                            show(sBoard, "卡槽格数已设置为 " + aCount2);
                        }));
                return;
            }
            if (theId == ID_SLOT_PICK && sBoard != null) {
                int aCount = sBoard.mSeedBank.mNumPackets;
                String[] aNames = new String[aCount];
                for (int i = 0; i < aCount; i++) {
                    aNames[i] = "第 " + (i + 1) + " 格";
                }
                final int aCountFinal = aCount;
                openFieldPopup(aNames, Math.min(sSlotPickIndex, aCountFinal - 1), aIndex -> {
                    sSlotPickIndex = aIndex;
                    mSlotPickButton.setLabel("第 " + (aIndex + 1) + " 格");
                }, mSlotPickButton);
                return;
            }
            if (theId == ID_SLOT_SEED) {
                buildSeedAndZombieLists();
                int aSelected = 0;
                for (int i = 0; i < sSeedIds.length; i++) {
                    if (sSeedIds[i] == sSlotSeed) {
                        aSelected = i;
                        break;
                    }
                }
                openFieldPopup(sSeedNames, aSelected, aIndex -> {
                    sSlotSeed = sSeedIds[aIndex];
                    mSlotSeedButton.setLabel("植物：" + sSeedNames[aIndex]);
                }, mSlotSeedButton);
                return;
            }
            if (theId == ID_SLOT_APPLY && sBoard != null) {
                SeedPacket aPacket = sBoard.mSeedBank.mSeedPackets[sSlotPickIndex];
                aPacket.mPacketType = sSlotSeed;
                aPacket.mImitaterType = cn.enaium.fabricpvz.portable.lawn.SeedType.SEED_NONE;
                aPacket.mRefreshing = false;
                aPacket.mRefreshCounter = 0;
                show(sBoard, "第 " + (sSlotPickIndex + 1) + " 格已设置为 "
                        + seedName(sSlotSeed));
                return;
            }
            if (theId == ID_CARD_PICK) {
                buildSeedAndZombieLists();
                int aSelected = 0;
                for (int i = 0; i < sSeedIds.length; i++) {
                    if (sSeedIds[i] == sCardSeed) {
                        aSelected = i;
                        break;
                    }
                }
                openFieldPopup(sSeedNames, aSelected, aIndex -> {
                    sCardSeed = sSeedIds[aIndex];
                    mCardPickButton.setLabel("卡片：" + sSeedNames[aIndex]);
                }, mCardPickButton);
                return;
            }
            if (theId == ID_CARD_ATTACK && sApp.mPlayerInfo != null) {
                sApp.addDialog(TRAINER_ATTACK_DIALOG_ID, new NumberInputDialog(
                        TRAINER_ATTACK_DIALOG_ID, "设置攻速", "请输入攻速（发射间隔 ticks）：", 5,
                        aRate -> {
                            cn.enaium.fabricpvz.portable.lawn.PlantDefinition aDef =
                                    cn.enaium.fabricpvz.portable.lawn.Plant.gPlantDefs[sCardSeed];
                            if (aDef != null) {
                                aDef.mLaunchRate = aRate;
                                if (sBoard != null) {
                                    for (Plant aPlant : sBoard.mPlants) {
                                        if (!aPlant.mDead && aPlant.mSeedType == sCardSeed) {
                                            aPlant.mLaunchRate = aRate;
                                        }
                                    }
                                }
                                show(sBoard, "攻速已设置为 " + aRate);
                            }
                        }));
                return;
            }
            if (theId == ID_CARD_PRICE && sApp.mPlayerInfo != null) {
                sApp.addDialog(TRAINER_PRICE_DIALOG_ID, new NumberInputDialog(
                        TRAINER_PRICE_DIALOG_ID, "设置价格", "请输入价格：", 5, aPrice -> {
                    cn.enaium.fabricpvz.portable.lawn.PlantDefinition aDef =
                            cn.enaium.fabricpvz.portable.lawn.Plant.gPlantDefs[sCardSeed];
                    if (aDef != null) {
                        aDef.mSeedCost = aPrice;
                        show(sBoard, "价格已设置为 " + aPrice);
                    }
                }));
                return;
            }
            if (theId == ID_CARD_COOLDOWN && sApp.mPlayerInfo != null) {
                sApp.addDialog(TRAINER_COOLDOWN_DIALOG_ID, new NumberInputDialog(
                        TRAINER_COOLDOWN_DIALOG_ID, "设置冷却", "请输入冷却（ticks）：", 6, aTime -> {
                    cn.enaium.fabricpvz.portable.lawn.PlantDefinition aDef =
                            cn.enaium.fabricpvz.portable.lawn.Plant.gPlantDefs[sCardSeed];
                    if (aDef != null) {
                        aDef.mRefreshTime = aTime;
                        show(sBoard, "冷却已设置为 " + aTime);
                    }
                }));
                return;
            }
            if (theId == ID_UNLOCK_GOLD_SUNFLOWER && sApp.mPlayerInfo != null) {
                unlockGoldSunflower();
                return;
            }
            if (theId == ID_TMP_UNLOCK_MODES) {
                tmpUnlockAllModes();
                return;
            }
            if (theId == ID_SET_COINS && sApp.mPlayerInfo != null
                    && sApp.getDialog(TRAINER_COIN_DIALOG_ID) == null) {
                sApp.addDialog(TRAINER_COIN_DIALOG_ID, new NumberInputDialog(TRAINER_COIN_DIALOG_ID,
                        "设置金币", "请输入金币数量：", 9, TrainerMod::setCoins));
                return;
            }
            if (theId == ID_TREE_HEIGHT && sApp.mPlayerInfo != null
                    && sApp.getDialog(TRAINER_TREE_HEIGHT_DIALOG_ID) == null) {
                sApp.addDialog(TRAINER_TREE_HEIGHT_DIALOG_ID, new NumberInputDialog(
                        TRAINER_TREE_HEIGHT_DIALOG_ID, "设置智慧树高度", "请输入智慧树高度：", 3,
                        TrainerMod::setTreeHeight));
                return;
            }
            if (theId == ID_ADD_SPROUT) {
                addPottedPlant(false, false);
                return;
            }
            if (theId == ID_ADD_MARIGOLD) {
                addPottedPlant(true, false);
                return;
            }
            if (theId == ID_ADD_BIG_PLANT) {
                addPottedPlant(false, true);
                return;
            }
            int anEffectIndex = theId - ID_EFFECT_SUKHBIR;
            if (anEffectIndex >= 0 && anEffectIndex < EFFECT_NAMES.length) {
                toggleEffect(EFFECT_NAMES[anEffectIndex]);
                return;
            }
            if (theId == ID_KILL_PLANTS && aInLevel) {
                killAllPlants(sBoard);
                return;
            }
            if (theId == ID_CLEAR_MOWERS && aInLevel) {
                clearLawnMowers(sBoard);
                return;
            }
            if (theId == ID_RESTORE_MOWERS && aInLevel) {
                restoreLawnMowers(sBoard);
                return;
            }
            if (theId == ID_START_MOWERS && aInLevel) {
                startLawnMowers(sBoard);
                return;
            }
            if (theId == ID_SKIP_LEVEL && aInLevel) {
                openSkipLevelDialog();
                return;
            }
            super.buttonDepress(theId);
        }

        // CheckboxListener：开关即写状态（免费种植/自动收集与隐码同源标志位）。
        // 菜单里也可能打开对话框（sBoard 可能为 null），棋盘相关动作只在关卡内做——
        // 标志位本身照常生效，进关卡后由 TICK 的写回逻辑接管
        @Override
        public void checkboxChecked(int theId, boolean theChecked) {
            super.checkboxChecked(); // 保留基类的按键音
            if (mTabMenuWidget != null) {
                closeTabMenu();
            }
            boolean aInLevel = sBoard != null && sApp != null
                    && sApp.mGameScene == GameScenes.SCENE_PLAYING;
            switch (theId) {
                case ID_CHECK_SUN -> {
                    infiniteSun = theChecked;
                    show(sBoard, "无限阳光：" + onOff(theChecked));
                }
                case ID_CHECK_PLANT -> {
                    freePlanting = theChecked;
                    if (aInLevel) {
                        sBoard.mApp.mEasyPlantingCheat = theChecked;
                    }
                    show(sBoard, "免费种植：" + onOff(theChecked));
                }
                case ID_CHECK_COLLECT -> {
                    autoCollect = theChecked;
                    if (aInLevel) {
                        sBoard.mApp.mAutoCollectCheat = theChecked;
                        if (theChecked) {
                            // 开启瞬间收走场上已有的拾取物（新刷出的由游戏自己的标志位处理）
                            for (Coin aCoin : sBoard.mCoins) {
                                if (!aCoin.mDead && !aCoin.mIsBeingCollected) {
                                    aCoin.tryAutoCollectAfterLevelAward();
                                }
                            }
                        }
                    }
                    show(sBoard, "自动收集：" + onOff(theChecked));
                }
                case ID_CHECK_PAUSE_SPAWN -> {
                    pauseSpawning = theChecked;
                    show(sBoard, "暂停出怪：" + onOff(theChecked));
                }
                case ID_CHECK_NO_SUN_DROPS -> {
                    noSunDrops = theChecked;
                    show(sBoard, "不掉阳光：" + onOff(theChecked));
                }
                case ID_CHECK_NO_COIN_DROPS -> {
                    noCoinDrops = theChecked;
                    show(sBoard, "不掉金币：" + onOff(theChecked));
                }
                case ID_CHECK_ANY_PLANTING -> {
                    anyPlanting = theChecked;
                    show(sBoard, "任意种植：" + onOff(theChecked));
                }
                case ID_CHECK_INFINITE_COINS -> {
                    infiniteCoins = theChecked;
                    show(sBoard, "无限金币：" + onOff(theChecked));
                }
                case ID_CHECK_INFINITE_FERTILIZER -> {
                    infiniteFertilizer = theChecked;
                    show(sBoard, "无限花肥：" + onOff(theChecked));
                }
                case ID_CHECK_INFINITE_BUG_SPRAY -> {
                    infiniteBugSpray = theChecked;
                    show(sBoard, "无限杀虫剂：" + onOff(theChecked));
                }
                case ID_CHECK_INFINITE_CHOCOLATE -> {
                    infiniteChocolate = theChecked;
                    show(sBoard, "无限巧克力：" + onOff(theChecked));
                }
                case ID_CHECK_COB_NO_CD -> {
                    cobNoCooldown = theChecked;
                    show(sBoard, "玉米炮无冷却：" + onOff(theChecked));
                }
                case ID_CHECK_MAGNET_NO_CD -> {
                    magnetNoCooldown = theChecked;
                    show(sBoard, "磁力菇无冷却：" + onOff(theChecked));
                }
                case ID_CHECK_POTATO_NO_CD -> {
                    potatoNoCooldown = theChecked;
                    show(sBoard, "土豆雷无冷却：" + onOff(theChecked));
                }
                case ID_CHECK_CHOMPER_NO_CD -> {
                    chomperNoCooldown = theChecked;
                    show(sBoard, "大嘴花无冷却：" + onOff(theChecked));
                }
                case ID_CHECK_PLANT_INVINCIBLE -> {
                    plantInvincible = theChecked;
                    if (plantInvincible) {
                        plantFragile = false;
                        mPlantFragileCheck.mChecked = false;
                    }
                    show(sBoard, "植物无敌：" + onOff(theChecked));
                }
                case ID_CHECK_PLANT_FRAGILE -> {
                    plantFragile = theChecked;
                    if (plantFragile) {
                        plantInvincible = false;
                        mPlantInvincibleCheck.mChecked = false;
                    }
                    show(sBoard, "植物脆弱：" + onOff(theChecked));
                }
                case ID_CHECK_NO_CRATER -> {
                    noCrater = theChecked;
                    show(sBoard, "不留弹坑：" + onOff(theChecked));
                }
                case ID_CHECK_MUSHROOM_AWAKE -> {
                    mushroomAwake = theChecked;
                    show(sBoard, "蘑菇免唤醒：" + onOff(theChecked));
                }
                case ID_CHECK_REMOVE_FOG -> {
                    removeFog = theChecked;
                    show(sBoard, "去除浓雾：" + onOff(theChecked));
                }
                case ID_CHECK_VASE_XRAY -> {
                    vaseXray = theChecked;
                    show(sBoard, "花瓶透视：" + onOff(theChecked));
                }
                case ID_CHECK_MODE_SEED_RAIN -> {
                    seedRain = theChecked;
                    if (theChecked && stormyNight) { // 二者共用 Challenge.mChallengeStateCounter
                        stormyNight = false;
                        mStormyNightCheck.mChecked = false;
                        sStormBoard = null;
                    }
                    if (!theChecked && !stormyNight && sBoard != null) {
                        sApp.mSoundSystem.stopFoley(cn.enaium.fabricpvz.portable.pvzp.fx.FoleyType.FOLEY_RAIN);
                    }
                    show(sBoard, challengeName(cn.enaium.fabricpvz.portable.lawn.GameMode.GAMEMODE_CHALLENGE_RAINING_SEEDS)
                            + "：" + onOff(theChecked));
                }
                case ID_CHECK_MODE_MINI_ZOMBIE -> {
                    miniZombie = theChecked;
                    show(sBoard, challengeName(cn.enaium.fabricpvz.portable.lawn.GameMode.GAMEMODE_CHALLENGE_LITTLE_TROUBLE)
                            + "：" + onOff(theChecked));
                }
                case ID_CHECK_MODE_COLUMN -> {
                    columnPlant = theChecked;
                    show(sBoard, challengeName(cn.enaium.fabricpvz.portable.lawn.GameMode.GAMEMODE_CHALLENGE_COLUMN)
                            + "：" + onOff(theChecked));
                }
                case ID_CHECK_MODE_ZOMBIE_SPEED -> {
                    zombieSpeed = theChecked;
                    show(sBoard, challengeName(cn.enaium.fabricpvz.portable.lawn.GameMode.GAMEMODE_CHALLENGE_SPEED)
                            + "：" + onOff(theChecked));
                }
                case ID_CHECK_MODE_WHACK -> {
                    whackHammer = theChecked;
                    show(sBoard, challengeName(cn.enaium.fabricpvz.portable.lawn.GameMode.GAMEMODE_CHALLENGE_WHACK_A_ZOMBIE)
                            + "：" + onOff(theChecked));
                }
                case ID_CHECK_MODE_NEWTON -> {
                    newtonGravity = theChecked;
                    show(sBoard, challengeName(cn.enaium.fabricpvz.portable.lawn.GameMode.GAMEMODE_CHALLENGE_HIGH_GRAVITY)
                            + "：" + onOff(theChecked));
                }
                case ID_CHECK_MODE_GRAVE_DANGER -> {
                    graveDanger = theChecked;
                    show(sBoard, challengeName(cn.enaium.fabricpvz.portable.lawn.GameMode.GAMEMODE_CHALLENGE_GRAVE_DANGER)
                            + "：" + onOff(theChecked));
                }
                case ID_CHECK_MODE_STORMY_NIGHT -> {
                    stormyNight = theChecked;
                    if (theChecked && seedRain) { // 二者共用 Challenge.mChallengeStateCounter
                        seedRain = false;
                        mSeedRainCheck.mChecked = false;
                        sSeedRainBoard = null;
                    }
                    if (!theChecked && !seedRain && sBoard != null) {
                        sApp.mSoundSystem.stopFoley(cn.enaium.fabricpvz.portable.pvzp.fx.FoleyType.FOLEY_RAIN);
                    }
                    show(sBoard, challengeName(cn.enaium.fabricpvz.portable.lawn.GameMode.GAMEMODE_CHALLENGE_STORMY_NIGHT)
                            + "：" + onOff(theChecked));
                }
                case ID_CHECK_MODE_BUNGEE_BLITZ -> {
                    bungeeBlitz = theChecked;
                    show(sBoard, challengeName(cn.enaium.fabricpvz.portable.lawn.GameMode.GAMEMODE_CHALLENGE_BUNGEE_BLITZ)
                            + "：" + onOff(theChecked));
                }
                case ID_CHECK_ZOMBIE_EXPLODE -> {
                    zombieExplode = theChecked;
                    if (zombieExplode && zombieNeverExplode) { // 爆与不爆互斥
                        zombieNeverExplode = false;
                        mZombieNeverExplodeCheck.mChecked = false;
                    }
                    show(sBoard, "立即爆炸：" + onOff(theChecked));
                }
                case ID_CHECK_ZOMBIE_NEVER_EXPLODE -> {
                    zombieNeverExplode = theChecked;
                    if (zombieNeverExplode && zombieExplode) {
                        zombieExplode = false;
                        mZombieExplodeCheck.mChecked = false;
                    }
                    show(sBoard, "从不爆炸：" + onOff(theChecked));
                }
                case ID_CHECK_ZOMBIE_NO_CHILL -> {
                    zombieNoChill = theChecked;
                    show(sBoard, "取消寒冰减速：" + onOff(theChecked));
                }
                case ID_CHECK_ZOMBIE_NO_BUTTER -> {
                    zombieNoButter = theChecked;
                    show(sBoard, "取消黄油固定：" + onOff(theChecked));
                }
                case ID_CHECK_ZOMBIE_HIDE -> {
                    zombieHide = theChecked;
                    show(sBoard, "僵尸隐形：" + onOff(theChecked));
                }
                case ID_CHECK_ZOMBIE_SHOW -> {
                    zombieShow = theChecked;
                    show(sBoard, "僵尸现形：" + onOff(theChecked));
                }
                case ID_CHECK_ZOMBIE_NO_DANCERS -> {
                    zombieNoDancers = theChecked;
                    show(sBoard, "取消泳池三人组：" + onOff(theChecked));
                }
                case ID_CHECK_ZOMBIE_NO_ICE_TRAIL -> {
                    zombieNoIceTrail = theChecked;
                    show(sBoard, "不留冰道：" + onOff(theChecked));
                }
                case ID_CHECK_ZOMBIE_INVINCIBLE -> {
                    zombieInvincible = theChecked;
                    if (zombieInvincible && zombieWeak) { // 无敌与虚弱互斥
                        zombieWeak = false;
                        mZombieWeakCheck.mChecked = false;
                    }
                    show(sBoard, "僵尸无敌：" + onOff(theChecked));
                }
                case ID_CHECK_ZOMBIE_WEAK -> {
                    zombieWeak = theChecked;
                    if (zombieWeak && zombieInvincible) {
                        zombieInvincible = false;
                        mZombieInvincibleCheck.mChecked = false;
                    }
                    show(sBoard, "僵尸脆弱：" + onOff(theChecked));
                }
                case ID_CHECK_ZOMBIE_NO_IMP_THROW -> {
                    zombieNoImpThrow = theChecked;
                    show(sBoard, "不扔小鬼：" + onOff(theChecked));
                }
                case ID_CHECK_ZOMBIE_GARG_EDIBLE -> {
                    zombieGargEdible = theChecked;
                    show(sBoard, "巨人可被吃：" + onOff(theChecked));
                }
                case ID_CHECK_ZOMBIE_FROZEN -> {
                    zombieFrozen = theChecked;
                    show(sBoard, "僵尸不动：" + onOff(theChecked));
                }
                case ID_CHECK_ZOMBIE_BOSS_FROZEN -> {
                    zombieBossFrozen = theChecked;
                    show(sBoard, "僵王不动：" + onOff(theChecked));
                }
                case ID_CHECK_ZOMBIE_BALLOON_BOOM -> {
                    zombieBalloonBoom = theChecked;
                    show(sBoard, "气球自爆：" + onOff(theChecked));
                }
                case ID_CHECK_ZOMBIE_VACUUM -> {
                    zombieVacuum = theChecked;
                    show(sBoard, "吸怪：" + onOff(theChecked) + "（目标列 " + sVacuumCol + "）");
                }
                case ID_CHECK_HIDE_SEEDBANK -> {
                    if (sBoard != null) {
                        // 隐藏 = 卡槽整体不画（GameObject.mVisible 由 beginDraw 检查）
                        sBoard.mSeedBank.mVisible = !theChecked;
                        show(sBoard, "隐藏卡槽：" + onOff(theChecked));
                    }
                }
                case ID_CHECK_SHOW_SHOVEL -> {
                    if (sBoard != null) {
                        sBoard.mShowShovel = theChecked;
                        show(sBoard, "显示铲子：" + onOff(theChecked));
                    }
                }
                case ID_CHECK_HIDE_MENU_BUTTON -> {
                    if (sBoard != null) {
                        sBoard.mMenuButton.mBtnNoDraw = theChecked;
                        show(sBoard, "隐藏菜单按钮：" + onOff(theChecked));
                    }
                }
                case ID_CHECK_NO_COOLDOWN -> {
                    noCooldown = theChecked;
                    show(sBoard, "卡片无冷却：" + onOff(theChecked));
                }
                default -> {
                }
            }
        }
    }

    // -------------------------------------------------------------------------------------

    /**
     * 配置对话框：修改器的设置项（当前：展开自动暂停），改动即写 config/pvz-trainer-mod.json。
     * 主菜单 X+C 组合键与 ModMenu 的"配置"入口共用；modal=true（主菜单上无棋盘可暂停）。
     */
    private static final class TrainerConfigDialog extends LawnDialog implements CheckboxListener {

        private static final int CHECKBOX_WIDTH = 46;
        private static final int CHECKBOX_HEIGHT = 45;
        private static final int ROW_HEIGHT = 50;

        private final Checkbox mAutoPauseCheck;
        private final Checkbox mAlwaysOnTopCheck;

        TrainerConfigDialog() {
            super(sApp, TRAINER_CONFIG_DIALOG_ID, true, "修改器配置", "", "关 闭", Dialog.BUTTONS_FOOTER);
            mAutoPauseCheck = LawnCommon.makeNewCheckbox(ID_CHECK_AUTO_PAUSE, this, sAutoPauseOnOpen);
            mAlwaysOnTopCheck = LawnCommon.makeNewCheckbox(ID_CHECK_ALWAYS_ON_TOP, this, sAlwaysOnTop);
            calcSize(80, 2 * ROW_HEIGHT + 56);

            // addDialog 只在 mWidth==0 时自动摆放，calcSize 后已是非零——自己居中
            int aX = Math.max(8, (sApp.mWidth - mWidth) / 2);
            int aY = Math.max(16, (sApp.mHeight - mHeight) / 2 - 12);
            resize(aX, aY, mWidth, mHeight);
        }

        @Override
        public void addedToManager(WidgetManager theWidgetManager) {
            super.addedToManager(theWidgetManager);
            addWidget(mAutoPauseCheck);
            addWidget(mAlwaysOnTopCheck);
        }

        @Override
        public void removedFromManager(WidgetManager theWidgetManager) {
            removeWidget(mAutoPauseCheck);
            removeWidget(mAlwaysOnTopCheck);
            super.removedFromManager(theWidgetManager);
        }

        @Override
        public void resize(int theX, int theY, int theWidth, int theHeight) {
            super.resize(theX, theY, theWidth, theHeight); // 基类排标题与 footer 按钮
            // 复选框（46×45，默认 mClip=true 的控件必须显式给足尺寸）与文字整组居中，
            // 两行各占一个复选框行
            Font aFont = cn.enaium.fabricpvz.portable.lawn.Resources
                    .getFontById(cn.enaium.fabricpvz.portable.lawn.Resources.FONT_BRIANNETOD12_ID);
            String[] aNames = {"展开修改器时自动暂停", "悬浮按钮始终置顶"};
            Checkbox[] aChecks = {mAutoPauseCheck, mAlwaysOnTopCheck};
            for (int i = 0; i < aNames.length; i++) {
                int aGroupWidth = CHECKBOX_WIDTH + 10 + aFont.stringWidth(aNames[i]);
                aChecks[i].resize(getLeft() + Math.max(0, (getWidth() - aGroupWidth) / 2),
                        getTop() + i * ROW_HEIGHT, CHECKBOX_WIDTH, CHECKBOX_HEIGHT);
            }
        }

        @Override
        public void draw(Graphics g) {
            super.draw(g);
            Font aFont = cn.enaium.fabricpvz.portable.lawn.Resources
                    .getFontById(cn.enaium.fabricpvz.portable.lawn.Resources.FONT_BRIANNETOD12_ID);
            g.setFont(aFont);
            Checkbox[] aChecks = {mAutoPauseCheck, mAlwaysOnTopCheck};
            String[] aNames = {"展开修改器时自动暂停", "悬浮按钮始终置顶"};
            for (int i = 0; i < aNames.length; i++) {
                g.setColor(new cn.enaium.fabricpvz.portable.sexy.Color(232, 218, 160));
                g.drawString(aNames[i], aChecks[i].mX + CHECKBOX_WIDTH + 10,
                        aChecks[i].mY + (CHECKBOX_HEIGHT - aFont.mHeight) / 2 + aFont.mAscent);
            }
        }

        // Dialog 的 ButtonListener：1000 = footer"关 闭"
        @Override
        public void buttonDepress(int theId) {
            if (theId == Dialog.ID_FOOTER) {
                sApp.killDialog(mId);
                return;
            }
            super.buttonDepress(theId);
        }

        // CheckboxListener：改动即写配置文件
        @Override
        public void checkboxChecked(int theId, boolean theChecked) {
            super.checkboxChecked(); // 保留基类的按键音
            if (theId == ID_CHECK_AUTO_PAUSE) {
                sAutoPauseOnOpen = theChecked;
                saveConfig();
                LOGGER.info("展开修改器时自动暂停：{}", onOff(theChecked));
            } else if (theId == ID_CHECK_ALWAYS_ON_TOP) {
                sAlwaysOnTop = theChecked;
                saveConfig();
                LOGGER.info("悬浮按钮始终置顶：{}", onOff(theChecked));
            }
        }
    }

    // -------------------------------------------------------------------------------------

    /**
     * 数字输入对话框（原生输入框 LawnEditWidget，NewUserDialog 同款骨架）：回车或"确 定"
     * 把输入的数字交给 {@code theApplier}（校验/写入由应用方负责），只接受纯数字。
     */
    private static final class NumberInputDialog extends LawnDialog implements EditListener {

        private final LawnEditWidget mNumberEdit;
        private final java.util.function.IntConsumer mApplier;

        NumberInputDialog(int theDialogId, String theTitle, String thePrompt, int theMaxDigits,
                java.util.function.IntConsumer theApplier) {
            super(sApp, theDialogId, true, theTitle, thePrompt, "确 定", Dialog.BUTTONS_FOOTER);
            mApplier = theApplier;
            mVerticalCenterText = false;
            mNumberEdit = LawnCommon.createEditWidget(0, this, this);
            mNumberEdit.mMaxChars = theMaxDigits;
            mNumberEdit.addWidthCheckFont(
                    cn.enaium.fabricpvz.portable.lawn.Resources
                            .getFontById(cn.enaium.fabricpvz.portable.lawn.Resources.FONT_BRIANNETOD16_ID),
                    220);
            calcSize(110, 40);

            int aX = Math.max(8, (sApp.mWidth - mWidth) / 2);
            int aY = Math.max(16, (sApp.mHeight - mHeight) / 2 - 12);
            resize(aX, aY, mWidth, mHeight);
        }

        @Override
        public void addedToManager(WidgetManager theWidgetManager) {
            super.addedToManager(theWidgetManager);
            addWidget(mNumberEdit);
            theWidgetManager.setFocus(mNumberEdit);
        }

        @Override
        public void removedFromManager(WidgetManager theWidgetManager) {
            super.removedFromManager(theWidgetManager);
            removeWidget(mNumberEdit);
        }

        @Override
        public void resize(int theX, int theY, int theWidth, int theHeight) {
            super.resize(theX, theY, theWidth, theHeight);
            mNumberEdit.resize(mContentInsets.mLeft + 12, mHeight - 155,
                    mWidth - mContentInsets.mLeft - mContentInsets.mRight - 24, 28);
        }

        @Override
        public void draw(Graphics g) {
            super.draw(g);
            LawnCommon.drawEditBox(g, mNumberEdit);
        }

        // EditListener：回车提交
        @Override
        public void editWidgetText(int theId, String theString) {
            apply();
        }

        private void apply() {
            String aValue = mNumberEdit.mString.trim();
            // 输入框不做字符过滤（引擎未接线字符过滤钩子），提交时校验：只接受纯数字
            if (!aValue.matches("\\d+")) {
                return;
            }
            try {
                mApplier.accept(Integer.parseInt(aValue));
            } catch (NumberFormatException ignored) {
                return;
            }
            sApp.killDialog(mId);
        }

        // Dialog 的 ButtonListener：1000 = footer"确 定"
        @Override
        public void buttonDepress(int theId) {
            if (theId == Dialog.ID_FOOTER) {
                apply();
                return;
            }
            super.buttonDepress(theId);
        }
    }
}
