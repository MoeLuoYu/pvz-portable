package cn.enaium.fabricpvz.trainermod;

import cn.enaium.fabricpvz.api.lawn.LawnEvents;
import cn.enaium.fabricpvz.portable.lawn.AdviceType;
import cn.enaium.fabricpvz.portable.lawn.Board;
import cn.enaium.fabricpvz.portable.lawn.Coin;
import cn.enaium.fabricpvz.portable.lawn.DamageFlags;
import cn.enaium.fabricpvz.portable.lawn.GameScenes;
import cn.enaium.fabricpvz.portable.lawn.MessageStyle;
import cn.enaium.fabricpvz.portable.lawn.Resources;
import cn.enaium.fabricpvz.portable.lawn.SeedPacket;
import cn.enaium.fabricpvz.portable.lawn.Zombie;
import cn.enaium.fabricpvz.portable.sexy.Color;
import cn.enaium.fabricpvz.portable.sexy.Widget;
import cn.enaium.fabricpvz.portable.sexy.WidgetManager;
import cn.enaium.fabricpvz.portable.sexy.graphics.Font;
import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 修改器模组：左下角悬浮菜单（点击条目触发），零 Mixin。
 *
 * <p>实现路径全部走公开面：
 * <ul>
 *   <li>悬浮菜单——一个自定义 {@link Widget} 加进 {@code WidgetManager}（公开 API）。
 *     命中测试按"后加入者优先"（{@code WidgetContainer.getWidgetAtHelper} 从列表尾向前找），
 *     面板保持最顶层时其矩形内的点击只到达面板、不穿透到棋盘；矩形外点击照常给棋盘。</li>
 *   <li>置顶维护——每级开始时棋盘会被重新加进管理器（排到面板之上），TICK 里检测到面板
 *     不在末位就摘下来重新加入；{@code mVisible=false} 的 widget 不参与命中测试，
 *     所以菜单只在 SCENE_PLAYING 显示、主菜单等界面自动放行。</li>
 *   <li>持续生效项（无限阳光/秒冷却）在 TICK 里逐 tick 强制写回；开关项复用游戏自带的
 *     作弊标志位（免费种植/自动收集），与隐码完全同源。</li>
 *   <li>字幕反馈走 {@code Board.displayAdvice}——不带方括号的字符串原样显示，
 *     不需要往语言文件里加键。</li>
 * </ul>
 */
public final class TrainerMod implements ModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("pvz-trainer-mod");

    /** 无限阳光强制写入的数值。 */
    private static final int INFINITE_SUN_VALUE = 9990;
    /** 秒杀伤害与标志：与 Board.killAllZombiesInRadius 的非燃烧路径同参（Board.cpp:9312）。 */
    private static final int KILL_DAMAGE = 1800;
    private static final int KILL_FLAGS = DamageFlags.DAMAGE_HITS_SHIELD_AND_BODY
            | (1 << DamageFlags.DAMAGE_DOESNT_LEAVE_BODY);

    private static boolean infiniteSun;
    private static boolean freePlanting;
    private static boolean autoCollect;
    private static boolean instantRecharge;

    /** 最近一帧的棋盘（TICK 只在关卡里触发，菜单只在关卡里可见）。 */
    private static Board sBoard;
    /** 悬浮菜单；首次 TICK 时挂进 WidgetManager。 */
    private static TrainerMenu sMenu;

    @Override
    public void onInitialize() {
        LOGGER.info("pvz-trainer-mod 已加载：关卡内左下角悬浮菜单（点击条目触发）");
        LawnEvents.TICK.register(TrainerMod::onTick);
    }

    private static void onTick(Board theBoard, int theTick) {
        sBoard = theBoard;
        WidgetManager aManager = theBoard.mApp.mWidgetManager;
        if (sMenu == null) {
            sMenu = new TrainerMenu();
            aManager.addWidget(sMenu);
        }

        // 只在关卡进行中显示；mVisible=false 的 widget 不参与命中测试，主菜单等界面不受影响
        sMenu.mVisible = theBoard.mApp.mGameScene == GameScenes.SCENE_PLAYING;

        // 新关卡会把棋盘重新加入管理器（排到面板之上）：面板不在末位就摘下重新加入，恢复置顶
        if (sMenu.mVisible && aManager.mWidgets.get(aManager.mWidgets.size() - 1) != sMenu) {
            aManager.removeWidget(sMenu);
            aManager.addWidget(sMenu);
        }

        // 持续生效项：逐 tick 强制写回
        if (infiniteSun && sMenu.mVisible) {
            theBoard.mSunMoney = INFINITE_SUN_VALUE;
        }
        if (instantRecharge && sMenu.mVisible) {
            for (SeedPacket aPacket : theBoard.mSeedBank.mSeedPackets) {
                if (aPacket != null) {
                    aPacket.mRefreshCounter = 0;
                    aPacket.mRefreshing = false;
                }
            }
        }
    }

    private static void show(Board theBoard, String theMessage) {
        LOGGER.info(theMessage);
        // 不带方括号的字符串不经翻译，原样显示——无需语言键
        theBoard.displayAdvice(theMessage, MessageStyle.MESSAGE_STYLE_BIG_MIDDLE_FAST,
                AdviceType.ADVICE_NONE);
    }

    private static String onOff(boolean theOn) {
        return theOn ? "开" : "关";
    }

    /**
     * 左下角悬浮菜单：表头可收起，条目点击触发。绘制与命中全部自绘，
     * 不依赖游戏的 ButtonWidget（避免动用游戏的按钮资源与状态机）。
     */
    private static final class TrainerMenu extends Widget {

        private static final int WIDTH = 176;
        private static final int ROW_HEIGHT = 20;
        private static final int HEADER_HEIGHT = 22;
        private static final int ITEM_COUNT = 5;

        private boolean mCollapsed;
        private int mHoverRow = -1;

        TrainerMenu() {
            resize(8, 600 - HEADER_HEIGHT - ITEM_COUNT * ROW_HEIGHT - 8, WIDTH,
                    HEADER_HEIGHT + ITEM_COUNT * ROW_HEIGHT);
        }

        @Override
        public void draw(Graphics g) {
            Font aFont = Resources.getFontById(Resources.FONT_BRIANNETOD12_ID);
            g.setFont(aFont);

            int aHeight = mCollapsed ? HEADER_HEIGHT : HEADER_HEIGHT + ITEM_COUNT * ROW_HEIGHT;
            g.setColor(new Color(0, 0, 0, 170));
            g.fillRect(0, 0, WIDTH, aHeight);
            g.setColor(new Color(255, 255, 255, 90));
            g.drawRect(0, 0, WIDTH, aHeight);

            // 表头
            g.setColor(new Color(255, 255, 255, 60));
            if (mHoverRow == 0) {
                g.fillRect(1, 1, WIDTH - 2, HEADER_HEIGHT - 2);
            }
            g.setColor(new Color(255, 220, 80));
            g.drawString(mCollapsed ? "修改器 ▸" : "修改器 ▾", 8, 4 + aFont.mAscent);

            if (mCollapsed) {
                return;
            }

            // 功能条目
            String[] aNames = {"无限阳光", "免费种植", "自动收集", "秒冷却", "秒杀全场"};
            boolean[] aStates = {infiniteSun, freePlanting, autoCollect, instantRecharge, false};
            for (int i = 0; i < ITEM_COUNT; i++) {
                int aRow = i + 1;
                int aY = HEADER_HEIGHT + i * ROW_HEIGHT;
                if (mHoverRow == aRow) {
                    g.setColor(new Color(255, 255, 255, 40));
                    g.fillRect(1, aY + 1, WIDTH - 2, ROW_HEIGHT - 2);
                }
                g.setColor(new Color(255, 255, 255));
                g.drawString(aNames[i], 8, aY + (ROW_HEIGHT - aFont.mHeight) / 2 + aFont.mAscent);
                if (i < 4) {
                    g.setColor(aStates[i] ? new Color(120, 255, 120) : new Color(150, 150, 150));
                    g.drawString(aStates[i] ? "开" : "关", WIDTH - 30, aY + (ROW_HEIGHT - aFont.mHeight) / 2 + aFont.mAscent);
                }
            }
        }

        @Override
        public void mouseMove(int x, int y) {
            mHoverRow = rowAt(x, y);
        }

        @Override
        public void mouseDown(int x, int y, int theBtnNum, int theClickCount) {
            int aRow = rowAt(x, y);
            if (aRow == 0) {
                mCollapsed = !mCollapsed; // 收起后高度变化，重新适配矩形
                resize(mX, mY, WIDTH, mCollapsed ? HEADER_HEIGHT : HEADER_HEIGHT + ITEM_COUNT * ROW_HEIGHT);
                return;
            }
            if (sBoard == null) {
                return;
            }
            switch (aRow) {
                case 1 -> {
                    infiniteSun = !infiniteSun;
                    show(sBoard, "无限阳光：" + onOff(infiniteSun));
                }
                case 2 -> {
                    freePlanting = !freePlanting;
                    sBoard.mApp.mEasyPlantingCheat = freePlanting; // 与 cheatplant 隐码同源标志位
                    show(sBoard, "免费种植：" + onOff(freePlanting));
                }
                case 3 -> {
                    autoCollect = !autoCollect;
                    sBoard.mApp.mAutoCollectCheat = autoCollect; // 与 lazyclick 隐码同源标志位
                    if (autoCollect) {
                        // 开启瞬间收走场上已有的拾取物（新刷出的由游戏自己的标志位处理）
                        for (Coin aCoin : sBoard.mCoins) {
                            if (!aCoin.mDead && !aCoin.mIsBeingCollected) {
                                aCoin.tryAutoCollectAfterLevelAward();
                            }
                        }
                    }
                    show(sBoard, "自动收集：" + onOff(autoCollect));
                }
                case 4 -> {
                    instantRecharge = !instantRecharge;
                    show(sBoard, "秒冷却：" + onOff(instantRecharge));
                }
                case 5 -> {
                    int aKilled = 0;
                    for (Zombie aZombie : sBoard.mZombies) {
                        if (!aZombie.mDead) {
                            aZombie.takeDamage(KILL_DAMAGE, KILL_FLAGS); // 正常致死路径，播放死亡动画
                            aKilled++;
                        }
                    }
                    show(sBoard, "秒杀全场僵尸 ×" + aKilled);
                }
                default -> {
                }
            }
        }

        /** 表头为 0；收起时其余行一律 -1。 */
        private int rowAt(int x, int y) {
            if (x < 0 || x >= WIDTH || y < 0) {
                return -1;
            }
            if (y < HEADER_HEIGHT) {
                return 0;
            }
            if (mCollapsed) {
                return -1;
            }
            int aRow = (y - HEADER_HEIGHT) / ROW_HEIGHT + 1;
            return aRow <= ITEM_COUNT ? aRow : -1;
        }
    }
}
