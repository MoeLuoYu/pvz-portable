# pvz-trainer-mod — 修改器示例模组

本生态的**修改器（trainer）示例**：关卡内左下角**悬浮菜单**（点击条目触发），游戏内字幕反馈，
**零 Mixin**——全部功能走事件 API、公开字段与 Widget 系统，是"API 之外不碰游戏内部"的活例。

## 悬浮菜单

关卡进行中，左下角显示半透明面板；点击表头可收起/展开，点击条目触发：

| 条目 | 功能 |
|---|---|
| 无限阳光 | 开关，每 tick 强制 9990 |
| 免费种植 | 开关，与 cheatplant 隐码同源标志位 |
| 自动收集 | 开关，与 lazyclick 隐码同源标志位（开启瞬间收走场上已有的） |
| 秒冷却 | 开关，全部种子卡冷却计数清零 |
| 秒杀全场 | 一次性，正常致死路径播放死亡动画 |

前四项右侧实时显示 开/关 状态。面板只在 SCENE_PLAYING 显示——主菜单等界面自动隐藏且不挡点击；
新关卡开始时自动回到最顶层（面板区域的点击只作用于菜单，不会穿透到棋盘种植）。

## 构建

```bash
# 先在游戏仓库根跑一次 distZip 产出 build/distributions/（本模组的 pvz(...) 指向它）
cd ../../ && ./gradlew distZip && cd examples/pvz-trainer-mod

./gradlew build        # 产物 build/libs/pvz-trainer-mod-1.0.0.jar
./gradlew runClient    # 开发运行
```

对着正式发布版本构建时，把 `build.gradle.kts` 里的 `pvz(files("../../build/distributions"))`
换成 `pvz("cn.enaium.fabricpvz:pvz-portable:1.2.5")`。

## 安装（正式版本目录）

把 `build/libs/pvz-trainer-mod-1.0.0.jar` 放进版本目录的 `mods/`，同时确保
`fabric-api-<版本>.jar`（聚合 jar）也在里面。

## 实现要点（对照 [模组开发文档](../pvz-minimal-mod/模组开发文档.md) §5/§8）

- **悬浮菜单**：自定义 `Widget` 子类，`WidgetManager.addWidget` 挂到顶层——命中测试从
  widget 列表尾向前找（`WidgetContainer.getWidgetAtHelper`），后加入者优先，所以面板矩形内
  的点击只到达面板、不穿透到棋盘；`draw`/`mouseMove`/`mouseDown` 全部自绘自处理，
  不依赖游戏的 ButtonWidget；
- **置顶维护**：新关卡会把棋盘重新加进管理器（排到面板之上），TICK 里检测面板不在列表末位
  就摘下重新加入；`mVisible=false` 的 widget 不参与命中测试，主菜单等界面自动放行；
- **开关**：免费种植/自动收集直接写 `LawnApp` 的公开作弊标志位，行为与游戏隐码完全一致；
- **字幕**：`Board.displayAdvice` 传不带方括号的字符串即原样显示，无需语言键；
- **秒杀**：`Zombie.takeDamage(1800, DAMAGE_HITS_SHIELD_AND_BODY | (1 << DAMAGE_DOESNT_LEAVE_BODY))`，
  与游戏 `Board.killAllZombiesInRadius` 的非燃烧路径同参（Board.cpp:9312），不留尸体。

MIT License.
