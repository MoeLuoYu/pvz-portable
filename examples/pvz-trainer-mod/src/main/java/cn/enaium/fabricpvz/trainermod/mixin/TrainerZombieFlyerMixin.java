package cn.enaium.fabricpvz.trainermod.mixin;

import cn.enaium.fabricpvz.portable.lawn.GameMode;
import cn.enaium.fabricpvz.portable.lawn.ZombieBehaviourA;
import cn.enaium.fabricpvz.trainermod.TrainerMod;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 特效页"牛顿的力量"：飞行僵尸（气球等）在高重力下的下坠判定（ZombieBehaviourA.
 * updateZombieFlyer）——OR 入开关后空中单位同样遵循重力。
 */
@Mixin(ZombieBehaviourA.class)
public class TrainerZombieFlyerMixin {

    @ModifyExpressionValue(method = "updateZombieFlyer(Lcn/enaium/fabricpvz/portable/lawn/Zombie;)V",
            at = @At(value = "FIELD", target = "Lcn/enaium/fabricpvz/portable/lawn/LawnApp;mGameMode:I"))
    private static int trainermod$flyerGravity(int theOriginal) {
        return TrainerMod.newtonGravityEnabled()
                ? GameMode.GAMEMODE_CHALLENGE_HIGH_GRAVITY : theOriginal;
    }
}
