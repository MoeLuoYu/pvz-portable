package cn.enaium.fabricpvz.trainermod.mixin;

import cn.enaium.fabricpvz.portable.lawn.GameMode;
import cn.enaium.fabricpvz.portable.lawn.Projectile;
import cn.enaium.fabricpvz.trainermod.TrainerMod;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 特效页"牛顿的力量"：抛射物的两处高重力判定——updateLobMotion 的额外下压加速度
 * 与 updateNormalMotion 的 FLOAT_OVER 抛物线——原本只认牛顿的力量（High Gravity）
 * 模式；OR 入开关后任意远程攻击走真实物理抛物线。
 */
@Mixin(Projectile.class)
public class TrainerProjectileMixin {

    @ModifyExpressionValue(method = "updateLobMotion()V",
            at = @At(value = "FIELD", target = "Lcn/enaium/fabricpvz/portable/lawn/LawnApp;mGameMode:I"))
    private int trainermod$lobGravity(int theOriginal) {
        return TrainerMod.newtonGravityEnabled()
                ? GameMode.GAMEMODE_CHALLENGE_HIGH_GRAVITY : theOriginal;
    }

    @ModifyExpressionValue(method = "updateNormalMotion()V",
            at = @At(value = "FIELD", target = "Lcn/enaium/fabricpvz/portable/lawn/LawnApp;mGameMode:I"))
    private int trainermod$normalGravity(int theOriginal) {
        return TrainerMod.newtonGravityEnabled()
                ? GameMode.GAMEMODE_CHALLENGE_HIGH_GRAVITY : theOriginal;
    }
}
