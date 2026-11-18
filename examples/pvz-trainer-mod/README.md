# pvz-trainer-mod — 修改器示例模组

本生态的**修改器（trainer）示例**：关卡内左下角一枚**可拖动的原生石头按钮**，点击展开
**PvZ 原生对话框**操作，对话框底部"收 起"按钮收回折叠（回到悬浮按钮）。**零 Mixin**——
全部功能走事件 API、公开字段与游戏的按钮/对话框/复选框控件，是"API 之外不碰游戏内部"的活例。

## 形态

- **悬浮按钮**（折叠态）：`LawnStoneButton` 原生石钮贴图（`GameButton.drawStoneButton`，
  IMAGE_BUTTON_LEFT/MIDDLE/RIGHT 三段拼接，悬停高亮/按下位移齐全），按住可**自由拖动**
  （钳制在窗口内），位移小于阈值算点击——展开对话框；
- **原生对话框**（展开态）：`LawnDialog` 原生棕框 + 金色标题，modal 打开即自动暂停棋盘
  （`modalOpen`/`modalClose` 既有机制）；顶部**下拉式标签页**（"关 卡 / 其 他"，popup
  command widget，展开时再点收起）；内容区为**固定高度的滚动视口**（拖动滚动条 + 滚轮），
  条目在视口内居中；底部 **"收 起"**（`BUTTONS_FOOTER` 模式的标准 footer 按钮）收回折叠；
- 按钮只在关卡进行中（SCENE_PLAYING）且对话框未展开时显示；新关卡开始时自动回到最顶层，
  按钮矩形内的点击只作用于按钮本身，不会穿透到棋盘种植。

| 条目 | 功能 |
|---|---|
| **关卡 页**（无限阳光已挪至卡槽页） | 下述开关与出怪按钮 |
| 无限阳光 | 开关，每 tick 强制 9990 |
| 免费种植 | 开关，与 cheatplant 隐码同源标志位 |
| 自动收集 | 开关，与 lazyclick 隐码同源标志位（开启瞬间收走场上已有的拾取物） |
| 暂停出怪 | 开关，出怪倒计时低于按住值（600）时逐 tick 顶回，冻结在出怪点之前；只压低不抬高，开局长倒计时与自然快进不受影响 |
| 立即下一波（按钮） | 一次性，把出怪倒计时压到 5（旗波先弹"一大波"预告再出怪）；最后一波已出完不动作 |
| 推进关卡（按钮） | 一次性，连续出怪直到刷完下一面旗（每波走游戏自己的 spawnZombieWave 并复刻出怪后记账）；旗距通常 10，一次最多连刷 9 个普通波 + 1 个旗波 |
| 直接过关（按钮） | 一次性，与 wingame 隐码同路径：波次制关卡清场后走 COIN_NONE + FadeOutLevel，其余关卡中央刷本关奖励并立即收取、走过关淡出仪式 |
| **其他 页** | 下述按钮 |
| 杀死全场僵尸（按钮） | 一次性，正常致死路径播放死亡动画、留尸体 |
| 杀死全场植物（按钮） | 一次性，每株植物走自己的 die（带各自的移除特效），与 killplant 隐码同路径 |
| 清除全部小推车（按钮） | 一次性，直接置 mDead 移除（不走 die() 的奖励小推车滚入补偿） |
| 恢复全部小推车（按钮） | 一次性，缺车的行补新车（跳过土路行），从画面外滚入并播雪车音效，与 retrack 隐码同规则 |
| 启动所有小推车（按钮） | 一次性，全部就绪小推车 startMower，与 dotrack 隐码同路径 |
| 跳关器（按钮） | 一次性，打开原版隐藏的"Enter New Level"跳关对话框（F1 同款，栈上叠加） |
| **用户 页** | 下述按钮与开关（作用于当前存档） |
| 解锁金向日葵（按钮） | 一次性，对齐 unlockall 的挑战记录段：冒险标记二周目、全部非无尽挑战记录置为满解锁值（只升不降，智慧树高度 ≥500）并存档——金向日葵判定是"冒险通关且三选关页奖杯 ≥48"；主菜单打开时即时刷新选关解锁状态 |
| 临时解锁所有模式（按钮） | 一次性，翻转选关器可用性 cheat（tmpunlock 隐码同款：重算选关状态并清三模式锁）；仅主菜单可用 |
| 无限金币 | 开关，逐 tick 把当前存档金币顶到 999990（unlockall 的同一数值） |
| 设置金币（按钮） | 一次性，原生输入框（NewUserDialog 同款骨架）输入数量，回车或"确 定"写入并存档；只接受纯数字 |
| **花园 页** | 下述开关与按钮（花园场景内使用） |
| 无限花肥 / 无限杀虫剂 / 无限巧克力 / 无限树肥 | 开关，逐 tick 把对应消耗品数量顶到 100（unlockall 的同一数值） |
| 设置智慧树高度（按钮） | 一次性，原生输入框输入高度（0-500）写入智慧树的挑战记录槽；智慧树未购买则自动解锁 |
| 新增一颗随机幼苗（按钮） | 一次性，随机树种随机朝向的幼苗入花园；已满则提示不再添加 |
| 新增金盏花幼苗（按钮） | 一次性，随机颜色（白/橙/红/浅绿）随机朝向的金盏花幼苗；已满则提示不再添加 |
| 新增一颗随机大花（按钮） | 一次性，随机树种、直接成株、状态良好的盆栽；已满则提示不再添加 |
| **状态 页** | 纯展示（实时刷新）：游戏模式 / 游戏界面 / 进行时间 / 完成轮数 / 本轮总波数 / 已刷新波数 / 本波总血量 / 刷新触发血量 / 刷新倒计时 / 大波刷新倒计时 / 植物数量 / 僵尸数量 |
| **特效 页** | 8 枚打字隐码效果开关：SUKHBIR（僵尸随音乐）/ FUTURE（未来僵尸）/ MUSTACHE（僵尸胡子，附带成就）/ TRICKEDOUT（超级小推车）/ DAISIES（死亡留雏菊）/ DANCE（僵尸跳舞）/ PINATA（死亡掉糖果）/ KONAMI（彩蛋音效）——前三项有解锁门槛，不满足时蜂鸣提示；均与打字隐码同一路径，仅关卡内可用 |
| **卡槽 页** | 卡槽格数（1-10，同步购买升级记录）/ 隐藏卡槽（卡槽整体不绘制）/ 显示铲子 / 卡片无冷却 / 隐藏菜单按钮；第一格下拉+植物下拉可改任意卡槽内容；卡片下拉 + 攻速/价格/冷却输入直接改植物定义表（对已种植株同步生效） |

## 配置

- 配置文件：启动目录 `config/pvz-trainer-mod.json`（与游戏设置注册表同一文件夹，保存时自动创建）；
- **展开修改器时自动暂停**（默认开）：开 = 修改器对话框 modal 打开（`modalOpen`/`modalClose`
  既有机制暂停棋盘）；关 = 对话框非模态，棋盘在背后继续运行；
- **悬浮按钮始终置顶**（默认开）：关 = 按钮不再每帧摘下重加恢复置顶，被选项之类的后开
  窗口覆盖层级；
- 配置入口：**主菜单 X+C 组合键**（`GameSelector.keyDown` 的 Mixin 转发，只在主菜单生效，
  800ms 防自动重复连发）或 ModMenu 的"配置"按钮（`entrypoints.modmenu` 入口点实现
  `cn.enaium.fabricpvz.modmenu.api.ModMenuApi`，编译期 compileOnly pvz-modmenu 的产物，
  运行时未装 pvz-modmenu 则入口点无读取方、无副作用）。改动即时写盘。

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
- **秒杀**：`Zombie.takeDamage(1800, DAMAGE_HITS_SHIELD_AND_BODY)`，与游戏
  `Board.killAllZombiesInRadius` 的非燃烧路径同参（Board.cpp:9312），但去掉
  `DAMAGE_DOESNT_LEAVE_BODY`——正常致死动画并留尸体。

MIT License.
