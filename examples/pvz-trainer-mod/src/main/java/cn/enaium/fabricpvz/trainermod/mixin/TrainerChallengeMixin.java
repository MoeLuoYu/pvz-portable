package cn.enaium.fabricpvz.trainermod.mixin;

import cn.enaium.fabricpvz.portable.lawn.Challenge;
import cn.enaium.fabricpvz.portable.lawn.GameMode;
import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;
import cn.enaium.fabricpvz.trainermod.TrainerMod;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 特效页三特性的 Challenge 侧注入：
 * <ul>
 * <li>"坟墓危险"：spawnZombieWave 的每波墓碑生成判定（旗波顶出墓碑僵尸、第 5 波后
 *     每波随机新墓碑）——OR 入开关；</li>
 * <li>"打僵尸"：mouseDown 的锤击命中分发（空地挥锤打中僵尸）——OR 入开关；</li>
 * <li>"暴风雨之夜/种子雨"：drawWeather 的雨与闪电绘制——开关开启时改走
 *     drawRain/drawStormNight（真实模式不受影响，照常走原判定）。</li>
 * </ul>
 */
@Mixin(Challenge.class)
public class TrainerChallengeMixin {

    @ModifyExpressionValue(method = "spawnZombieWave()V",
            at = @At(value = "FIELD", target = "Lcn/enaium/fabricpvz/portable/lawn/LawnApp;mGameMode:I"))
    private int trainermod$gravePerWave(int theOriginal) {
        return TrainerMod.graveDangerEnabled()
                ? GameMode.GAMEMODE_CHALLENGE_GRAVE_DANGER : theOriginal;
    }

    @Redirect(method = "mouseDown(IIILcn/enaium/fabricpvz/portable/lawn/HitResult;)I",
            at = @At(value = "INVOKE",
                    target = "Lcn/enaium/fabricpvz/portable/lawn/LawnApp;isWhackAZombieLevel()Z",
                    ordinal = 0))
    private boolean trainermod$whackClick(cn.enaium.fabricpvz.portable.lawn.LawnApp theApp) {
        return theApp.isWhackAZombieLevel() || TrainerMod.whackHammerEnabled();
    }

    @Inject(method = "whackAZombieSpawning()V",
            at = @At("HEAD"), cancellable = true)
    private void trainermod$pauseWhackSpawning(CallbackInfo ci) {
        // "暂停出怪"对打僵尸关同样生效：该关出怪不走普通倒计时主线，而是
        // whackAZombieSpawning 自己的节奏（递减倒计时→补墓碑→从墓碑顶出僵尸）——
        // 整体冻结，不补墓碑也不出怪
        if (TrainerMod.pauseSpawningEnabled()) {
            ci.cancel();
        }
    }

    @Inject(method = "drawWeather(Lcn/enaium/fabricpvz/portable/sexy/graphics/Graphics;)V",
            at = @At("HEAD"), cancellable = true)
    private void trainermod$drawWeather(Graphics g, CallbackInfo ci) {
        boolean aRain = TrainerMod.seedRainEnabled();
        boolean aStorm = TrainerMod.stormyNightEnabled();
        if (!aRain && !aStorm) {
            return; // 真实模式照常走原判定
        }
        Challenge aSelf = (Challenge) (Object) this;
        aSelf.drawRain(g);
        if (aStorm) {
            aSelf.drawStormNight(g);
        }
        ci.cancel();
    }
}
