package cn.enaium.fabricpvz.modmenu;

import cn.enaium.fabricpvz.portable.lawn.LawnApp;
import cn.enaium.fabricpvz.portable.lawn.Resources;
import cn.enaium.fabricpvz.portable.lawn.widget.LawnDialog;
import cn.enaium.fabricpvz.portable.lawn.DrawStringJustification;
import cn.enaium.fabricpvz.portable.pvzp.PvzpCommon;
import cn.enaium.fabricpvz.portable.sexy.Color;
import cn.enaium.fabricpvz.portable.sexy.Dialog;
import cn.enaium.fabricpvz.portable.sexy.Rect;
import cn.enaium.fabricpvz.portable.sexy.Widget;
import cn.enaium.fabricpvz.portable.sexy.WidgetManager;
import cn.enaium.fabricpvz.portable.sexy.graphics.Font;
import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;
import cn.enaium.fabricpvz.portable.sexy.graphics.Image;
import cn.enaium.fabricpvz.portable.sexy.graphics.MemoryImage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * 打开链接前的确认框：标题 / URL / 警示语居中，底部一行三枚石钮
 * （是 — 复制链接到剪贴板 — 否）。
 *
 * <p>三枚按钮全部自绘：石钮条（left+middle+right 原样拼成的一条）整体缩放到各自
 * 的矩形——矩形任意大小都不会被裁切，"是/否"取正方形，"复制"取能容下文字的宽条。
 * 点击区域与绘制共用同一组矩形（{@link #mButtonRects}），所见即所点。
 */
final class OpenUrlDialog extends LawnDialog {

    private static final Logger LOGGER = LoggerFactory.getLogger("pvz-modmenu");

    private static final String TITLE = "确定要打开以下网页？";
    private static final String WARNING = "永远不要打开你不信任的人提供的链接";
    private static final String[] BUTTON_LABELS = {"是", "复制链接到剪贴板", "否"};
    private static final int BUTTON_GAP = 12;

    private final String mUrl;
    /** URL 按 draw 同款宽度换行出的行（构造时算好，高度预算与绘制共用同一份数据）。 */
    private final List<String> mUrlLines;
    /** 预拼的一条完整石钮；三枚按钮都是它按各自矩形的缩放。 */
    private Image mStoneStrip;
    /** 三枚按钮的矩形（对话框坐标），draw 时重建、命中时查询。 */
    private final List<Rect> mButtonRects = new ArrayList<>();
    private int mHoverButton = -1;

    /**
     * 焦点代理：模态框自身持有的焦点会在 {@code addBaseModal} 建立模态时被清掉
     * （对话框对自己而言"位于模态之下"），而子控件不算在模态之下——用一枚零尺寸
     * 子控件代持焦点、代收键盘事件。
     */
    private final Widget mFocusProxy = new Widget() {
        @Override
        public boolean wantsFocus() {
            return true;
        }

        @Override
        public void keyDown(int theKey) {
            if (theKey == cn.enaium.fabricpvz.portable.sexy.KeyCode.KEYCODE_ESCAPE) {
                mApp.killDialog(mId);
            }
        }
    };

    OpenUrlDialog(LawnApp theApp, String theUrl) {
        super(theApp, ModMenuMod.DIALOG_OPEN_URL, true, "打开链接", "", "", Dialog.BUTTONS_NONE);
        mUrl = theUrl;

        // 宽度预算：内容区要容下三枚按钮一行。calcSize 的追加量加在"边距+标题宽"的最小宽上，
        // 所以按目标内容宽反推：extraX = 目标内容宽 - 标题宽（其后向上取整只会更大）。
        Font aLabelFont = Resources.getFontById(Resources.FONT_DWARVENTODCRAFT18GREENINSET_ID);
        int aSquare = stoneArtHeight();
        int aCopyWidth = aSquare * 2 + aLabelFont.stringWidth(BUTTON_LABELS[1]) + 28;
        int aRowWidth = aSquare * 2 + aCopyWidth + BUTTON_GAP * 2;
        int aHeaderWidth = mHeaderFont.stringWidth(mDialogHeader);
        int aContentWidth = Math.max(aRowWidth, aHeaderWidth);

        Font aBody = Resources.getFontById(Resources.FONT_DWARVENTODCRAFT15_ID);
        mUrlLines = wrap(aBody, aContentWidth);

        int aBodyHeight = 30 + mUrlLines.size() * 20 + 14 + 20;
        // 高度预算：正文从内容顶排下来，按钮行锚在底部边距带（见 drawButtons），总高要容下两者
        int aNeededHeight = getTop() + aBodyHeight + 8 + aSquare
                + mContentInsets.mBottom + mBackgroundInsets.mBottom + 8;
        // 高度同理：追加量加在最小高度（边距 + 头部高度 + 按钮带）上，按目标总高反推
        int aHeaderHeight = -mHeaderFont.getAscentPadding() + mHeaderFont.getHeight() + mSpaceAfterHeader;
        int aMinHeight = mBackgroundInsets.mTop + mBackgroundInsets.mBottom + mContentInsets.mTop
                + mContentInsets.mBottom + DIALOG_HEADER_OFFSET + aHeaderHeight + mButtonHeight;
        calcSize(Math.max(0, aContentWidth - aHeaderWidth), Math.max(0, aNeededHeight - aMinHeight));
        theApp.centerDialog(this, mWidth, mHeight);
    }

    /** 石钮贴图原生高（正方形按钮的边长）；未装载时回退 46。 */
    private static int stoneArtHeight() {
        Image aLeft = Resources.getImageById(cn.enaium.fabricpvz.portable.lawn.ImageID.IMAGE_BUTTON_LEFT);
        return aLeft != null ? aLeft.getHeight() : 46;
    }

    /** 预拼一条完整石钮（left+1×middle+right），所有按钮都由它缩放绘制。 */
    private Image stoneStrip() {
        if (mStoneStrip != null || stoneArtHeight() <= 0) {
            return mStoneStrip;
        }
        Image aLeft = Resources.getImageById(cn.enaium.fabricpvz.portable.lawn.ImageID.IMAGE_BUTTON_LEFT);
        Image aMiddle = Resources.getImageById(cn.enaium.fabricpvz.portable.lawn.ImageID.IMAGE_BUTTON_MIDDLE);
        Image aRight = Resources.getImageById(cn.enaium.fabricpvz.portable.lawn.ImageID.IMAGE_BUTTON_RIGHT);
        try {
            int aW = aLeft.getWidth() + aMiddle.getWidth() + aRight.getWidth();
            MemoryImage anImage = new MemoryImage();
            anImage.create(aW, aLeft.getHeight());
            Graphics aG = new Graphics(anImage);
            aG.drawImage(aLeft, 0, 0);
            aG.drawImage(aMiddle, aLeft.getWidth(), 0);
            aG.drawImage(aRight, aLeft.getWidth() + aMiddle.getWidth(), 0);
            mStoneStrip = anImage;
        } catch (RuntimeException e) {
            LOGGER.warn("石钮条拼接失败", e);
        }
        return mStoneStrip;
    }

    /** URL 按 15 号字体逐字换行（中文友好）。 */
    private List<String> wrap(Font theFont, int theMaxWidth) {
        List<String> aLines = new ArrayList<>();
        StringBuilder aCur = new StringBuilder();
        for (int i = 0; i < mUrl.length(); i++) {
            char aChar = mUrl.charAt(i);
            if (!aCur.isEmpty() && theFont.stringWidth(aCur.toString() + aChar) > theMaxWidth) {
                aLines.add(aCur.toString());
                aCur.setLength(0);
            }
            aCur.append(aChar);
        }
        aLines.add(aCur.toString());
        return aLines;
    }

    @Override
    public void draw(Graphics g) {
        super.draw(g);

        Font aTitle = Resources.getFontById(Resources.FONT_DWARVENTODCRAFT18_ID);
        Font aBody = Resources.getFontById(Resources.FONT_DWARVENTODCRAFT15_ID);
        int aCenterX = getLeft() + getWidth() / 2;
        int aY = getTop();

        g.setColor(new Color(255, 220, 80));
        PvzpCommon.pvzpDrawString(g, TITLE, aCenterX, aY, aTitle,
                new Color(255, 220, 80), DrawStringJustification.DS_ALIGN_CENTER);
        aY += 30;

        g.setColor(new Color(255, 255, 255));
        for (String aLine : mUrlLines) {
            PvzpCommon.pvzpDrawString(g, aLine, aCenterX, aY, aBody,
                    new Color(255, 255, 255), DrawStringJustification.DS_ALIGN_CENTER);
            aY += 20;
        }
        aY += 14;

        PvzpCommon.pvzpDrawString(g, WARNING, aCenterX, aY, aBody,
                new Color(190, 120, 110), DrawStringJustification.DS_ALIGN_CENTER);

        drawButtons(g);
    }

    /** 三枚石钮一行：正方形 — 宽条 — 正方形，行内居中；纵向锚在底部石框带上（与原生 footer 按钮同位）。 */
    private void drawButtons(Graphics g) {
        Image aStrip = stoneStrip();
        if (aStrip == null) {
            return;
        }

        Font aMeasure = Resources.getFontById(Resources.FONT_DWARVENTODCRAFT18GREENINSET_ID);
        int aSquare = stoneArtHeight();
        int aCopyWidth = aSquare * 2 + aMeasure.stringWidth(BUTTON_LABELS[1]) + 28;
        int[] aWidths = {aSquare, aCopyWidth, aSquare};
        int aRowWidth = aSquare * 2 + aCopyWidth + BUTTON_GAP * 2;
        int aX = getLeft() + (getWidth() - aRowWidth) / 2;
        // 与 LawnDialog.resizeBase 摆原生 footer 按钮同款：底边落在底部内边距带上
        int aY = mHeight - mContentInsets.mBottom - mBackgroundInsets.mBottom - aSquare + 2;

        mButtonRects.clear();
        for (int i = 0; i < 3; i++) {
            Rect aRect = new Rect(aX, aY, aWidths[i], aSquare);
            // 完整石钮条缩放到矩形（三段比例随缩放保留，任意大小无裁切）
            g.drawImage(aStrip, aRect, new Rect(0, 0, aStrip.getWidth(), aStrip.getHeight()));
            drawStoneLabel(g, aRect);
            if (mHoverButton == i) {
                g.setColor(new Color(255, 220, 80, 200));
                g.drawRect(aRect.mX - 1, aRect.mY - 1, aRect.mWidth + 2, aRect.mHeight + 2);
            }
            mButtonRects.add(aRect);
            aX += aWidths[i] + BUTTON_GAP;
        }
    }

    /** 石钮文字（与 GameButton.drawStoneButton 同款绿色 inset 字体与居中公式）。 */
    private void drawStoneLabel(Graphics g, Rect theRect) {
        int anIndex = mButtonRects.size();
        Font aFont = Resources.getFontById(Resources.FONT_DWARVENTODCRAFT18GREENINSET_ID);
        g.setColor(Color.White);
        g.drawString(BUTTON_LABELS[anIndex],
                theRect.mX + (theRect.mWidth - aFont.stringWidth(BUTTON_LABELS[anIndex])) / 2 + 1,
                theRect.mY + (theRect.mHeight - aFont.getAscent() / 6 - 1 + aFont.getAscent()) / 2 - 4);
    }

    @Override
    public void mouseMove(int theX, int theY) {
        int aPrev = mHoverButton;
        mHoverButton = hoverIndex(theX, theY);
        if (aPrev != mHoverButton) {
            markDirty();
        }
    }

    @Override
    public void mouseDown(int theX, int theY, int theBtnNum, int theClickCount) {
        switch (hoverIndex(theX, theY)) {
            case 0 -> {
                openBrowser(mUrl);
                mApp.killDialog(mId);
            }
            case 1 -> {
                copyToClipboard(mUrl);
                mApp.killDialog(mId);
            }
            case 2 -> mApp.killDialog(mId);
            default -> super.mouseDown(theX, theY, theBtnNum, theClickCount);
        }
    }

    @Override
    public void addedToManager(WidgetManager theWidgetManager) {
        super.addedToManager(theWidgetManager);
        // 焦点给子控件代理：addBaseModal 随后建立模态，只有子控件持有的焦点不会被清
        addWidget(mFocusProxy);
        theWidgetManager.setFocus(mFocusProxy);
    }

    @Override
    public void removedFromManager(WidgetManager theWidgetManager) {
        removeWidget(mFocusProxy);
        super.removedFromManager(theWidgetManager);
    }

    /** ESC 关闭（等同"否"）；键盘事件由 {@link #mFocusProxy} 代收。 */
    @Override
    public void keyDown(int theKey) {
        if (theKey == cn.enaium.fabricpvz.portable.sexy.KeyCode.KEYCODE_ESCAPE) {
            mApp.killDialog(mId);
            return;
        }
        super.keyDown(theKey);
    }

    /** 指针所在的按钮序号；不在任何按钮上返回 -1。 */
    private int hoverIndex(int theX, int theY) {
        for (int i = 0; i < mButtonRects.size(); i++) {
            if (mButtonRects.get(i).contains(theX, theY)) {
                return i;
            }
        }
        return -1;
    }

    private static void openBrowser(String theUrl) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(theUrl));
            } else {
                LOGGER.warn("桌面环境不支持打开浏览器：{}", theUrl);
            }
        } catch (Exception e) {
            LOGGER.warn("打开网页失败：{}", theUrl, e);
        }
    }

    private static void copyToClipboard(String theUrl) {
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(theUrl), null);
            LOGGER.info("链接已复制到剪贴板：{}", theUrl);
        } catch (Exception e) {
            LOGGER.warn("复制到剪贴板失败：{}", theUrl, e);
        }
    }
}
