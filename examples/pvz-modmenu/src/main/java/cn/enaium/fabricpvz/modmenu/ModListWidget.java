package cn.enaium.fabricpvz.modmenu;

import cn.enaium.fabricpvz.portable.lawn.Resources;
import cn.enaium.fabricpvz.portable.sexy.Color;
import cn.enaium.fabricpvz.portable.sexy.KeyCode;
import cn.enaium.fabricpvz.portable.sexy.Rect;
import cn.enaium.fabricpvz.portable.sexy.Widget;
import cn.enaium.fabricpvz.portable.sexy.graphics.Font;
import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;

import java.util.List;

/**
 * 左侧模组列表：每行 58px——左侧 44×44 图标框（无图标画 id 首字母占位），
 * 右侧第一行模组名、第二行描述（单行截断）。选中行高亮。
 *
 * <p>滚动：右侧 8px 轨道的拖动滚动条（游戏滚轮事件只派发给焦点控件，
 * 拖动滚动条是可靠的做法）；行点击交给宿主对话框 {@code setSelected}。
 */
final class ModListWidget extends Widget {

    static final int ROW_HEIGHT = 58;
    static final int ICON_SIZE = 44;
    private static final int SCROLLBAR_WIDTH = 8;

    private final ModMenuDialog mOwner;
    private final List<ModEntry> mEntries;
    /** 向下滚动的像素偏移（0 = 顶部）。 */
    private int mScrollOffset;
    /** 拖动滚动条中（抓取时 thumb 的 y 偏移）。 */
    private boolean mDraggingThumb;
    private int mThumbGrabOffset;
    /** 上一帧鼠标位置（区分按下即拖）。 */
    private boolean mPressedOnThumb;

    ModListWidget(ModMenuDialog theOwner, List<ModEntry> theEntries) {
        mOwner = theOwner;
        mEntries = theEntries;
        mClip = true; // 超出列表矩形的行被裁掉（WidgetContainer.drawAll 的 mClip）
    }

    /** 内容总高与视口高，供 thumb 尺寸/位置计算。 */
    private int contentHeight() {
        return mEntries.size() * ROW_HEIGHT;
    }

    private int viewHeight() {
        return mHeight;
    }

    private int maxScroll() {
        return Math.max(0, contentHeight() - viewHeight());
    }

    /** thumb 在轨道上的 y 与高度。 */
    private int thumbHeight() {
        int aTrack = viewHeight() - 2;
        if (contentHeight() <= 0) {
            return aTrack;
        }
        return Math.max(24, aTrack * viewHeight() / contentHeight());
    }

    private int thumbY() {
        int aMax = maxScroll();
        if (aMax == 0) {
            return 1;
        }
        int aTrack = viewHeight() - 2 - thumbHeight();
        return 1 + aTrack * mScrollOffset / aMax;
    }

    @Override
    public void draw(Graphics g) {
        // 列表底色（半透明深色叠在对话框棕底上）
        g.setColor(new Color(0, 0, 0, 110));
        g.fillRect(0, 0, mWidth, mHeight);

        Font aNameFont = Resources.getFontById(Resources.FONT_DWARVENTODCRAFT18_ID);
        Font aDescFont = Resources.getFontById(Resources.FONT_DWARVENTODCRAFT15_ID);

        int aListWidth = mWidth - SCROLLBAR_WIDTH - 4;
        int aFirstRow = Math.max(0, mScrollOffset / ROW_HEIGHT);
        int aVisibleRows = mHeight / ROW_HEIGHT + 1;

        for (int i = aFirstRow; i < mEntries.size() && i < aFirstRow + aVisibleRows; i++) {
            ModEntry aEntry = mEntries.get(i);
            int aRowY = i * ROW_HEIGHT - mScrollOffset;
            if (aRowY + ROW_HEIGHT < 0) {
                continue;
            }

            // 选中高亮
            if (i == mOwner.getSelectedIndex()) {
                g.setColor(new Color(255, 220, 80, 60));
                g.fillRect(0, aRowY, aListWidth, ROW_HEIGHT);
                g.setColor(new Color(255, 220, 80));
                g.drawRect(0, aRowY, aListWidth, ROW_HEIGHT);
            }

            // 图标 / 占位
            int anIconX = 4;
            int anIconY = aRowY + (ROW_HEIGHT - ICON_SIZE) / 2;
            if (aEntry.icon != null) {
                int aSrcW = aEntry.icon.getWidth();
                int aSrcH = aEntry.icon.getHeight();
                // 纵横比 fit 到 44×44
                int aFit = Math.min(ICON_SIZE * 256 / aSrcW, ICON_SIZE * 256 / aSrcH);
                int aW = aSrcW * aFit / 256;
                int aH = aSrcH * aFit / 256;
                g.drawImage(aEntry.icon,
                        new Rect(anIconX + (ICON_SIZE - aW) / 2, anIconY + (ICON_SIZE - aH) / 2, aW, aH),
                        new Rect(0, 0, aSrcW, aSrcH));
            } else {
                g.setColor(new Color(60, 58, 74));
                g.fillRect(anIconX, anIconY, ICON_SIZE, ICON_SIZE);
                g.setColor(new Color(120, 118, 138));
                g.drawRect(anIconX, anIconY, ICON_SIZE, ICON_SIZE);
                g.setColor(new Color(220, 218, 230));
                g.setFont(aNameFont);
                String aInitial = aEntry.name.substring(0, 1);
                g.drawString(aInitial, anIconX + (ICON_SIZE - aNameFont.stringWidth(aInitial)) / 2,
                        anIconY + (ICON_SIZE - aNameFont.mHeight) / 2 + aNameFont.mAscent);
            }

            // 名字行 + 描述行（单行截断；drawString 的 y 是基线，从"字形顶部"换算要加 ascent）
            int aTextX = anIconX + ICON_SIZE + 8;
            g.setColor(new Color(255, 255, 255));
            g.setFont(aNameFont);
            g.drawString(ellipsize(aEntry.name, aNameFont, aListWidth - aTextX - 4),
                    aTextX, aRowY + 6 + aNameFont.getAscent());

            g.setColor(new Color(176, 176, 190));
            g.setFont(aDescFont);
            String aDesc = aEntry.description.isEmpty() ? "（无描述）" : aEntry.description;
            g.drawString(ellipsize(aDesc, aDescFont, aListWidth - aTextX - 4),
                    aTextX, aRowY + 30 + aDescFont.getAscent());
        }

        // 滚动条轨道 + thumb
        if (maxScroll() > 0) {
            g.setColor(new Color(0, 0, 0, 90));
            g.fillRect(mWidth - SCROLLBAR_WIDTH, 0, SCROLLBAR_WIDTH, mHeight);
            g.setColor(new Color(200, 196, 210, 200));
            g.fillRect(mWidth - SCROLLBAR_WIDTH + 1, thumbY(), SCROLLBAR_WIDTH - 2, thumbHeight());
        }
    }

    @Override
    public void mouseDown(int theX, int theY, int theBtnNum, int theClickCount) {
        mPressedOnThumb = false;
        // 滚动条区域：进入拖动
        if (theX >= mWidth - SCROLLBAR_WIDTH && maxScroll() > 0) {
            int aThumbTop = thumbY();
            if (theY >= aThumbTop && theY < aThumbTop + thumbHeight()) {
                mDraggingThumb = true;
                mThumbGrabOffset = theY - aThumbTop;
            } else {
                mDraggingThumb = true;
                mThumbGrabOffset = thumbHeight() / 2; // 点轨道：thumb 跳到点击处附近
                dragThumb(theY);
            }
            mPressedOnThumb = true;
            return;
        }
        // 行点击：交给宿主选中
        int aRow = (theY + mScrollOffset) / ROW_HEIGHT;
        mOwner.setSelected(aRow);
    }

    @Override
    public void mouseUp(int theX, int theY, int theBtnNum, int theClickCount) {
        mDraggingThumb = false;
    }

    @Override
    public void mouseMove(int theX, int theY) {
        if (mDraggingThumb) {
            dragThumb(theY);
        }
    }

    @Override
    public void mouseDrag(int theX, int theY) {
        // 按住拖动时管理器派发的是 mouseDrag（WidgetManager.mouseDrag → mLastDownWidget），走同一路径
        mouseMove(theX, theY);
    }

    /** 点击列表（行或滚动条）时抓取焦点：滚轮与键盘事件只派发给焦点控件。 */
    @Override
    public boolean wantsFocus() {
        return true;
    }

    /** 滚轮滚动：指针在列表内滚列表，否则滚动宿主对话框的详情区（滚轮只派发给焦点控件）。 */
    @Override
    public void mouseWheel(int theDelta) {
        var anAbsPos = getAbsPos();
        int aMouseX = mWidgetManager.mLastMouseX;
        int aMouseY = mWidgetManager.mLastMouseY;
        if (aMouseX >= anAbsPos.mX && aMouseX < anAbsPos.mX + mWidth
                && aMouseY >= anAbsPos.mY && aMouseY < anAbsPos.mY + mHeight) {
            setScrollOffset(mScrollOffset - theDelta * ROW_HEIGHT);
        } else {
            mOwner.scrollDetail(theDelta);
        }
    }

    @Override
    public void keyDown(int theKey) {
        boolean aShift = mWidgetManager != null
                && mWidgetManager.mKeyDown[KeyCode.KEYCODE_SHIFT];
        switch (theKey) {
            case KeyCode.KEYCODE_UP, KeyCode.KEYCODE_NUMPAD8, KeyCode.KEYCODE_NUMPAD4 ->
                moveSelection(-1);
            case KeyCode.KEYCODE_DOWN, KeyCode.KEYCODE_NUMPAD2, KeyCode.KEYCODE_NUMPAD6 ->
                moveSelection(1);
            case KeyCode.KEYCODE_TAB -> moveSelection(aShift ? -1 : 1); // TAB 循环移动高亮
            default -> mOwner.keyDown(theKey); // 其余按键（ESC 等）交宿主对话框处理
        }
    }

    /** 高亮移动一格（循环），并把选中行滚进可视区。 */
    private void moveSelection(int theDelta) {
        if (mEntries.isEmpty()) {
            return;
        }
        int aIndex = mOwner.getSelectedIndex() + theDelta;
        if (aIndex < 0) {
            aIndex = mEntries.size() - 1;
        } else if (aIndex >= mEntries.size()) {
            aIndex = 0;
        }
        mOwner.setSelected(aIndex);
        scrollRowIntoView(aIndex);
    }

    /** 选中行进入可视区（行在视口外才滚动）。 */
    private void scrollRowIntoView(int theIndex) {
        int aRowY = theIndex * ROW_HEIGHT;
        if (aRowY < mScrollOffset) {
            setScrollOffset(aRowY);
        } else if (aRowY + ROW_HEIGHT > mScrollOffset + viewHeight()) {
            setScrollOffset(aRowY + ROW_HEIGHT - viewHeight());
        }
    }

    private void setScrollOffset(int theOffset) {
        mScrollOffset = Math.max(0, Math.min(maxScroll(), theOffset));
    }

    private void dragThumb(int theMouseY) {
        int aTrack = viewHeight() - 2 - thumbHeight();
        if (aTrack <= 0) {
            return;
        }
        int aThumbY = theMouseY - mThumbGrabOffset;
        mScrollOffset = Math.max(0, Math.min(maxScroll(),
                aThumbY * maxScroll() / aTrack));
    }

    /** 宽度超限的文字以 "…" 截断（逐字测量，中文友好）。 */
    static String ellipsize(String theText, Font theFont, int theMaxWidth) {
        if (theFont.stringWidth(theText) <= theMaxWidth) {
            return theText;
        }
        StringBuilder aBuilder = new StringBuilder();
        for (int i = 0; i < theText.length(); i++) {
            String aNext = aBuilder.toString() + theText.charAt(i) + "…";
            if (theFont.stringWidth(aNext) > theMaxWidth) {
                break;
            }
            aBuilder.append(theText.charAt(i));
        }
        return aBuilder.append('…').toString();
    }
}
