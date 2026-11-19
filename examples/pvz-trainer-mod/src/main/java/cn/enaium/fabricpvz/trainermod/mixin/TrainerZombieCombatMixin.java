package cn.enaium.fabricpvz.trainermod.mixin;

import cn.enaium.fabricpvz.trainermod.TrainerMod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 僵尸页"僵尸无敌"：ZombieCombat.takeDamage 是全部伤害的唯一入口（含护盾/头盔/
 * 本体分派）——HEAD 取消后僵尸免疫一切伤害；死亡动画等非伤害路径不受影响。
 * ZombieCombat 是 public final 类但方法为 static，handler 亦 static。
 */
@Mixin(cn.enaium.fabricpvz.portable.lawn.ZombieCombat.class)
public class TrainerZombieCombatMixin {

    @Inject(method = "takeDamage(Lcn/enaium/fabricpvz/portable/lawn/Zombie;II)V",
            at = @At("HEAD"), cancellable = true)
    private static void trainermod$invincible(cn.enaium.fabricpvz.portable.lawn.Zombie theZombie,
            int theDamage, int theDamageFlags, CallbackInfo ci) {
        if (TrainerMod.zombieInvincibleEnabled()) {
            ci.cancel();
        }
    }
}
