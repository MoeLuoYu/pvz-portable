package cn.enaium.fabricpvz.trainermod.mixin;

import cn.enaium.fabricpvz.trainermod.TrainerMod;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 僵尸页"巨人可被吃"：大嘴花的吞咽判定——巨人/红眼巨人/僵王走 doBite（只咬 40 伤）
 * ——修改该判定的 mZombieType 读取（opcode 限定 GETFIELD），开关开启时
 * 返回普通僵尸类型，巨人同普通僵尸一样被一口吞掉。
 */
@Mixin(cn.enaium.fabricpvz.portable.lawn.Plant.class)
public class TrainerChomperMixin {

    @ModifyExpressionValue(method = "updateChomper()V",
            at = @At(value = "FIELD", target = "Lcn/enaium/fabricpvz/portable/lawn/Zombie;mZombieType:I",
                    opcode = Opcodes.GETFIELD))
    private int trainermod$gargEdible(int theOriginal) {
        return TrainerMod.zombieGargEdibleEnabled()
                ? cn.enaium.fabricpvz.portable.lawn.ZombieType.ZOMBIE_NORMAL : theOriginal;
    }
}
