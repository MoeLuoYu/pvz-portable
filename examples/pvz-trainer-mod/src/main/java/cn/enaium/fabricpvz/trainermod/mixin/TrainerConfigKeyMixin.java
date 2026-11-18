package cn.enaium.fabricpvz.trainermod.mixin;

import cn.enaium.fabricpvz.portable.lawn.widget.GameSelector;
import cn.enaium.fabricpvz.trainermod.TrainerMod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 主菜单的修改器配置组合键：GameSelector.keyDown（主菜单的按键入口，unlockall 等
 * 隐码同一条路径）尾部转发给修改器——X+C 同时按住呼出配置对话框。GameSelector 只
 * 存在于主菜单，所以组合键天然只在主菜单生效。
 */
@Mixin(GameSelector.class)
public class TrainerConfigKeyMixin {

    @Inject(method = "keyDown(I)V", at = @At("TAIL"))
    private void trainermod$onKeyDown(int theKey, CallbackInfo ci) {
        TrainerMod.onGameSelectorKey(theKey);
    }
}
