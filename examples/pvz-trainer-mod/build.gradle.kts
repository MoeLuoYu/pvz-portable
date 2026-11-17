// 与 pvz-minimal-mod 相同的骨架：一行 loom 插件供齐全部类路径与 runClient，模组零手动依赖。
plugins {
    id("cn.enaium.fabricpvz.loom") version "1.0.9"
}

dependencies {
    // 本地开发：用游戏仓库 build 出的发行包（先在仓库根跑过 distZip）。
    // 对着正式发布版本构建时改为：pvz("cn.enaium.fabricpvz:pvz-portable:1.2.5")
    pvz(files("../../build/distributions"))
}

group = "cn.enaium.fabricpvz"
version = "1.0.0"
