package cn.enaium.fabricpvz.trainermod.mixin;

import cn.enaium.fabricpvz.portable.lawn.Board;
import cn.enaium.fabricpvz.portable.lawn.Coin;
import cn.enaium.fabricpvz.portable.lawn.CoinMotion;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 局内植物（金盏花等）产币的运动方式标注：updateProductionPlant 里金盏花的
 * 金/银币原版走 COIN_MOTION_COIN（与僵尸掉宝同运动，"不掉金币"无法按运动区分）
 * ——统一改标 COIN_MOTION_FROM_PLANT，配合 TrainerBoardCoinMixin 的运动豁免，
 * "不掉阳光/不掉金币"都不再误伤植物自己的产出。
 * 用 @WrapOperation 而非 @Redirect：api-plant 的 PlantOverridesMixin 对同一
 * 调用点（producedCoin 事件钩子）已有 redirect，两条 redirect 不能叠加在同一条
 * 指令上；wrap 是包裹链，与原 redirect 共存。
 */
@Mixin(cn.enaium.fabricpvz.portable.lawn.Plant.class)
public class TrainerPlantCoinMixin {

    @WrapOperation(method = "updateProductionPlant()V",
            at = @At(value = "INVOKE",
                    target = "Lcn/enaium/fabricpvz/portable/lawn/Board;addCoin(IIII)Lcn/enaium/fabricpvz/portable/lawn/Coin;"))
    private Coin trainermod$plantMotion(Board theBoard, int theX, int theY, int theCoinType,
            int theCoinMotion, Operation<Coin> theOriginal) {
        if (theCoinMotion == CoinMotion.COIN_MOTION_COIN) {
            theCoinMotion = CoinMotion.COIN_MOTION_FROM_PLANT;
        }
        return theOriginal.call(theBoard, theX, theY, theCoinType, theCoinMotion);
    }
}
