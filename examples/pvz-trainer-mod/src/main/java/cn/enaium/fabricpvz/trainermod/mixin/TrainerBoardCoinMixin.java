package cn.enaium.fabricpvz.trainermod.mixin;

import cn.enaium.fabricpvz.portable.lawn.Coin;
import cn.enaium.fabricpvz.portable.lawn.CoinType;
import cn.enaium.fabricpvz.trainermod.TrainerMod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 关卡页"不掉阳光/不掉金币"：掉落源头分散（天降阳光、植物产出、僵尸掉宝、
 * 关卡奖励），统一在 Board.addCoin 的尾部过滤——被屏蔽币种的生成物立即置
 * mDead（不显示、不可拾取、槽位由对象池回收），调用方拿到的是合法引用，
 * 使用返回值的路径（关卡奖励/种子雨/礼物盒的币种不在屏蔽范围）不受影响。
 */
@Mixin(cn.enaium.fabricpvz.portable.lawn.Board.class)
public class TrainerBoardCoinMixin {

    @Inject(method = "addCoin(IIII)Lcn/enaium/fabricpvz/portable/lawn/Coin;",
            at = @At("TAIL"))
    private void trainermod$noDrops(int theX, int theY, int theCoinType, int theCoinMotion,
            CallbackInfoReturnable<Coin> cir) {
        // 禅境花园豁免：花园植物（金盏花/成株）产出的金银钻石与僵尸掉宝同运动方式，
        // 按模式区分——花园场景的产出不受"不掉阳光/不掉金币"影响
        var aApp = cn.enaium.fabricpvz.portable.lawn.LawnApp.gLawnApp;
        if (aApp != null
                && aApp.mGameMode == cn.enaium.fabricpvz.portable.lawn.GameMode.GAMEMODE_CHALLENGE_ZEN_GARDEN) {
            return;
        }
        Coin aCoin = cir.getReturnValue();
        if (aCoin == null) {
            return;
        }
        if (TrainerMod.noSunDropsEnabled()
                && (theCoinType == CoinType.COIN_SUN
                || theCoinType == CoinType.COIN_SMALLSUN
                || theCoinType == CoinType.COIN_LARGESUN)
                // "不掉阳光"排除植物自己的产出（FROM_PLANT）：只屏蔽天降
                // （FROM_SKY）与僵尸掉宝等普通运动（COIN）的阳光
                && (theCoinMotion == cn.enaium.fabricpvz.portable.lawn.CoinMotion.COIN_MOTION_FROM_SKY
                || theCoinMotion == cn.enaium.fabricpvz.portable.lawn.CoinMotion.COIN_MOTION_COIN)) {
            aCoin.mDead = true;
        } else if (TrainerMod.noCoinDropsEnabled()
                && (theCoinType == CoinType.COIN_SILVER
                || theCoinType == CoinType.COIN_GOLD
                || theCoinType == CoinType.COIN_DIAMOND)
                // 植物产币（金盏花等，FROM_PLANT 标注见 TrainerPlantCoinMixin）豁免
                && theCoinMotion != cn.enaium.fabricpvz.portable.lawn.CoinMotion.COIN_MOTION_FROM_PLANT) {
            aCoin.mDead = true;
        }
    }
}
