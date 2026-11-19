package cn.enaium.fabricpvz.trainermod.mixin;

import cn.enaium.fabricpvz.portable.lawn.Board;
import cn.enaium.fabricpvz.trainermod.TrainerMod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 场地页"去除迷雾"：迷雾的唯一绘制点是 BoardRender.drawFog（渲染项分发
 * RENDER_ITEM_FOG 直调的静态方法；Board.drawFog 只是没人调用的委托），取消
 * 绘制最干净——雾格 alpha 每帧由 updateFog 重铺，从数据侧清零会被下一帧覆盖；
 * Plantern/火炬树的清雾逻辑仍在数据侧照常运行，恢复开关后迷雾原样回来。
 * BoardRender 是包私有类，目标只能用 targets 字符串指定；静态目标的 handler
 * 也必须是 static。
 */
@Mixin(targets = "cn.enaium.fabricpvz.portable.lawn.BoardRender")
public class TrainerBoardMixin {

    @Inject(method = "drawFog(Lcn/enaium/fabricpvz/portable/lawn/Board;"
            + "Lcn/enaium/fabricpvz/portable/sexy/graphics/Graphics;)V",
            at = @At("HEAD"), cancellable = true)
    private static void trainermod$skipFog(cn.enaium.fabricpvz.portable.lawn.Board theBoard,
            cn.enaium.fabricpvz.portable.sexy.graphics.Graphics theG, CallbackInfo ci) {
        if (TrainerMod.removeFogEnabled()) {
            ci.cancel();
        }
    }
}
