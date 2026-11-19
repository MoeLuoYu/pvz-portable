package cn.enaium.fabricpvz.trainermod.mixin;

import cn.enaium.fabricpvz.trainermod.TrainerMod;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;

/**
 * 关卡页"任意种植"：包裹 canPlantAt 整个方法体（@At("RETURN") 多 return 点时
 * 中途 return 的 setReturnValue 会抛 CancellationException——见崩溃记录）——
 * 开启时除格子越界外一律放行 PLANTING_OK：无视花盆/莲叶/草皮/墓碑/弹坑/水面
 * 等地形，同一格可以叠种多个植物。
 */
@Mixin(cn.enaium.fabricpvz.portable.lawn.Board.class)
public class TrainerBoardPlantMixin {

    @WrapMethod(method = "canPlantAt(III)I")
    private int trainermod$anyTerrain(int theGridX, int theGridY, int theSeedType,
            Operation<Integer> theOriginal) {
        int aReason = theOriginal.call(theGridX, theGridY, theSeedType);
        if (TrainerMod.anyPlantingEnabled()
                && theGridX >= 0 && theGridX < cn.enaium.fabricpvz.portable.lawn.Board.MAX_GRID_SIZE_X
                && theGridY >= 0 && theGridY < cn.enaium.fabricpvz.portable.lawn.Board.MAX_GRID_SIZE_Y) {
            return cn.enaium.fabricpvz.portable.lawn.PlantingReason.PLANTING_OK;
        }
        return aReason;
    }
}
