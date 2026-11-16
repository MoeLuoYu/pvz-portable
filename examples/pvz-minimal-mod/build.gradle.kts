// 一行插件引入模组工具链：编译/测试/启动的类路径、runClient 任务、模组 jar 的构建
// 全部由它供齐，模组自身不声明任何游戏、Loader 或 Mixin 依赖。
plugins {
    id("cn.enaium.fabricpvz.loom") version "1.0.9"
}

dependencies {
    // 本地开发：直接用本仓库（游戏仓库）build 出的发行包（先在仓库根跑过 distZip）。
    // 对着正式发布版本构建时改为：pvz("cn.enaium.fabricpvz:pvz-portable:1.2.5")
    pvz(files("../../build/distributions"))
}

group = "cn.enaium.fabricpvz"
version = "1.0.0"
