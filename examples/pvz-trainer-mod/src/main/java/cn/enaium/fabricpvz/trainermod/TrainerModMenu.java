package cn.enaium.fabricpvz.trainermod;

import cn.enaium.fabricpvz.modmenu.api.ModMenuApi;

/**
 * ModMenu 的配置入口点：ModMenu 模组详情面板的"配 置"按钮 → 修改器配置对话框
 * （与主菜单 X+C 组合键同一入口）。声明在本模组 fabric.mod.json 的
 * {@code entrypoints.modmenu} 里。
 */
public final class TrainerModMenu implements ModMenuApi {

    @Override
    public void openConfig() {
        TrainerMod.openConfigDialog();
    }
}
