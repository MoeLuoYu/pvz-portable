# pvz-trainer-mod — 修改器示例模组

本生态的**修改器（trainer）示例**：关卡内左下角一枚**可拖动的原生石头按钮**，点击展开
**PvZ 原生对话框**操作，对话框底部"收 起"按钮收回折叠（回到悬浮按钮）。**零 Mixin**——
全部功能走事件 API、公开字段与游戏的按钮/对话框/复选框控件，是"API 之外不碰游戏内部"的活例。

## 形态

- **悬浮按钮**（折叠态）：`LawnStoneButton` 原生石钮贴图（`GameButton.drawStoneButton`，
  IMAGE_BUTTON_LEFT/MIDDLE/RIGHT 三段拼接，悬停高亮/按下位移齐全），按住可**自由拖动**
  （钳制在窗口内），位移小于阈值算点击——展开对话框；
- **原生对话框**（展开态）：`LawnDialog` 原生棕框 + 金色标题，modal 打开即自动暂停棋盘
  （`modalOpen`/`modalClose` 既有机制）；4 个**原生复选框**（`LawnCommon.makeNewCheckbox`，
  OPTIONS_CHECKBOX 美术）+ 原生石钮"秒杀全场"；底部 **"收 起"**（`BUTTONS_FOOTER`
  模式的标准 footer 按钮）收回折叠；
- 按钮只在关卡进行中（SCENE_PLAYING）且对话框未展开时显示；新关卡开始时自动回到最顶层，
  按钮矩形内的点击只作用于按钮本身，不会穿透到棋盘种植。

| 条目 | 功能 |
|---|---|
| 无限阳光 | 开关，每 tick 强制 9990 |
| 免费种植 | 开关，与 cheatplant 隐码同源标志位 |
| 自动收集 | 开关，与 lazyclick 隐码同源标志位（开启瞬间收走场上已有的拾取物） |
| 秒冷却 | 开关，全部种子卡冷却计数逐 tick 清零 |
| 秒杀全场（按钮） | 一次性，正常致死路径播放死亡动画、不留尸体 |

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

- **原生控件复用**：悬浮按钮 = `LawnStoneButton` 子类（同款对话框 OK/Cancel 钮的绘制路径）；
  对话框 = `LawnDialog` 子类（`BUTTONS_FOOTER` 模式的 footer 按钮即"收 起"，id 1000 =
  `Dialog.ID_FOOTER`）；复选框 = `Checkbox` + `CheckboxListener`；模组自建控件 id 取 2001+
  高位段，避开 Dialog 的 1000/1001 与游戏的 Dialogs 常量（对话框 id 用 900）；
- **拖动**：标准 Sexy 派发的两个事实——(1) `WidgetManager.mLastMouseX/Y` 是 public，
  可在 TICK 里读全局指针；(2) mouseUp 派发给 last-down widget，指针移出按钮也可靠收到。
  mouseDown 记抓取偏移，TICK 吸附跟随（阈值 5px 区分点击/拖动），mouseUp 结束并判定；
- **置顶与穿透**：命中测试从 widget 列表尾向前找（后加入者优先），TICK 检测按钮不在列表
  末位就摘下重加；`mVisible=false` 不参与命中测试，主菜单等界面自动放行；
- **对话框生命周期**：`mApp.addDialog(id, dialog)` 打开（modal 自动暂停），底部"收 起"
  在 `buttonDepress(1000)` 里 `mApp.killDialog(mId)` 关闭并复位折叠标记；
- **秒杀**：`Zombie.takeDamage(1800, DAMAGE_HITS_SHIELD_AND_BODY | (1 << DAMAGE_DOESNT_LEAVE_BODY))`，
  与游戏 `Board.killAllZombiesInRadius` 的非燃烧路径同参（Board.cpp:9312），不留尸体。

MIT License.
