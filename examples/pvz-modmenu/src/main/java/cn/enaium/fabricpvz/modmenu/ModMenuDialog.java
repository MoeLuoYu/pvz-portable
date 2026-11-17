package cn.enaium.fabricpvz.modmenu;

import cn.enaium.fabricpvz.modmenu.api.ModMenuApi;
import cn.enaium.fabricpvz.portable.lawn.LawnApp;
import cn.enaium.fabricpvz.portable.lawn.Resources;
import cn.enaium.fabricpvz.portable.lawn.widget.LawnDialog;
import cn.enaium.fabricpvz.portable.sexy.Color;
import cn.enaium.fabricpvz.portable.sexy.Dialog;
import cn.enaium.fabricpvz.portable.sexy.Rect;
import cn.enaium.fabricpvz.portable.sexy.WidgetManager;
import cn.enaium.fabricpvz.portable.sexy.graphics.Font;
import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;
import cn.enaium.fabricpvz.portable.sexy.graphics.Image;

import java.awt.Desktop;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * 模组管理界面：左侧 {@link ModListWidget} 模组列表，右侧选中模组的详情——
 * 名称、版本、描述、官网/问题/配置三枚图标按钮（模组自带 64×64 图，按之前的排版
 * 横排在描述下方）、链接（可点文本）、许可证、模组贡献者（作者/贡献者）。
 *
 * <p>链接（官网/问题/链接条目）点击后打开 {@link OpenUrlDialog} 三按钮确认框。
 * 配置按钮只在目标模组声明了 {@code modmenu} 配置入口点（{@link ModMenuApi}）时显示。
 * 图标按钮不走石钮贴图（其拼接宽度不可控、会溢出面板），命中与置灰在对话框内自理。
 */
final class ModMenuDialog extends LawnDialog {

    private static final int LIST_WIDTH = 330;
    private static final int PANEL_GAP = 16;
    private static final int DETAIL_MAX_DESC_LINES = 3;

    private final List<ModEntry> mEntries;
    private final ModListWidget mListWidget;
    /** 三枚图标按钮的贴图（website/issue/config），加载失败为 null（不画不响应）。 */
    private final Image[] mButtonArt = new Image[3];
    /** 石钮条底部阴影的像素数（首次绘制时实测并缓存）。 */
    private Integer mStripShadow;

    /** 详情下区（链接起到末尾）的可滚动状态。 */
    private static final int DETAIL_SCROLLBAR_WIDTH = 8;
    private Rect mDetailRegion = new Rect(0, 0, 0, 0);
    private int mDetailScrollOffset;
    private int mDetailContentHeight;
    private int mDetailViewHeight;
    private boolean mDetailDraggingThumb;
    private int mDetailThumbGrabOffset;
    /** 预拼的一条完整石钮（left+1×middle+right），绘制时整体缩放成正方形。 */
    private Image mStoneStrip;
    private int mHoverButton = -1;
    private int mSelectedIndex;

    /** 链接条目的可点击区域（对话框坐标），每次 draw 重建。 */
    private final List<Rect> mLinkRects = new ArrayList<>();
    private final List<String> mLinkUrls = new ArrayList<>();
    /** 三枚图标按钮的可点击区域（对话框坐标），每次 draw 重建。 */
    private final List<Rect> mButtonRects = new ArrayList<>();
    private final List<Integer> mButtonIds = new ArrayList<>();

    /** 石钮贴图原生高（正方形按钮的边长）；未装载时回退 46。 */
    private static int stoneArtHeight() {
        Image aLeft = Resources.getImageById(cn.enaium.fabricpvz.portable.lawn.ImageID.IMAGE_BUTTON_LEFT);
        return aLeft != null ? aLeft.getHeight() : 46;
    }

    private static int stoneButtonWidth(int theMiddleRepeats) {
        Image aLeft = Resources.getImageById(cn.enaium.fabricpvz.portable.lawn.ImageID.IMAGE_BUTTON_LEFT);
        Image aMiddle = Resources.getImageById(cn.enaium.fabricpvz.portable.lawn.ImageID.IMAGE_BUTTON_MIDDLE);
        Image aRight = Resources.getImageById(cn.enaium.fabricpvz.portable.lawn.ImageID.IMAGE_BUTTON_RIGHT);
        if (aLeft == null || aMiddle == null || aRight == null) {
            return 110;
        }
        return aLeft.getWidth() + aRight.getWidth() + theMiddleRepeats * aMiddle.getWidth();
    }

    /**
     * 预拼一条完整石钮（left+1×middle+right 原样拼接）到临时位图——正方形按钮
     * 由它整体缩放而来，避免三段在窄矩形里被裁/被错误九宫格化。
     */
    private Image buildStoneStrip() {
        int aW = stoneButtonWidth(1);
        int aH = stoneArtHeight();
        if (mStoneStrip != null || aH <= 0) {
            return mStoneStrip;
        }
        try {
            cn.enaium.fabricpvz.portable.sexy.graphics.MemoryImage anImage =
                    new cn.enaium.fabricpvz.portable.sexy.graphics.MemoryImage();
            anImage.create(aW, aH);
            Graphics aG = new Graphics(anImage);
            aG.drawImage(Resources.getImageById(cn.enaium.fabricpvz.portable.lawn.ImageID.IMAGE_BUTTON_LEFT), 0, 0);
            int aX = Resources.getImageById(cn.enaium.fabricpvz.portable.lawn.ImageID.IMAGE_BUTTON_LEFT).getWidth();
            Image aMiddle = Resources.getImageById(cn.enaium.fabricpvz.portable.lawn.ImageID.IMAGE_BUTTON_MIDDLE);
            int aMidW = aMiddle.getWidth();
            while (aX + aMidW <= aW - Resources.getImageById(cn.enaium.fabricpvz.portable.lawn.ImageID.IMAGE_BUTTON_RIGHT).getWidth()) {
                aG.drawImage(aMiddle, aX, 0);
                aX += aMidW;
            }
            aG.drawImage(Resources.getImageById(cn.enaium.fabricpvz.portable.lawn.ImageID.IMAGE_BUTTON_RIGHT), aW - Resources.getImageById(cn.enaium.fabricpvz.portable.lawn.ImageID.IMAGE_BUTTON_RIGHT).getWidth(), 0);
            mStoneStrip = anImage;
        } catch (RuntimeException e) {
            org.slf4j.LoggerFactory.getLogger("pvz-modmenu").warn("石钮条拼接失败", e);
        }
        return mStoneStrip;
    }

    ModMenuDialog(LawnApp theApp) {
        super(theApp, ModMenuMod.DIALOG_MODMENU, true, "模 组", "", "返 回", Dialog.BUTTONS_FOOTER);

        mEntries = ModEntry.loadAll();

        mListWidget = new ModListWidget(this, mEntries);
        for (int i = 0; i < mButtonArt.length; i++) {
            try {
                mButtonArt[i] = cn.enaium.fabricpvz.api.resource.ModImages.load(ModMenuMod.class,
                        List.of("art/website.png", "art/issue.png", "art/config.png").get(i));
            } catch (RuntimeException e) {
                org.slf4j.LoggerFactory.getLogger("pvz-modmenu").warn("按钮图加载失败", e);
            }
        }

        // 固定目标尺寸：calcSize 从最小尺寸起步，补足差额
        calcSize(Math.max(0, 740 - mWidth), Math.max(0, 590 - mHeight));
        theApp.centerDialog(this, mWidth, mHeight);
    }

    int getSelectedIndex() {
        return mSelectedIndex;
    }

    void setSelected(int theIndex) {
        if (theIndex < 0 || theIndex >= mEntries.size()) {
            return;
        }
        mSelectedIndex = theIndex;
        markDirty();
    }

    @Override
    public void addedToManager(WidgetManager theWidgetManager) {
        super.addedToManager(theWidgetManager);
        addWidget(mListWidget);
        // 初始焦点给列表：滚轮与键盘事件只派发给焦点控件；焦点在模组框内，开模态不会被清
        theWidgetManager.setFocus(mListWidget);
    }

    @Override
    public void removedFromManager(WidgetManager theWidgetManager) {
        removeWidget(mListWidget);
        super.removedFromManager(theWidgetManager);
    }

    @Override
    public void resize(int theX, int theY, int theWidth, int theHeight) {
        super.resize(theX, theY, theWidth, theHeight); // 基类排标题与 footer 按钮

        int aLeft = getLeft();
        int aTop = getTop();
        int aContentHeight = mHeight - aTop - mContentInsets.mBottom - mBackgroundInsets.mBottom - 66;
        int aContentWidth = getWidth();

        mListWidget.resize(aLeft, aTop, LIST_WIDTH, aContentHeight);
    }

    /** 详情文字/按钮区的宽度（横排三枚 64px 图标 + 间距，不依赖石钮贴图宽度）。 */
    private int textColumnWidth() {
        return getWidth() - LIST_WIDTH - PANEL_GAP;
    }

    @Override
    public void draw(Graphics g) {
        super.draw(g);

        ModEntry aEntry = mEntries.get(mSelectedIndex);
        Font aBig = Resources.getFontById(Resources.FONT_DWARVENTODCRAFT18_ID);
        Font aSmall = Resources.getFontById(Resources.FONT_DWARVENTODCRAFT15_ID);

        int aDetailLeft = getLeft() + LIST_WIDTH + PANEL_GAP;
        int aTextWidth = textColumnWidth();
        int aY = getTop();

        // 名称 + 版本
        g.setColor(new Color(255, 220, 80));
        g.setFont(aBig);
        g.drawString(ModListWidget.ellipsize(aEntry.name, aBig, aTextWidth), aDetailLeft, aY);
        aY += 26;
        g.setColor(new Color(176, 176, 190));
        g.setFont(aSmall);
        g.drawString("版本 " + aEntry.version, aDetailLeft, aY);
        aY += 20;
        g.drawString(aEntry.modId, aDetailLeft, aY);
        aY += 26;

        // 描述（换行，最多 6 行）
        g.setColor(new Color(232, 218, 160));
        String aDesc = aEntry.description.isEmpty() ? "（无描述）" : aEntry.description;
        List<String> aLines = wrap(aDesc, aSmall, aTextWidth);
        for (int i = 0; i < aLines.size() && i < DETAIL_MAX_DESC_LINES; i++) {
            g.drawString(aLines.get(i), aDetailLeft, aY);
            aY += 20;
        }
        if (aLines.size() > DETAIL_MAX_DESC_LINES) {
            g.drawString("……", aDetailLeft, aY);
            aY += 20;
        }

        // 三枚正方形石钮横排（官网/问题/配置）：紧跟描述结束（流式排版，无固定行距）
        // 未设置置灰，配置未声明入口点整枚不画
        int aButtonY = aY + 8;
        drawIconButtons(g, aEntry, aDetailLeft, aButtonY, aTextWidth);

        // 链接起到末尾为可滚动区：内容超出可视高时出滚动条（拖动）并接受滚轮
        int aRegionTop = aButtonY + stoneArtHeight() + 18;
        // 底部停在对话框底部石框带上方（用底部石图实测高收口），也不越过"返回"按钮上沿
        int aBottomBandTop = mHeight - Resources
                .getImageById(cn.enaium.fabricpvz.portable.lawn.ImageID.IMAGE_DIALOG_BOTTOMLEFT)
                .getHeight();
        int aFooterButtonTop = mHeight - mContentInsets.mBottom - mBackgroundInsets.mBottom
                - stoneArtHeight() + 2;
        int aRegionBottom = Math.min(aBottomBandTop - 8, aFooterButtonTop - 6);
        int aViewHeight = Math.max(0, aRegionBottom - aRegionTop);
        List<DetailRow> aRows = buildDetailRows(aEntry);
        mDetailContentHeight = aRows.size() * 20;
        mDetailViewHeight = aViewHeight;
        mDetailScrollOffset = Math.max(0, Math.min(maxDetailScroll(), mDetailScrollOffset));
        mDetailRegion = new Rect(aDetailLeft, aRegionTop, aTextWidth, aViewHeight);

        Rect aSavedClip = g.getClipRect();
        g.clipRect(aDetailLeft, aRegionTop, aTextWidth, aViewHeight);
        mLinkRects.clear();
        mLinkUrls.clear();
        g.setFont(aSmall);
        for (int i = 0; i < aRows.size(); i++) {
            DetailRow aRow = aRows.get(i);
            int aRowBaseline = aRegionTop + aSmall.getAscent() + i * 20 - mDetailScrollOffset;
            if (aRowBaseline - aSmall.getAscent() >= aRegionBottom || aRowBaseline < aRegionTop) {
                continue; // 行整体在可视区外
            }
            switch (aRow.colorKind()) {
                case ROW_COLOR_HEADER -> g.setColor(new Color(255, 220, 80));
                case ROW_COLOR_LINK -> g.setColor(new Color(96, 160, 255));
                default -> g.setColor(new Color(176, 176, 190));
            }
            int aX = aDetailLeft + aRow.indent();
            g.drawString(aRow.text(), aX, aRowBaseline);
            if (aRow.url() != null) {
                // 名字超链接：下划线贴基线下方（drawString 的 y 本身是基线），点击区盖住字形
                int aNameW = aSmall.stringWidth(aRow.text());
                g.drawLine(aX, aRowBaseline + 2, aX + aNameW, aRowBaseline + 2);
                mLinkRects.add(new Rect(aX, aRowBaseline - aSmall.getAscent(), aNameW + 4,
                        aSmall.getAscent() + 5));
                mLinkUrls.add(aRow.url());
            }
        }
        g.setClipRect(aSavedClip);

        // 滚动条轨道 + thumb（画在裁剪外，贴可视区右缘）
        if (maxDetailScroll() > 0) {
            int aTrackX = aDetailLeft + aTextWidth - DETAIL_SCROLLBAR_WIDTH;
            g.setColor(new Color(0, 0, 0, 90));
            g.fillRect(aTrackX, aRegionTop, DETAIL_SCROLLBAR_WIDTH, aViewHeight);
            g.setColor(new Color(200, 196, 210, 200));
            g.fillRect(aTrackX + 1, detailThumbY(), DETAIL_SCROLLBAR_WIDTH - 2, detailThumbHeight());
        }
    }

    /** 详情下区行颜色角色。 */
    private static final int ROW_COLOR_HEADER = 0;
    private static final int ROW_COLOR_TEXT = 1;
    private static final int ROW_COLOR_LINK = 2;

    /** 详情下区的一行：颜色角色、文本、缩进、链接 URL（null = 纯文本）。 */
    private record DetailRow(int colorKind, String text, int indent, String url) {
    }

    /** 链接起到末尾的行列表（链接 / 许可证 / 模组贡献者）。 */
    private List<DetailRow> buildDetailRows(ModEntry theEntry) {
        List<DetailRow> aRows = new ArrayList<>();
        if (!theEntry.links.isEmpty()) {
            aRows.add(new DetailRow(ROW_COLOR_HEADER, "链接：", 0, null));
            for (ModEntry.Link aLink : theEntry.links) {
                aRows.add(new DetailRow(ROW_COLOR_LINK, aLink.name(), 16, aLink.url()));
            }
        }
        if (!theEntry.license.isEmpty()) {
            aRows.add(new DetailRow(ROW_COLOR_TEXT, "许可证：" + theEntry.license, 0, null));
        }
        if (!theEntry.authors.isEmpty() || !theEntry.contributors.isEmpty()) {
            aRows.add(new DetailRow(ROW_COLOR_TEXT, "模组贡献者：", 0, null));
            List<String[]> aGroups = new ArrayList<>();
            if (!theEntry.authors.isEmpty()) {
                aGroups.add(new String[] {"作者", theEntry.authors});
            }
            if (!theEntry.contributors.isEmpty()) {
                aGroups.add(new String[] {"贡献者", theEntry.contributors});
            }
            for (String[] aGroup : aGroups) {
                aRows.add(new DetailRow(ROW_COLOR_TEXT, aGroup[0] + "：", 16, null));
                for (String aPerson : aGroup[1].split(", ")) {
                    aRows.add(new DetailRow(ROW_COLOR_TEXT, aPerson, 32, null));
                }
            }
        }
        return aRows;
    }

    /** 详情下区滚动：内容总高 - 可视高。 */
    private int maxDetailScroll() {
        return Math.max(0, mDetailContentHeight - mDetailViewHeight);
    }

    /** 详情滚动条 thumb 的高与 y（几何同 ModListWidget）。 */
    private int detailThumbHeight() {
        int aTrack = mDetailViewHeight - 2;
        if (mDetailContentHeight <= 0) {
            return aTrack;
        }
        return Math.max(24, aTrack * mDetailViewHeight / mDetailContentHeight);
    }

    private int detailThumbY() {
        int aMax = maxDetailScroll();
        if (aMax == 0) {
            return mDetailRegion.mY + 1;
        }
        int aTrack = mDetailViewHeight - 2 - detailThumbHeight();
        return mDetailRegion.mY + 1 + aTrack * mDetailScrollOffset / aMax;
    }

    /** 详情下区滚动一格（滚轮；正值向上）。 */
    void scrollDetail(int theDelta) {
        mDetailScrollOffset = Math.max(0, Math.min(maxDetailScroll(), mDetailScrollOffset - theDelta * 20));
        markDirty();
    }

    /**
     * 三枚正方形石钮横排（边长 = 石钮贴图原生高，用 drawImageBox 把三段贴图拉伸到任意
     * 正方形，绕开三段拼接的固定宽度），钮面居中贴模组图标、无文字。可点击区域与
     * 悬停高亮在对话框坐标自理；website/issues 未设置画深色罩（不可点），配置未声明
     * 入口点不画。
     */
    private void drawIconButtons(Graphics g, ModEntry theEntry, int theX, int theY, int thePanelWidth) {
        mButtonRects.clear();
        mButtonIds.clear();
        int[] aIds = {ModMenuMod.BUTTON_WEBSITE, ModMenuMod.BUTTON_ISSUES, ModMenuMod.BUTTON_CONFIG};
        boolean[] aUsable = {theEntry.website != null, theEntry.issues != null, theEntry.hasConfig};
        int aSize = stoneArtHeight();
        int aRowWidth = aSize * 3 + 12;
        int aStartX = theX + Math.max(0, (Math.min(thePanelWidth, aRowWidth) - aRowWidth) / 2);

        for (int i = 0; i < 3; i++) {
            int aBx = aStartX + (aSize + 6) * i;
            if (i == 2 && !theEntry.hasConfig) {
                continue; // 配置未声明入口点：整枚不画（不画石钮、图标，也不留点击区）
            }
            Rect aRect = new Rect(aBx, theY, aSize, aSize);
            drawSquareStoneButton(g, aBx, theY, aSize);
            if (mButtonArt[i] != null) {
                int aSrcW = mButtonArt[i].getWidth();
                int aSrcH = mButtonArt[i].getHeight();
                int aFit = Math.min((aSize - 14) * 256 / aSrcW, (aSize - 14) * 256 / aSrcH);
                int aW = Math.max(1, aSrcW * aFit / 256);
                int aH = Math.max(1, aSrcH * aFit / 256);
                g.drawImage(mButtonArt[i],
                        new Rect(aBx + (aSize - aW) / 2, theY + (aSize - aH) / 2, aW, aH),
                        new Rect(0, 0, aSrcW, aSrcH));
            }
            if (!aUsable[i]) {
                g.setColor(new Color(30, 30, 30, 150)); // 置灰罩（官网/问题未设置）
                g.fillRect(aBx, theY, aSize, aSize);
            } else if (mHoverButton == i) {
                g.setColor(new Color(255, 220, 80, 200));
                g.drawRect(aBx - 1, theY - 1, aSize + 2, aSize + 2);
            }
            mButtonRects.add(aRect);
            mButtonIds.add(aIds[i]);
        }
    }

    /** 正方形石钮背景：预拼石钮条整体缩放（完整圆角与高光随比例缩放，无裁切）。 */
    private void drawSquareStoneButton(Graphics g, int theX, int theY, int theSize) {
        Image aStrip = buildStoneStrip();
        if (aStrip == null) {
            g.setColor(new Color(90, 88, 100));
            g.fillRect(theX, theY, theSize, theSize);
            g.setColor(new Color(160, 158, 170));
            g.drawRect(theX, theY, theSize, theSize);
            return;
        }
        // 贴图底部含阴影带：目标高多加阴影的像素，钮面正好填满正方形，阴影悬在方块外
        g.drawImage(aStrip, new Rect(theX, theY, theSize, theSize + stoneStripShadow()),
                new Rect(0, 0, aStrip.getWidth(), aStrip.getHeight()));
    }

    /** 石钮条底部阴影的像素数（实测：从底向上数不含完全不透明像素的行）；测不出回 0。 */
    private int stoneStripShadow() {
        if (mStripShadow != null) {
            return mStripShadow;
        }
        mStripShadow = 0;
        if (buildStoneStrip() instanceof cn.enaium.fabricpvz.portable.sexy.graphics.MemoryImage anImage) {
            int[] aBits = anImage.getBits();
            int aW = anImage.getWidth();
            for (int aRow = anImage.getHeight() - 1; aRow >= 0; aRow--) {
                boolean aFacePixel = false;
                for (int aX = 0; aX < aW; aX++) {
                    if ((aBits[aRow * aW + aX] >>> 24) == 255) {
                        aFacePixel = true;
                        break;
                    }
                }
                if (aFacePixel) {
                    break;
                }
                mStripShadow++;
            }
        }
        return mStripShadow;
    }

    @Override
    public void mouseMove(int theX, int theY) {
        int aPrev = mHoverButton;
        mHoverButton = -1;
        for (int i = 0; i < mButtonRects.size(); i++) {
            if (mButtonRects.get(i).contains(theX, theY)) {
                mHoverButton = i;
                break;
            }
        }
        if (aPrev != mHoverButton) {
            markDirty();
        }
    }

    @Override
    public void mouseDown(int theX, int theY, int theBtnNum, int theClickCount) {
        // 图标按钮点击
        for (int i = 0; i < mButtonRects.size(); i++) {
            if (mButtonRects.get(i).contains(theX, theY)) {
                handleButton(mButtonIds.get(i));
                return;
            }
        }
        // 详情滚动条：进入拖动（点 thumb 抓取，点轨道 thumb 跳到点击处附近）
        int aTrackX = mDetailRegion.mX + mDetailRegion.mWidth - DETAIL_SCROLLBAR_WIDTH;
        if (theX >= aTrackX && theY >= mDetailRegion.mY
                && theY < mDetailRegion.mY + mDetailRegion.mHeight && maxDetailScroll() > 0) {
            mDetailDraggingThumb = true;
            int aThumbTop = detailThumbY();
            mDetailThumbGrabOffset = (theY >= aThumbTop && theY < aThumbTop + detailThumbHeight())
                    ? theY - aThumbTop : detailThumbHeight() / 2;
            dragDetailThumb(theY);
            return;
        }
        // 链接条目点击 → 打开链接确认框
        for (int i = 0; i < mLinkRects.size(); i++) {
            if (mLinkRects.get(i).contains(theX, theY)) {
                openUrl(mLinkUrls.get(i));
                return;
            }
        }
        super.mouseDown(theX, theY, theBtnNum, theClickCount);
    }

    @Override
    public void mouseDrag(int theX, int theY) {
        if (mDetailDraggingThumb) {
            dragDetailThumb(theY);
            return;
        }
        super.mouseDrag(theX, theY);
    }

    @Override
    public void mouseUp(int theX, int theY, int theClickCount) {
        mDetailDraggingThumb = false;
        super.mouseUp(theX, theY, theClickCount);
    }

    private void dragDetailThumb(int theMouseY) {
        int aTrack = mDetailViewHeight - 2 - detailThumbHeight();
        if (aTrack <= 0) {
            return;
        }
        int aThumbY = theMouseY - mDetailThumbGrabOffset - (mDetailRegion.mY + 1);
        mDetailScrollOffset = Math.max(0, Math.min(maxDetailScroll(),
                aThumbY * maxDetailScroll() / aTrack));
        markDirty();
    }

    private void handleButton(int theId) {
        ModEntry aEntry = mEntries.get(mSelectedIndex);
        if (theId == ModMenuMod.BUTTON_WEBSITE && aEntry.website != null) {
            openUrl(aEntry.website);
        } else if (theId == ModMenuMod.BUTTON_ISSUES && aEntry.issues != null) {
            openUrl(aEntry.issues);
        } else if (theId == ModMenuMod.BUTTON_CONFIG && aEntry.hasConfig) {
            openConfig(aEntry.modId);
        }
    }

    /** Dialog 的按钮派发：返回(1000) 关闭。图标按钮的点击在 mouseDown 自理。 */
    @Override
    public void buttonDepress(int theId) {
        if (theId == Dialog.ID_FOOTER) {
            mApp.killDialog(mId);
            return;
        }
        super.buttonDepress(theId);
    }

    /** ESC 返回（焦点在列表时经列表转发到这里）。 */
    @Override
    public void keyDown(int theKey) {
        if (theKey == cn.enaium.fabricpvz.portable.sexy.KeyCode.KEYCODE_ESCAPE) {
            buttonDepress(Dialog.ID_FOOTER);
            return;
        }
        super.keyDown(theKey);
    }

    private void openUrl(String theUrl) {
        mApp.addDialog(ModMenuMod.DIALOG_OPEN_URL, new OpenUrlDialog(mApp, theUrl));
    }

    private void openConfig(String theModId) {
        for (var anEntry : net.fabricmc.loader.api.FabricLoader.getInstance()
                .getEntrypointContainers("modmenu", ModMenuApi.class)) {
            if (anEntry.getProvider().getMetadata().getId().equals(theModId)) {
                try {
                    anEntry.getEntrypoint().openConfig();
                } catch (RuntimeException e) {
                    org.slf4j.LoggerFactory.getLogger("pvz-modmenu")
                            .warn("mod {} 的配置入口点抛出异常", theModId, e);
                }
                return;
            }
        }
    }

    /** 逐字换行（中文友好），返回行列表。 */
    static List<String> wrap(String theText, Font theFont, int theMaxWidth) {
        List<String> aLines = new ArrayList<>();
        for (String aPara : theText.split("\n")) {
            StringBuilder aCur = new StringBuilder();
            for (int i = 0; i < aPara.length(); i++) {
                char aChar = aPara.charAt(i);
                if (!aCur.isEmpty() && theFont.stringWidth(aCur.toString() + aChar) > theMaxWidth) {
                    aLines.add(aCur.toString());
                    aCur.setLength(0);
                }
                aCur.append(aChar);
            }
            aLines.add(aCur.toString());
        }
        return aLines;
    }

}
