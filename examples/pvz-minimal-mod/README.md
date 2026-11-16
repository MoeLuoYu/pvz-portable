# pvz-minimal-mod — 极简示例模组

本生态里**最小可运行**的模组：一个入口类 + 一个事件监听，无 Mixin，共 5 个源/配置文件。

功能：加载时打一条日志（验证装载成功），并把玩家收取的阳光翻倍（`LawnEvents.SUN_COLLECTED`，监听器返回值即最终生效的阳光数）。

## 构建

```bash
# 先在游戏仓库根跑一次 distZip 产出 build/distributions/（本模组的 pvz(...) 指向它）
cd ../../ && ./gradlew distZip && cd examples/pvz-minimal-mod

./gradlew build        # 产物 build/libs/pvz-minimal-mod-1.0.0.jar
./gradlew runClient    # 开发运行（模组与 fabric-api 在类路径上）
```

对着正式发布版本构建时，把 `build.gradle.kts` 里的 `pvz(files("../../build/distributions"))`
换成 `pvz("cn.enaium.fabricpvz:pvz-portable:1.2.5")`。

## 安装（正式版本目录）

把 `build/libs/pvz-minimal-mod-1.0.0.jar` 放进版本目录的 `mods/`，同时确保
`fabric-api-<版本>.jar`（聚合 jar，五个 api-\* 模块内嵌其中）也在里面——单放模组 jar 会报
"需要 fabric-api"。

## 文件一览

| 文件 | 作用 |
|---|---|
| `build.gradle.kts` | 一行 loom 插件 + `pvz(...)` 指向本仓库发行包 |
| `settings.gradle.kts` | 仓库源（mavenLocal / Plugin Portal / Fabric maven） |
| `gradle.properties` | 禁止 Gradle 自动下载 JDK |
| `src/main/resources/fabric.mod.json` | 声明 `"pvz-portable": "*"` 与入口类 |
| `src/main/java/.../MinimalMod.java` | 入口类，全部逻辑两行 |

**完整开发指南见同目录 [模组开发文档.md](模组开发文档.md)**（事件 / Override / 内容注册 / 资源 / Mixin / 发布 / FAQ）。
MIT License.
