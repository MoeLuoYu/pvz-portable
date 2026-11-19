package cn.enaium.fabricpvz.trainermod.mixin;

import cn.enaium.fabricpvz.portable.lawn.GameMode;
import cn.enaium.fabricpvz.portable.lawn.Zombie;
import cn.enaium.fabricpvz.trainermod.TrainerMod;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 僵尸页两个判定类开关（Zombie 上的注入）：
 * <ul>
 * <li>"僵尸不动"：isImmobilizied 是定身的统一判定（动画速率归零、被大嘴花视为
 *     无_miss 目标等）——OR 入开关，比 mVelX 可靠（普通行走由动画驱动）；</li>
 * <li>"隐形切换"：隐形战争（Invisighoul）关的僵尸隐形由 draw 里的模式判定实现
 *     （跳过 reanim 绘制）——开关开启时把 mGameMode 读取改写成普通模式，判定为
 *     真 → 现形；关闭恢复游戏自身行为（opcode 限定 GETFIELD）。</li>
 * </ul>
 */
@Mixin(Zombie.class)
public class TrainerZombieMixin {

    @Redirect(method = "zombieInitialize(IIZLcn/enaium/fabricpvz/portable/lawn/Zombie;I)V",
            at = @At(value = "INVOKE",
                    target = "Lcn/enaium/fabricpvz/portable/lawn/LawnApp;isLittleTroubleLevel()Z"))
    private boolean trainermod$miniZombie(cn.enaium.fabricpvz.portable.lawn.LawnApp theApp) {
        return theApp.isLittleTroubleLevel() || TrainerMod.miniZombieEnabled();
    }

    @Inject(method = "isImmobilizied()Z", at = @At("HEAD"), cancellable = true)
    private void trainermod$frozen(CallbackInfoReturnable<Boolean> cir) {
        // 只定身活僵尸（死亡/濒死的不能挡，否则尸体动画与移除链被卡住，尸体会一直
        // 站着）；已迷惑的（自己人）不冻结
        if (TrainerMod.zombieFrozenEnabled()
                && !((Zombie) (Object) this).isDeadOrDying()
                && !((Zombie) (Object) this).mMindControlled) {
            cir.setReturnValue(true);
        }
    }

    @ModifyExpressionValue(method = "draw(Lcn/enaium/fabricpvz/portable/sexy/graphics/Graphics;)V",
            at = @At(value = "FIELD", target = "Lcn/enaium/fabricpvz/portable/lawn/LawnApp;mGameMode:I",
                    opcode = Opcodes.GETFIELD))
    private int trainermod$invisiShow(int theOriginal) {
        // "僵尸现形"：仅隐形战争关有意义——把模式判定改写成普通模式，判定为真
        // → 僵尸照常绘制（现形）；取消恢复游戏自身行为
        return TrainerMod.zombieShowEnabled()
                ? GameMode.GAMEMODE_ADVENTURE : theOriginal;
    }
}
