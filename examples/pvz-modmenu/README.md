# pvz-modmenu — 模组管理界面（ModMenu）

游戏内的模组管理界面：**主菜单 → 游戏选项 → "模 组"按钮**（原"查看图鉴"槽位，
由 Mixin 注入，仅主菜单变体显示）打开。

## 界面

- **左侧**：模组列表（44×44 图标框 + 模组名 / 下一行描述，选中高亮；右侧拖动滚动条翻页，
  游戏滚轮事件只派发给焦点控件所以用滚动条）；
- **右侧**：选中模组的详情——名称、版本（id）、描述（自动换行，最多 6 行）、
  三枚原生石钮 **官网 / 问题 / 配置**、链接（名字超链接：`sources` 显示为"源码"，
  `contact` 里 `modmenu.<平台>` 键显示为平台名——支持 GitHub/GitLab/Codeberg/Gitee/
  Discord/QQ群/微信/微博/知乎/哔哩哔哩/抖音/X/YouTube/Telegram 等，未收录的键首字母
  大写回落；多个链接同行横排、放不下换行）、许可证、模组贡献者
  （栏内分"作者 / 贡献者"两行）；
  未设置的字段/按钮不显示或置灰：
  - 官网/问题未设置 → 按钮置灰
  - 配置未设置 → 按钮整体隐藏
  - 链接/许可证/模组贡献者未设置 → 条目不画
- **打开链接**：官网/问题/链接条目点击后弹出确认框——"确定要打开以下网页？" / URL /
  "永远不要打开你不信任的人提供的链接"，按钮 **是**（系统浏览器打开）/ **复制链接到剪贴板** /
  **否**。

## 元数据来源

直接读 Fabric Loader 的 `net.fabricmc.loader.api.metadata.ModMetadata`（名称/版本/描述/
作者/贡献者/许可证），联系方式（官网/问题/链接）从 `contact` map 按键 `website` / `issues` /
`sources` 取（0.19.5 的 ContactInformation 只有 `get(String)`/`asMap()`），`modmenu.*` 键
读为命名链接。图标从 `getIconPath(0)` + `ModContainer.findPath` 读取包内 PNG。

列表条目口径与主界面左下角"模组已载入 N 个"一致：游戏本体与 loader 自身计入，
排除 builtin 环境占位（JVM 等）与 jar-in-jar 嵌套子模块（如 fabric-api 聚合 jar 内的
api-* 子模块、loader 内嵌的 MixinExtras）。

## 模组接入"配置"按钮

在模组的 `fabric.mod.json` 声明入口点，并实现本模组的接口：

```json
"entrypoints": { "modmenu": ["cn.xxx.你的类"] }
```

```java
public final class YourModMenu implements cn.enaium.fabricpvz.modmenu.api.ModMenuApi {
    @Override
    public void openConfig() {
        // 叠加打开你自己的模态对话框
    }
}
```

声明后详情面板出现"配 置"按钮。

## 构建 / 安装

```bash
cd ../../ && ./gradlew distZip && cd examples/pvz-modmenu
./gradlew build        # 产物 build/libs/pvz-modmenu-1.0.0.jar
```

jar 放进版本目录 `mods/`（需同放 `fabric-api-<版本>.jar`）。

MIT License.
