package cn.enaium.fabricpvz.trainermod;

import cn.enaium.fabricpvz.api.lawn.LawnEvents;
import cn.enaium.fabricpvz.portable.lawn.AdviceType;
import cn.enaium.fabricpvz.portable.lawn.Board;
import cn.enaium.fabricpvz.portable.lawn.Coin;
import cn.enaium.fabricpvz.portable.lawn.DamageFlags;
import cn.enaium.fabricpvz.portable.lawn.GameScenes;
import cn.enaium.fabricpvz.portable.lawn.LawnCommon;
import cn.enaium.fabricpvz.portable.lawn.MessageStyle;
import cn.enaium.fabricpvz.portable.lawn.SeedPacket;
import cn.enaium.fabricpvz.portable.lawn.Zombie;
import cn.enaium.fabricpvz.portable.lawn.widget.GameButton;
import cn.enaium.fabricpvz.portable.lawn.widget.LawnDialog;
import cn.enaium.fabricpvz.portable.lawn.widget.LawnStoneButton;
import cn.enaium.fabricpvz.portable.sexy.Checkbox;
import cn.enaium.fabricpvz.portable.sexy.CheckboxListener;
import cn.enaium.fabricpvz.portable.sexy.Dialog;
import cn.enaium.fabricpvz.portable.sexy.Widget;
import cn.enaium.fabricpvz.portable.sexy.WidgetManager;
import cn.enaium.fabricpvz.portable.sexy.graphics.Font;
import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;
import cn.enaium.fabricpvz.portable.sexy.graphics.Image;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    /** 秒杀伤害与标志：与 Board.killAllZombiesInRadius 的非燃烧路径同参（Board.cpp:9312）。 */
    private static final int KILL_DAMAGE = 1800;
    private static final int KILL_FLAGS = DamageFlags.DAMAGE_HITS_SHIELD_AND_BODY
            | (1 << DamageFlags.DAMAGE_DOESNT_LEAVE_BODY);

    /** 本模组的对话框 id：游戏 Dialogs 常量只用到 5x，取高位避开。 */
    private static final int TRAINER_DIALOG_ID = 900;
    /** 对话框内自建按钮/复选框的 id 段：避开 Dialog 的 1000/1001。 */
    private static final int ID_KILL_ALL = 2005;
    private static final int ID_CHECK_SUN = 2001;
    private static final int ID_CHECK_PLANT = 2002;
    private static final int ID_CHECK_COLLECT = 2003;
    private static final int ID_CHECK_RECHARGE = 2004;

    private static boolean infiniteSun;
    private static boolean freePlanting;
    private static boolean autoCollect;
    private static boolean instantRecharge;

    private static cn.enaium.fabricpvz.portable.lawn.LawnApp sApp;
    private static Board sBoard;
    /** 悬浮按钮；游戏初始化完成即挂进 WidgetManager，常驻所有界面。 */
    private static TrainerButton sButton;
    /** 对话框打开中（抑制悬浮按钮显示与拖拽歧义）。 */
    private static boolean sDialogOpen;
    /** 免费种植/自动收集已同步进哪个棋盘——换关卡即重新推送一次。 */
    private static Board sLastSyncedBoard;

    @Override
    public void onInitialize() {
        LOGGER.info("pvz-trainer-mod 已加载：常驻悬浮按钮（可拖动），点击展开原生对话框");
        // 资源装载完成后立即挂按钮（mWidgetManager 在 SexyAppBase 构造器里创建，此时已就绪）
        cn.enaium.fabricpvz.api.lifecycle.GameLifecycleEvents.INITIALIZED.register(TrainerMod::createButton);
        LawnEvents.TICK.register(TrainerMod::onTick);
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
        }

        // 持续生效项：逐 tick 强制写回
        if (infiniteSun && aPlaying) {
            theBoard.mSunMoney = INFINITE_SUN_VALUE;
        }
        if (instantRecharge && aPlaying) {
            for (SeedPacket aPacket : theBoard.mSeedBank.mSeedPackets) {
                if (aPacket != null) {
                    aPacket.mRefreshCounter = 0;
                    aPacket.mRefreshing = false;
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

            // 常驻：除加载画面与自身对话框展开外都显示；mVisible=false 不参与命中测试
            boolean aLoading = sApp.mGameScene == GameScenes.SCENE_LOADING;
            mVisible = !aLoading && !sDialogOpen;

            // 新界面/棋盘会把整屏 widget 重新加入管理器（排到按钮之上）：
            // 按钮不在末位就摘下重加，恢复置顶
            if (mVisible && !aManager.mWidgets.isEmpty()
                    && aManager.mWidgets.get(aManager.mWidgets.size() - 1) != this) {
                aManager.removeWidget(this);
                aManager.addWidget(this);
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
            sApp.addDialog(TRAINER_DIALOG_ID, new TrainerDialog());
        }
    }

    // -------------------------------------------------------------------------------------

    /**
     * 原生对话框：4 个原生复选框（前四项开关）+ 原生石钮"秒杀全场"，
     * 底部 footer 按钮"收 起"（BUTTONS_FOOTER 的标准 1000 号按钮）收回折叠。
     * modal=true：打开自动暂停棋盘，关闭自动恢复（modalOpen/modalClose 既有机制）。
     */
    private static final class TrainerDialog extends LawnDialog implements CheckboxListener {

        /** 原生 Checkbox 美术是 46×45（NewOptionsDialog 的同名常量），行高必须盖过它。 */
        private static final int CHECKBOX_WIDTH = 46;
        private static final int CHECKBOX_HEIGHT = 45;
        private static final int ROW_HEIGHT = 50;

        private final Checkbox mSunCheck;
        private final Checkbox mPlantCheck;
        private final Checkbox mCollectCheck;
        private final Checkbox mRechargeCheck;
        private final LawnStoneButton mKillButton;

        TrainerDialog() {
            super(sApp, TRAINER_DIALOG_ID, true, "修改器", "", "收 起", Dialog.BUTTONS_FOOTER);

            mSunCheck = LawnCommon.makeNewCheckbox(ID_CHECK_SUN, this, infiniteSun);
            mPlantCheck = LawnCommon.makeNewCheckbox(ID_CHECK_PLANT, this, freePlanting);
            mCollectCheck = LawnCommon.makeNewCheckbox(ID_CHECK_COLLECT, this, autoCollect);
            mRechargeCheck = LawnCommon.makeNewCheckbox(ID_CHECK_RECHARGE, this, instantRecharge);
            mKillButton = GameButton.makeButton(ID_KILL_ALL, this, "秒杀全场");
            // 石钮按贴图原生尺寸摆放（drawStoneButton 从 (0,0) 原尺寸拼接，控件矩形小了会被裁）
            int aStoneHeight = stoneArtHeight();
            mKillButton.resize(0, 0, stoneButtonWidth(2), aStoneHeight);

            // 追加高度：4 行复选框 + 秒杀按钮（贴图原生高）+ footer 按钮的活动区余量
            calcSize(80, 4 * ROW_HEIGHT + aStoneHeight + 56);

            // addDialog 只在 mWidth==0 时自动摆放，calcSize 后已是非零——自己居中，
            // 并保证对话框整体落在窗口内（子控件会被对话框矩形裁剪）
            int aX = Math.max(8, (sApp.mWidth - mWidth) / 2);
            int aY = Math.max(16, (sApp.mHeight - mHeight) / 2 - 12);
            resize(aX, aY, mWidth, mHeight);
        }

        @Override
        public void addedToManager(WidgetManager theWidgetManager) {
            super.addedToManager(theWidgetManager);
            addWidget(mSunCheck);
            addWidget(mPlantCheck);
            addWidget(mCollectCheck);
            addWidget(mRechargeCheck);
            addWidget(mKillButton);
        }

        @Override
        public void removedFromManager(WidgetManager theWidgetManager) {
            removeWidget(mSunCheck);
            removeWidget(mPlantCheck);
            removeWidget(mCollectCheck);
            removeWidget(mRechargeCheck);
            removeWidget(mKillButton);
            super.removedFromManager(theWidgetManager);
        }

        @Override
        public void resize(int theX, int theY, int theWidth, int theHeight) {
            super.resize(theX, theY, theWidth, theHeight); // 先让基类排好标题与 footer 按钮

            int aLeft = getLeft();
            int aTop = getTop();
            mSunCheck.resize(aLeft, aTop + 0 * ROW_HEIGHT, CHECKBOX_WIDTH, CHECKBOX_HEIGHT);
            mPlantCheck.resize(aLeft, aTop + 1 * ROW_HEIGHT, CHECKBOX_WIDTH, CHECKBOX_HEIGHT);
            mCollectCheck.resize(aLeft, aTop + 2 * ROW_HEIGHT, CHECKBOX_WIDTH, CHECKBOX_HEIGHT);
            mRechargeCheck.resize(aLeft, aTop + 3 * ROW_HEIGHT, CHECKBOX_WIDTH, CHECKBOX_HEIGHT);
            mKillButton.resize(aLeft, aTop + 4 * ROW_HEIGHT + 6, mKillButton.mWidth,
                    mKillButton.mHeight);
        }

        @Override
        public void draw(Graphics g) {
            super.draw(g);

            // 条目文字：画在每个复选框右侧、垂直居中（基类只画标题与正文）
            Font aFont = cn.enaium.fabricpvz.portable.lawn.Resources
                    .getFontById(cn.enaium.fabricpvz.portable.lawn.Resources.FONT_BRIANNETOD12_ID);
            g.setFont(aFont);
            String[] aNames = {"无限阳光", "免费种植", "自动收集", "秒冷却"};
            int aLeft = getLeft() + CHECKBOX_WIDTH + 10;
            for (int i = 0; i < aNames.length; i++) {
                g.setColor(new cn.enaium.fabricpvz.portable.sexy.Color(232, 218, 160));
                g.drawString(aNames[i], aLeft,
                        getTop() + i * ROW_HEIGHT + (CHECKBOX_HEIGHT - aFont.mHeight) / 2
                                + aFont.mAscent);
            }
        }

        // Dialog 的 ButtonListener：1000 = footer"收 起"（折叠回悬浮按钮），2005 = 秒杀
        @Override
        public void buttonDepress(int theId) {
            if (theId == Dialog.ID_FOOTER) {
                sDialogOpen = false;
                sApp.killDialog(mId);
                return;
            }
            if (theId == ID_KILL_ALL && sBoard != null
                    && sApp.mGameScene == GameScenes.SCENE_PLAYING) {
                killAllZombies(sBoard);
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
                case ID_CHECK_RECHARGE -> {
                    instantRecharge = theChecked;
                    show(sBoard, "秒冷却：" + onOff(theChecked));
                }
                default -> {
                }
            }
        }
    }
}
