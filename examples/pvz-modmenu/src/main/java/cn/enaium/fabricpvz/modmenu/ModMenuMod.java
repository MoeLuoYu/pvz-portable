package cn.enaium.fabricpvz.modmenu;

import cn.enaium.fabricpvz.portable.lawn.LawnApp;
import cn.enaium.fabricpvz.portable.lawn.SoundType;
import cn.enaium.fabricpvz.portable.sexy.ButtonListener;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PvZ ModMenu：游戏内的模组管理界面。
 *
 * <p>入口按钮由 {@code ModMenuButtonMixin} 注入主菜单"游戏选项"对话框
 * （原图鉴按钮槽位，仅主菜单变体），点击后打开 {@link ModMenuDialog}：
 * 左侧模组列表、右侧选中模组的详情。 {@link OpenUrlDialog} 是打开链接前的
 * 三按钮确认框。
 */
public final class ModMenuMod implements ModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("pvz-modmenu");

    /** 模组管理对话框 id（游戏 Dialogs 常量最高 5x，修改器模组占 900）。 */
    public static final int DIALOG_MODMENU = 920;
    /** 打开链接确认框 id。 */
    public static final int DIALOG_OPEN_URL = 921;
    /** 选项对话框里入口按钮的 id（游戏 NewOptionsDialog_* 用 0-10）。 */
    public static final int BUTTON_OPEN = 8100;
    /** 详情面板图标按钮（自绘命中，不经过 ButtonListener 派发，仅作标识用）。 */
    public static final int BUTTON_WEBSITE = 2101;
    public static final int BUTTON_ISSUES = 2102;
    public static final int BUTTON_CONFIG = 2103;

    /** 入口按钮的监听器：press 播原生按键音，depress 打开模组管理界面。 */
    public static final ButtonListener ENTRY_BUTTON_LISTENER = new ButtonListener() {
        @Override
        public void buttonPress(int theId) {
            LawnApp aApp = LawnApp.gLawnApp;
            if (aApp != null) {
                aApp.playSample(SoundType.SOUND_GRAVEBUTTON);
            }
        }

        @Override
        public void buttonDepress(int theId) {
            openModMenu(LawnApp.gLawnApp);
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

    @Override
    public void onInitialize() {
        LOGGER.info("pvz-modmenu 已加载：主菜单游戏选项内打开模组管理界面");
    }

    /** 打开模组管理界面（已在栈中则忽略）；选项对话框保持打开，返回时还在。 */
    public static void openModMenu(LawnApp theApp) {
        if (theApp == null || theApp.getDialog(DIALOG_MODMENU) != null) {
            return;
        }
        ModMenuDialog aDialog = new ModMenuDialog(theApp);
        theApp.centerDialog(aDialog, aDialog.mWidth, aDialog.mHeight);
        theApp.addDialog(DIALOG_MODMENU, aDialog);
    }
}
