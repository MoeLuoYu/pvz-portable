package cn.enaium.fabricpvz.trainermod.mixin;

import cn.enaium.fabricpvz.portable.lawn.CursorPreview;
import cn.enaium.fabricpvz.portable.lawn.GameMode;
import cn.enaium.fabricpvz.trainermod.TrainerMod;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 特效页"排山倒海"：CursorPreview.draw 的整列预览虚影判定——OR 入开关后普通
 * 关卡种树光标同样画出整列落点预览。
 */
@Mixin(CursorPreview.class)
public class TrainerCursorPreviewMixin {

    @ModifyExpressionValue(method = "draw(Lcn/enaium/fabricpvz/portable/sexy/graphics/Graphics;)V",
            at = @At(value = "FIELD", target = "Lcn/enaium/fabricpvz/portable/lawn/LawnApp;mGameMode:I"))
    private int trainermod$columnPreview(int theOriginal) {
        return TrainerMod.columnPlantEnabled()
                ? GameMode.GAMEMODE_CHALLENGE_COLUMN : theOriginal;
    }
}
