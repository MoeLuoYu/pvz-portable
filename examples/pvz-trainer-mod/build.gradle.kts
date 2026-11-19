// 与 pvz-minimal-mod 相同的骨架：一行 loom 插件供齐全部类路径与 runClient，模组零手动依赖。
plugins {
    id("cn.enaium.fabricpvz.loom") version "1.0.9"
}

dependencies {
    // 本地开发：用游戏仓库 build 出的发行包（先在仓库根跑过 distZip）。
    // 对着正式发布版本构建时改为：pvz("cn.enaium.fabricpvz:pvz-portable:1.2.5")
    pvz(files("../../build/distributions"))
    // ModMenu 的配置入口点接口（§7A.2）：只在编译期需要，运行时由 pvz-modmenu 模组自己提供；
    // 未安装 pvz-modmenu 时入口点没有任何读取方，声明无副作用
    compileOnly(files("../pvz-modmenu/build/libs/modmenu-1.0.0.jar"))
    // MixinExtras 注解（@ModifyExpressionValue）：Fabric Loader 0.15+ 内置运行时
    // （loader jar 的 META-INF/jars 里同名 jar），编译期用从中取出的同一份
    compileOnly(files("libs/mixinextras-fabric-0.5.5.jar"))
}

group = "cn.enaium.fabricpvz"
version = "1.0.0"
