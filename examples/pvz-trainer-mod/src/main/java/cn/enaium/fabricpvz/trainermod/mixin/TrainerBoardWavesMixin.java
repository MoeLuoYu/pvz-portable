package cn.enaium.fabricpvz.trainermod.mixin;

import cn.enaium.fabricpvz.portable.lawn.GameMode;
import cn.enaium.fabricpvz.trainermod.TrainerMod;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 僵尸页"坟墓危险""飞贼闪电战""屏蔽三人组"（BoardWaves 是包私有类，targets
 * 字符串指定；静态目标的 handler 亦 static）：
 * <ul>
 * <li>spawnZombiesFromGraves 的墓碑顶出波次抑制判定——OR 入坟墓危险开关，波次
 *     推进时墓碑按该模式的节奏顶出僵尸；</li>
 * <li>spawnZombieWave 的飞贼空降判定——OR 入飞贼闪电战开关，每一波出怪都走
 *     BungeeDropGrid 的绳降流程；</li>
 * <li>spawnZombiesFromGraves 里泳池关的水路出怪（spawnZombiesFromPool，末波
 *     从水里冒出的潜水僵尸三人组）——Redirect 掉调用点，开关开启时不生成。</li>
 * </ul>
 */
@Mixin(targets = "cn.enaium.fabricpvz.portable.lawn.BoardWaves")
public class TrainerBoardWavesMixin {

    @ModifyExpressionValue(method = "spawnZombiesFromGraves(Lcn/enaium/fabricpvz/portable/lawn/Board;)V",
            at = @At(value = "FIELD", target = "Lcn/enaium/fabricpvz/portable/lawn/LawnApp;mGameMode:I"))
    private static int trainermod$gravePacing(int theOriginal) {
        return TrainerMod.graveDangerEnabled()
                ? GameMode.GAMEMODE_CHALLENGE_GRAVE_DANGER : theOriginal;
    }

    @Redirect(method = "spawnZombieWave(Lcn/enaium/fabricpvz/portable/lawn/Board;)V",
            at = @At(value = "INVOKE",
                    target = "Lcn/enaium/fabricpvz/portable/lawn/LawnApp;isBungeeBlitzLevel()Z"))
    private static boolean trainermod$bungeeDrop(cn.enaium.fabricpvz.portable.lawn.LawnApp theApp) {
        return theApp.isBungeeBlitzLevel() || TrainerMod.bungeeBlitzEnabled();
    }

    @Redirect(method = "spawnZombiesFromGraves(Lcn/enaium/fabricpvz/portable/lawn/Board;)V",
            at = @At(value = "INVOKE",
                    target = "Lcn/enaium/fabricpvz/portable/lawn/BoardWaves;spawnZombiesFromPool(Lcn/enaium/fabricpvz/portable/lawn/Board;)V"))
    private static void trainermod$noPoolTrio(cn.enaium.fabricpvz.portable.lawn.Board theBoard) {
        if (!TrainerMod.zombieNoDancersEnabled()) {
            theBoard.spawnZombiesFromPool(); // 未屏蔽时走原调用（Board 的公开转发）
        }
        // 屏蔽三人组开关开启时不从水路出怪
    }
}
