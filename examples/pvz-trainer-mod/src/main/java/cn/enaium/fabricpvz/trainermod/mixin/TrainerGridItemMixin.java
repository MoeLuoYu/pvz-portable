package cn.enaium.fabricpvz.trainermod.mixin;

import cn.enaium.fabricpvz.portable.lawn.GridItem;
import cn.enaium.fabricpvz.trainermod.TrainerMod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 场地页"花瓶透视"：借用游戏自带的揭示渲染——updateScaryPot 尾部把
 * mTransparentCounter 顶到 50（瓶身 alpha=58、内容完整可见），与 Plantern/
 * Shift 隐码的透视画面完全一致；每帧重设，压过游戏自己的递增/递减。
 */
@Mixin(GridItem.class)
public class TrainerGridItemMixin {

    @Inject(method = "updateScaryPot()V", at = @At("TAIL"))
    private void trainermod$xrayVase(CallbackInfo ci) {
        if (TrainerMod.vaseXrayEnabled()) {
            ((GridItem) (Object) this).mTransparentCounter = 50;
        }
    }
}
