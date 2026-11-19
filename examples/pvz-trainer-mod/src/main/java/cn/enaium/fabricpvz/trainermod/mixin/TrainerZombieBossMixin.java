package cn.enaium.fabricpvz.trainermod.mixin;

import cn.enaium.fabricpvz.portable.lawn.Zombie;
import cn.enaium.fabricpvz.trainermod.TrainerMod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * "暂停出怪"对僵王关生效：僵王不出主线倒计时（Challenge.updateZombieSpawning
 * 对 Final Boss 返回 true），出怪全在其行为状态机（updateBoss：空投飞贼/召雪橇/
 * 扔车/火球）里——开启时整个行为更新冻结，僵王停住不动不召怪，关闭从冻结点继续。
 */
@Mixin(cn.enaium.fabricpvz.portable.lawn.ZombieBoss.class)
public class TrainerZombieBossMixin {

    @Inject(method = "updateBoss(Lcn/enaium/fabricpvz/portable/lawn/Zombie;)V",
            at = @At("HEAD"), cancellable = true)
    private static void trainermod$pauseBoss(Zombie theZombie, CallbackInfo ci) {
        if (TrainerMod.pauseSpawningEnabled()) {
            ci.cancel();
        }
    }
}
