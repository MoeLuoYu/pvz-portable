package cn.enaium.fabricpvz.modmenu.api;

/**
 * 模组配置界面的入口点接口：模组在 fabric.mod.json 里声明
 * {@code "entrypoints": {"modmenu": ["自己的实现类"]}}，实现本接口后，
 * {@code cn.enaium.fabricpvz.modmenu} 的详情面板会出现"配置"按钮，点击时调用
 * {@link #openConfig()}。
 *
 * <p>打开配置界面的方式由实现者决定（例如再 {@code addDialog} 一个自己的模态对话框）。
 */
public interface ModMenuApi {

    /** 打开本模组的配置界面（ModMenu 已是模态栈顶，叠加打开即可）。 */
    void openConfig();
}
