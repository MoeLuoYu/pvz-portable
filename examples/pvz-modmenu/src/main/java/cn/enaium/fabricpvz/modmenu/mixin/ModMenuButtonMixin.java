package cn.enaium.fabricpvz.modmenu.mixin;

import cn.enaium.fabricpvz.modmenu.ModMenuMod;
import cn.enaium.fabricpvz.portable.lawn.LawnApp;
import cn.enaium.fabricpvz.portable.lawn.widget.GameButton;
import cn.enaium.fabricpvz.portable.lawn.widget.LawnStoneButton;
import cn.enaium.fabricpvz.portable.lawn.widget.NewOptionsDialog;
import cn.enaium.fabricpvz.portable.sexy.WidgetContainer;
import cn.enaium.fabricpvz.portable.sexy.WidgetManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 往主菜单"游戏选项"对话框（NewOptionsDialog 的选择器变体）注入 ModMenu 入口按钮，
 * 位置用原"查看图鉴"按钮的槽位（ALMANAC_BUTTON_*）。
 *
 * <p>四处注入：构造器尾部创建（仅选择器变体）、addedToManager 尾部挂入控件树、
 * removedFromManager 尾部摘除、resize 尾部摆到图鉴槽位。按钮监听器是
 * {@link ModMenuMod#ENTRY_BUTTON_LISTENER}（不走对话框的 buttonDepress，无需再注入）。
 */
@Mixin(NewOptionsDialog.class)
public class ModMenuButtonMixin {

    @Shadow(remap = false)
    public boolean mFromGameSelector;

    @Unique
    private LawnStoneButton modmenu$button;

    @Inject(method = "<init>(Lcn/enaium/fabricpvz/portable/lawn/LawnApp;Z)V", at = @At("TAIL"))
    private void modmenu$createButton(LawnApp theApp, boolean theFromGameSelector, CallbackInfo ci) {
        if (!theFromGameSelector) {
            return; // 只在主菜单的游戏选项里出现
        }
        modmenu$button = GameButton.makeButton(ModMenuMod.BUTTON_OPEN,
                ModMenuMod.ENTRY_BUTTON_LISTENER,
                "模 组(" + cn.enaium.fabricpvz.portable.pvzp.RuntimeEnvironment.totalMods() + ")");
        modmenu$button.mHasAlpha = true;
        modmenu$button.mHasTransparencies = true;
    }

    @Inject(method = "addedToManager(Lcn/enaium/fabricpvz/portable/sexy/WidgetManager;)V", at = @At("TAIL"))
    private void modmenu$addWidget(WidgetManager theWidgetManager, CallbackInfo ci) {
        if (modmenu$button != null) {
            ((WidgetContainer) (Object) this).addWidget(modmenu$button);
        }
    }

    @Inject(method = "removedFromManager(Lcn/enaium/fabricpvz/portable/sexy/WidgetManager;)V", at = @At("TAIL"))
    private void modmenu$removeWidget(WidgetManager theWidgetManager, CallbackInfo ci) {
        if (modmenu$button != null) {
            ((WidgetContainer) (Object) this).removeWidget(modmenu$button);
        }
    }

    @Inject(method = "resize(IIII)V", at = @At("TAIL"))
    private void modmenu$placeButton(int theX, int theY, int theWidth, int theHeight, CallbackInfo ci) {
        if (modmenu$button != null) {
            modmenu$button.resize(NewOptionsDialog.ALMANAC_BUTTON_X, NewOptionsDialog.ALMANAC_BUTTON_Y,
                    NewOptionsDialog.OPTIONS_BUTTON_WIDTH, NewOptionsDialog.OPTIONS_BUTTON_HEIGHT);
        }
    }
}
