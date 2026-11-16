package cn.enaium.fabricpvz.minimalmod;

import cn.enaium.fabricpvz.api.lawn.LawnEvents;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 极简示例模组：一个入口类 + 一个事件监听，没有任何 Mixin。
 *
 * <p>做两件事：
 * <ol>
 *   <li>加载时打一条日志——验证模组确实被 Loader 装进来了；</li>
 *   <li>注册 {@link LawnEvents#SUN_COLLECTED} 监听，把玩家收取的阳光翻倍——
 *       事件 API 的最小用法，监听器的返回值就是最终生效的阳光数。</li>
 * </ol>
 *
 * <p>日志走 SLF4J：API 在编译类路径上，Log4j 实现由启动类路径供齐，无需声明任何依赖。
 */
public final class MinimalMod implements ModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("pvz-minimal-mod");

    @Override
    public void onInitialize() {
        LOGGER.info("pvz-minimal-mod 已加载：阳光收取翻倍已开启");
        LawnEvents.SUN_COLLECTED.register((board, amount, source) -> amount * 2);
    }
}
