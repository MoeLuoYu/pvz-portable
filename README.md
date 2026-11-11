# PvZ-Portable (Java)

[PvZ-Portable](https://github.com/wszqkzqk/PvZ-Portable)——社区以 C++20 / SDL2 / OpenGL ES 2.0 重写的《植物大战僵尸》年度版——的 **Java 桌面移植版**，通过一个 game provider 运行在**真正的 [Fabric Loader](https://github.com/FabricMC/fabric-loader)**（含 Sponge Mixin）上，因此对游戏的扩展方式与 Minecraft Fabric 模组完全一致：`fabric.mod.json`、`ModInitializer` 入口点、`mods/` 目录、Sponge Mixin，以及在游戏自身挂钩点触发的事件总线。

当前版本为 **1.2.5**：Gradle 构建、jar、Maven 坐标（`cn.enaium.fabricpvz:pvz-portable:1.2.5`）以及发布压缩包（自包含的 `pvz-portable-1.2.5-offline.zip` 与启动器风格的 `pvz-portable-1.2.5-online.zip`）都使用这一版本号。它**不**跟随上游 C++ 项目的发布标签。

## 游戏资源从哪里找

游戏需要零售版文件（`main.pak` 和一个 `properties/` 目录）。它按以下顺序查找，命中即止：`-resdir <path>`（游戏参数）、`-Dpvz.resDir=<path>`（JVM 参数）、`PVZ_RES_DIR`（环境变量）、工作目录、运行中的 jar 所在目录，以及本机的 Steam 版《植物大战僵尸》安装（App ID 3590，遍历每个库目录查找，包括 macOS 的 `Plants vs. Zombies.app/Contents/Resources` 布局）。

存档目录采用游戏自身的默认值 `<资源目录>/savedata`——原版游戏存放存档的位置；无论以哪种方式启动（发行包脚本、开发运行或启动器），存档都在同一处：Minecraft 风格的版本包刻意不传 `-savedir`，因为把存档目录挂在启动器下面，玩家的存档就不在自己运行游戏时会写到的位置。`-savedir <path>` 游戏参数仍然可以覆盖它，这一点一如既往。

涉及启动器时有两点值得了解。第一，Minecraft 风格的启动器会把 `user.home` 重映射到它的游戏目录（HMCL 会传 `-Duser.home=<.minecraft 目录>`），因此 Steam 查找会同时从环境变量 `HOME` *和* `user.home` 取用户主目录——环境变量的优先，因为启动器不改的就是它。第二，如果一无所获，游戏会把搜索过的每个位置（以及本 JVM 的 `user.home`）记入日志，所以原因写在日志里而无需猜测：见 `../verification/VERIFICATION.md` 第 70 节。

本移植版分为三层，与上游对应：引擎（`sexy/`，相当于 SexyAppFramework）、游戏（`lawn/`，相当于上游的 `src/Lawn/`）和可移植运行时（`pvzp/`，相当于 `PvzpLib`）。窗口、渲染与输入使用 **LWJGL 3.4.3 的 `lwjgl-sdl`（SDL3）**，音频使用 OpenAL + stb_vorbis（MO3 经由 libopenmpt）。

游戏代码本身**完全不依赖 Fabric**：Fabric Loader 只是启动器，在 loader 眼中本游戏是一个 mod id 为 `pvz-portable` 的内置模组。所有 Fabric 相关的内容——`GameProvider`、启动库、事件/内容 API、模组 Gradle 插件——都在 [相关项目](#相关项目) 中列出的姊妹项目里。

**本仓库不附带任何游戏资源。**《植物大战僵尸》版权归 PopCap/EA 所有，游戏从你指定的目录（或本机的 Steam 安装）读取零售版资源——见 [游戏资源（用户提供）](#游戏资源用户提供)。

## 环境要求

| 组件 | 版本 | 说明 |
|---|---|---|
| JDK | **25** | Gradle 工具链锁定为 25，源码以 `--release 25` 编译 |
| Gradle | **9.8.1** | 由 wrapper 锁定——`./gradlew` 即可，无需预装 Gradle |
| 平台绑定 | **LWJGL 3.4.3**：`lwjgl-sdl`（SDL3）、`lwjgl-opengl`、`lwjgl-openal`、`lwjgl-stb`、`lwjgl-nfd` | 只有 LWJGL 3.4.x 提供 SDL3 绑定（3.3.x 没有发布 `lwjgl-sdl`） |
| 音频（可选） | `libopenmpt` | 仅 MO3 音乐需要。发行包内置其 Windows 版构建，移植版优先使用；macOS/Linux 使用系统安装的副本。没有任何副本时音乐被禁用，音效正常工作 |
| macOS | `-XstartOnFirstThread` | SDL 需要 JVM 的主线程持有首线程；`run`/test 任务会自行添加该开关 |
| Kotlin | 2.4.21 | **仅模组需要**：模组工具链按此版本编译 Kotlin 模组，而 Kotlin 模组在运行时需要 `fabric-language-kotlin` 语言模块作为 `kotlin.*` 的所有者。游戏本身不需要 Kotlin |

`gradle.properties` 设置了 `org.gradle.java.installations.auto-download=false`，因此机器上必须已经装好 JDK 25。

## 构建、测试、运行

```bash
./gradlew build                      # 编译 + 整个测试套件（1458 个测试，145 个测试类）
./gradlew test                       # 仅测试
./gradlew test --tests 'cn.enaium.fabricpvz.portable.lawn.BoardTest'
./gradlew run                        # 在真正的 Fabric Loader 上启动游戏（游戏目录：run/）
./gradlew syncDevResources run -PpvzResDir="/path/to/resources"   # 把 main.pak/properties 复制进 run/，然后启动
```

`./gradlew run` 启动的是 Fabric Loader 自带的启动器（`net.fabricmc.loader.impl.launch.knot.KnotClient`），工作目录设为 `run/`，并传入 `-Dpvz.gameDir=<项目>/run`、`-Dfabric.skipMcProvider=true`、`-Dfabric.development=true` 和 `--enable-native-access=ALL-UNNAMED`（macOS 上另有 `-XstartOnFirstThread`）。`run/mods/` 是 loader 读取模组的位置，与正式安装完全一致；`run/.fabric/` 是 loader 的缓存，`run/userdata/` 存放存档数据。`run` 不会向 `run/mods/` 复制任何内容：一次运行加载的模组就是你亲手放进那里的 jar，其中包括 API 的 `fabric-api-<version>.jar`，因此开发运行看到的模组集合与玩家的正式安装完全相同。`run` 不依赖资源同步：要用显式指定的资源目录，请在同一条命令里带上 `syncDevResources`（`./gradlew syncDevResources run -PpvzResDir=<dir>`），它只把 `main.pak` 和 `properties/**` 复制到 `run/`。不带它时，游戏会通过下面的发现链找到资源（例如你的 Steam 安装）。

### 构建需要从其他仓库得到什么

本项目是独立的：它消费的每个姊妹构件都以 Maven 坐标的形式来自 `mavenLocal()`，绝不取自姊妹仓库检出的构建目录。它的启动器（发行包的 `lib/`，本构建的 `runtimeOnly` 依赖）是带 Mixin、ASM 和日志栈的真正 Fabric Loader，外加 game provider——后者是*本*项目自身源码的一部分（`src/main/java/cn/enaium/fabricpvz/provider/`）；模组 API 同样是一个普通发布的坐标——它的五个功能模块（`api-base`、`api-events`、`api-plant`、`api-resource`、`api-zombie`）只出现在测试的*编译*类路径上，而其聚合 jar（`fabric-api`）则是 loader 端到端测试在真实 `mods/` 目录中找到的东西（版本号是 `build.gradle.kts` 中的 `apiVersion`，目前为 `1.2.0`）。因此，从一次新克隆开始（姊妹仓库并排检出）：

```bash
# 本项目，跳过测试，先把它的 POM 放进 mavenLocal（API 要针对本游戏编译）
./gradlew publishToMavenLocal -x test

# 来自 fabric-api 仓库：发布聚合 `fabric-api` jar 和五个 `api-*` 模块
# （它的事件类型针对本游戏的类定义；它的构建从 Gradle Plugin Portal 解析模组 Gradle 插件
# `cn.enaium.fabricpvz.loom`，因此该插件既不需要检出也不需要本地发布）
./gradlew publishToMavenLocal

# 只有测试套件需要这一项：loader 端到端测试会把它的 jar 装进真实的 mods/ 目录
# 来自 pvz-example-mod 仓库
./gradlew publishToMavenLocal

# 现在本项目可以完整地构建和测试了
./gradlew build
```

改过 API 之后，先重新发布它，再运行本项目的测试：

```bash
(cd ../fabric-api && ./gradlew publishToMavenLocal)
cd ../PvZ-Portable && ./gradlew test
```

编译移植的游戏本体只需要 game provider；API 发布缺失或过时会表现为无法解析的依赖，或者表现为某个测试与 API 的工作区对不上。

## 游戏资源（用户提供）

游戏按以下顺序查找资源根目录（命中即止）；一个目录只要包含 `main.pak`（不区分大小写）或 `properties/` 目录即符合条件：

1. `-resdir=<path>` 命令行参数
2. `PVZ_RES_DIR` 环境变量
3. 当前工作目录
4. 运行中的 jar/可执行文件所在目录
5. 本机 **Steam** 安装的《植物大战僵尸》年度版（App ID 3590），遍历 `steamapps/libraryfolders.vdf` 列出的每个库目录查找
   - macOS：`~/Library/Application Support/Steam/steamapps/common/Plants Vs Zombies/…/Contents/Resources/`
   - Windows：`<steam>/steamapps/common/Plants vs. Zombies/`
   - Linux：`~/.steam/steam`、`~/.steam/root`、`~/.local/share/Steam`、Flatpak 路径、`~/.steam/debian-installation`

可写数据（存档、注册表模拟、资源缓存）写入 `-savedir=<path>`，默认为各平台标准的应用数据目录。因此直接从项目目录运行游戏，会在源码旁边创建 `userdata/` 和 `cache64/`。

## 发行包（`distZip`）

```bash
./gradlew distZip       # → build/distributions/pvz-portable-1.2.5-offline.zip
./gradlew installDist   # → build/install/pvz-portable/（同样的内容，解压形式）
```

这个 zip 是 Gradle `application` 插件自己的归档——没有任何手工打包——而且是**一份通吃所有平台的 zip**：它携带 Windows、Linux 和 macOS 在 x64 与 arm64 上的 LWJGL 本地库（六个 classifier × 六个 LWJGL 模块 = 36 个 natives jar，约占 zip 的 25 MB），启动脚本会为运行它的机器挑选对应目录。

```
pvz-portable-1.2.5/
├── bin/pvz-portable            启动脚本：Fabric Loader 的启动器（KnotClient）——与
├── bin/pvz-portable.bat        `./gradlew run` 使用的入口点相同；.bat 是它的 Windows 对应版本
├── lib/                        pvz-portable-1.2.5.jar 以及启动所需的一切：
│                               启动器栈（Fabric Loader、Mixin、ASM、Log4j 绑定和
│                               log4j2.xml）、slf4j-api、LWJGL 各模块和全部 36 个 natives jar
└── launcher/assets/indexes/    pvz-portable-1.2.5.json——启动器预启动检查所需的空资源索引
                                （14 字节）。把这棵目录树复制到游戏目录
                                （`cp -r <package>/launcher/assets <game dir>/`）即可在本地满足
                                该检查：无需下载、无需联网
```

zip 里没有 `mods/` 目录——这是唯一需要你自己添加的东西。

**本地库（`lib/native/`）。** `lib/` 还携带游戏自行加载的本地库，每个平台一个目录：`lib/native/<os>-<arch>/` 为 `windows-x64` 和 `windows-arm64` 放置 `libopenmpt.dll`——上游发布的唯一已编译 libopenmpt 二进制就是 Windows 版（其下载页把其他所有系统都指向发行版软件包）——以及该库链接所需的编解码 DLL（`openmpt-ogg`、`openmpt-mpg123`、`openmpt-vorbis`、`openmpt-zlib`）、许可证文件 `lib/native/libopenmpt-LICENSE.txt`，还有 `lib/native/libopenmpt-natives.txt`——记录这些文件来自哪个发布压缩包及其 sha256 校验和。构建会下载该压缩包一次、校验 sha256 并暂存（`./gradlew libopenmptNatives`）：既没有网络、Gradle 用户主目录里也没有缓存（`caches/pvz/libopenmpt`）的构建会失败并提示应获取哪个 URL，而不是产出一个缺了它们的 zip；一旦缓存，`--offline` 构建即可工作。

移植版**优先**查找这里，找不到时回退到系统库，因此在 macOS 和 Linux 上会继续使用包管理器安装的副本（`brew install libopenmpt`、`apt install libopenmpt0`……）；你自己编译的 `libopenmpt.dylib`/`libopenmpt.so` 放进 `<游戏目录>/lib/native/<os>-<arch>/`（或 `<游戏目录>/native/<os>-<arch>/`）后同样优先。没有任何副本时，游戏照常可玩，只有两首 MO3 音乐除外——音效和所有 `.ogg` 曲目都用 Java 解码——并记录一行 `Music disabled: libopenmpt is not available`。某个副本成功加载时，会有一行 INFO 报告路径和版本：`Music: libopenmpt 0.8.9 loaded from …/lib/native/windows-arm64/libopenmpt.dll`。

**安装模组。** zip 不附带 `mods/` 目录，也不附带模组 jar，因为模组是玩家自己安装的东西。Fabric Loader 从它被启动所在的游戏目录读取 `mods/`，对解压后的发行包而言，游戏目录就是解压根目录——即容纳 `bin/` 和 `lib/` 的那个目录，所以 **`mods/` 与 `bin/` 并排**：

```bash
unzip -q build/distributions/pvz-portable-1.2.5.zip -d /tmp/pvz
mkdir -p /tmp/pvz/pvz-portable-1.2.5/mods
cp /path/to/fabric-api-1.2.0.jar /path/to/some-mod-1.0.0.jar /tmp/pvz/pvz-portable-1.2.5/mods/
/tmp/pvz/pvz-portable-1.2.5/bin/pvz-portable     # 从任何工作目录启动都行
```

启动脚本把 `-Dpvz.gameDir` 设为根据脚本自身位置推导出的解压根目录（`$APP_HOME` / `%APP_HOME%`），因此无论怎样启动脚本——双击、快捷方式、换个工作目录——都能找到 `mods/`。它只是一个**默认值**：脚本先展开 `DEFAULT_JVM_OPTS`，然后才是用户的 `JAVA_OPTS`/`PVZ_PORTABLE_OPTS`，而 JVM 中后出现的同名 `-D` 会胜出，所以你可以把游戏目录挪走：

```bash
PVZ_PORTABLE_OPTS="-Dpvz.gameDir=/some/other/game/dir" /tmp/pvz/pvz-portable-1.2.5/bin/pvz-portable
```

game provider 也接受 `-gamedir <path>` 游戏参数，但会先解析 `-Dpvz.gameDir`，因此在解压后的发行包中，脚本的系统属性会遮蔽它。脚本已经传入 `--enable-native-access=ALL-UNNAMED` 和 `-Dfabric.skipMcProvider=true`（macOS 上还有 `-XstartOnFirstThread`）；额外的 JVM 选项通过 `PVZ_PORTABLE_OPTS` 或 `JAVA_OPTS` 添加。

资源仍需你自己指定，可以用游戏参数，也可以用环境变量：

```bash
/tmp/pvz/pvz-portable-1.2.5/bin/pvz-portable -resdir "/path/to/Plants vs. Zombies/Resources"
PVZ_RES_DIR="/path/to/resources" /tmp/pvz/pvz-portable-1.2.5/bin/pvz-portable
```

这个 zip 也是**模组构建编译所针对的东西**：模组项目的 `pvz(...)` 配置指名本游戏，取该版本的发布资产 `pvz-portable-<version>.zip`，并把它的 `lib/` 放上模组的编译/测试/运行类路径。因此改变进入 `lib/` 的内容就会改变模组链接的对象——工具链本身见 `loom` 仓库。

### 在 Windows 上

Steam 安装会被自动找到（`steamapps/libraryfolders.vdf` → 每个库目录），因此零售版 `Plants Vs Zombies` 目录——包括 Steam 版的 `.au` 音效——不需要外层包装目录，也不需要转换过的资源。已在 Windows 11 ARM64 + Liberica JDK 25 + 真实 Steam 资源上验证（../verification/VERIFICATION.md §59）。

**渲染后端。** 在某些 Windows 环境下，SDL 默认渲染器可能在 `SDL_CreateRenderer` 内部把 JVM 拖垮（`EXCEPTION_ACCESS_VIOLATION`）——此时启动日志里没有 `RENDERER:` 一行，窗口也没有内容。请改用软件后端：

```bat
set PVZ_PORTABLE_OPTS=-Dpvz.renderer=software
bin\pvz-portable.bat
```

缺少 `RENDERER:` 一行意味着请求的 GPU 后端失败，移植版回退到了离屏软件渲染器，所以如果画面看起来不对，先看日志行。

**音乐。** MO3 曲目需要 libopenmpt，而它的 Windows 版构建随发行包一同分发（`lib/native/windows-x64` / `windows-arm64`，见上文"发行包"一节），因此无需额外安装。系统副本仍是所有平台上的回退：Windows 上是 `PATH` 或 `C:\Program Files\libopenmpt\` 里的 `libopenmpt.dll`，macOS 上是 Homebrew 的，Linux 上是发行版软件包。没有任何副本时，日志会显示 `Music disabled: libopenmpt is not available` 和两次 `music failed to load`。两者都不致命：游戏照常运行，只是没有音乐。

**音效。** WAV、Ogg Vorbis、MO3 和 AU（µ-law，Steam 资源使用的 Sun/NeXT 格式）都按内容而非文件名解码。容器无法被任何解码器识别的音效会被记录（`Skipping sound '…': unrecognized audio container in …`）并跳过；而 pak/properties/data 文件中缺失的音效仍会让加载线程失败——因此损坏的清单不会被跳过逻辑掩盖。

### 在 Linux 上

已在 Fedora 44 Server（aarch64，OpenJDK 25）上验证，Wayland 与 X11 皆可：窗口转储、两个模组测试床、一次实际游玩关卡、Steam 资源发现和 MO3 音乐都与 macOS 上的数字一致（../verification/VERIFICATION.md §62）。

**显示。** SDL3 需要显示服务器，而 SSH 登录既没有 `DISPLAY` 也没有 `WAYLAND_DISPLAY`。两条路都可行——Wayland 走会话的运行时目录：

```bash
XDG_RUNTIME_DIR=/run/user/$(id -u) WAYLAND_DISPLAY=wayland-0 SDL_VIDEODRIVER=wayland bin/pvz-portable
```

以及 X11/Xwayland，它需要会话自己的授权文件：裸的 `DISPLAY=:0` 会失败，报 `Authorization required, but no authorization protocol specified`（随后是 `SDL_Init(SDL_INIT_VIDEO) failed: x11 not available`），因为 SSH 环境没有 `~/.Xauthority`：

```bash
DISPLAY=:0 XAUTHORITY=/run/user/$(id -u)/.mutter-Xwaylandauth.XXXXXX SDL_VIDEODRIVER=x11 bin/pvz-portable
```

`Failed to load plugin 'libdecor-gtk.so': failed to init` 是 Server 安装缺少 SDL 的 Wayland 客户端装饰插件（libdecor + GTK3）；SDL 会回退到服务端装饰或无装饰窗口，窗口工作正常，因此这一行是环境所致，不是缺陷。

**渲染后端。** 默认（`opengl`）在没有 GPU 的机器上也能工作——虚拟机经 Mesa 的 llvmpipe 运行它——`-Dpvz.renderer=software` 同样可用。两者画出相同的画面；软件后端曾经在与窗口管理器首次调整大小的竞争中落败，该问题已修复并在 ../verification/VERIFICATION.md §62 中说明。

**本地库目录。** 目录名是 `OpenMptLibrary.nativePlatform()` 产出的名字：`linux-x64` 和 `linux-arm64`。架构会被规范化，`aarch64` 映射为 `arm64`——`lib/native/linux-aarch64/` 目录**不会**被找到。

**音乐（libopenmpt）。** 发行版软件包现在会被自动发现（Fedora/RHEL 上 `dnf install libopenmpt` 安装 `/usr/lib64/libopenmpt.so.0`，`apt install libopenmpt0` 安装 multiarch 的 `/usr/lib/<triplet>/libopenmpt.so.0`），游戏目录里的副本仍然优先。没有 root 时，可以把同一个库解包进发行包自带的本地库目录：

```bash
mkdir -p ~/libopenmpt && cd ~/libopenmpt
curl -sSLO https://dl.fedoraproject.org/pub/fedora/linux/releases/44/Everything/aarch64/os/Packages/l/libopenmpt-0.8.6-1.fc44.aarch64.rpm
rpm2cpio libopenmpt-0.8.6-1.fc44.aarch64.rpm | cpio -idmv        # → usr/lib64/libopenmpt.so.0.5.5
mkdir -p ~/pvz/lib/native/linux-arm64
cp -L usr/lib64/libopenmpt.so.0.5.5 ~/pvz/lib/native/linux-arm64/libopenmpt.so
```

它的依赖（`libz`、`libmpg123`、`libogg`、`libvorbis`、`libstdc++`）来自基础系统。已验证：`Music: libopenmpt 0.8.6 loaded from …/lib/native/linux-arm64/libopenmpt.so`，且两首 MO3 曲目解码出的 PCM 与 macOS 和 Windows 相同（MAINMUSIC：8,373,498 帧，非零采样 0.9994，峰值 14462）。

### 发布

发布是从打了标签的提交手工完成的：本仓库**没有任何 CI 自动化**——没有 workflow，push 时也不运行任何东西——因此发布是一次刻意为之的本地操作。

```bash
# 从要发布的提交开始（main 上），以构建声明的版本打标签：标签命名发布，
# 发布资产也以同一版本命名
VERSION="$(./gradlew -q printVersion)"                       # 1.2.5
git tag -a "$VERSION" -m "pvz-portable $VERSION" && git push origin "$VERSION"
test "${VERSION#v}" = "$(git describe --tags --exact-match)" # 标签 == 版本，否则就此停下

# 发行包就是产物（若还需测试套件通过，先跑 `./gradlew build`）
./gradlew distZip                                            # → build/distributions/pvz-portable-$VERSION.zip

# 把它附加到该标签的 release 上；`--verify-tag` 会拒绝尚未推送的标签；重新上传已有 release 用
# `gh release upload "$VERSION" "build/distributions/pvz-portable-$VERSION.zip" --clobber`
gh release create "$VERSION" "build/distributions/pvz-portable-$VERSION.zip" \
  --title "pvz-portable $VERSION" --verify-tag
```

`test` 那一行就是"标签与项目版本一致"规则的全部：版本号只存在于 `build.gradle.kts` 中（`./gradlew -q printVersion` 会打印它），资产名由它派生，因此发布不可能带上贴错标签的 zip。zip 本身不依赖构建它的机器，因为 `lib/` 携带所有受支持平台的本地库。打包交给 Gradle 的 `distZip`，绝不手工卷归档，也不要把 workflow 加回来（`AGENTS.md` §6 说明了原因）。

1.0.0 发布正是这样完成的，来自同一份 `./gradlew distZip` 输出。

## 模组

本游戏的模组就是一个普通的 Fabric 模组：一个带 `fabric.mod.json` 和（可选）Mixin 配置的 jar，放进游戏目录的 `mods/`。

```json
{
  "schemaVersion": 1,
  "id": "my-mod",
  "version": "1.1.0",
  "environment": "*",
  "entrypoints": { "main": ["com.example.MyMod"] },
  "mixins": ["mymod.mixins.json"],
  "depends": { "fabricloader": ">=0.19.0", "pvz-portable": "*", "fabric-api": ">=1.0.0" }
}
```

- **Mod id。** 游戏是 `pvz-portable`；API 是 `fabric-api`（其功能模块 `api-base`、`api-events`、`api-plant`、`api-resource`、`api-zombie` 嵌在那个 jar 内）。想要 API 的模组声明 `"fabric-api": ">=1.0.0"`，针对游戏声明 `"pvz-portable": "*"`——游戏的版本不是模组必须跟随的东西。
- **入口点**是 Fabric 自己的接口（`net.fabricmc.api.ModInitializer`、`ClientModInitializer`、`net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint`）。game provider 在游戏主类之前调用 `main`/`client`；`preLaunch` 是 loader 自己的。Fabric Loader 不定义模组之间的顺序，因此初始化器不得假设另一个模组的初始化器已经运行。
- **Mixin** 是 Fabric Loader 自带的真正 Sponge Mixin（`@Mixin/@Inject/@Redirect/@Overwrite/@Shadow/@Accessor/@Invoker`）。本移植版不带映射运行，因此 mixin 引用按原样使用，不需要 refmap。
- **内容**（自定义植物和僵尸）来自 `api-plant`/`api-zombie`，模组自己的美术（它的种子包图标、重新着色的零售植物）来自 `api-resource`：注册一个定义会向本游戏容量固定的定义表写入一行（`SeedType.MAX_SEED_TYPES`、`ZombieType.MAX_ZOMBIE_TYPES`；空行表示"未注册"），API 的 Mixin 把定义的行为分派进游戏自己的方法——游戏自身没有注册表，也没有模组概念。种子选择器和图鉴都会在原版网格（第 0 页）之后的页面上显示已注册内容：玩家已解锁的已注册种子加入选择器的后续页面，图鉴的植物页也得到同样的后续页面（其僵尸页为零售范围之上的每个已定义僵尸各提供页面），条目的卡片显示注册时携带的名称和描述。
- **事件**来自 API：`cn.enaium.fabricpvz.api.lawn.LawnEvents`（关卡开始/结束、波次、僵尸生成/死亡/受伤、植物放置/移除/受伤、收集阳光、种植校验、每一 tick、绘制前后）、`cn.enaium.fabricpvz.api.PvzFabricApi` 和 `cn.enaium.fabricpvz.api.mod.ModQuery`（Fabric Loader 的模组查询）。API 还自带一个 Fabric 风格的事件总线（`cn.enaium.fabricpvz.api.event`），而不是依赖 Fabric API 的 base 模块。
- **模组从哪里加载。** 始终是游戏目录下的 `mods/`。只在类路径上的模组会在 loader 以开发模式运行时被发现（`-Dfabric.development=true`，`./gradlew run` 会传入）——模组自己的 `runClient` 就是这样工作的。API 在任何情况下都是 `mods/` 里的 jar，绝不是类路径条目：开发期间你要自己把 `fabric-api-<version>.jar` 复制到 `run/mods/`——构建不会往那里安装任何东西。

构建模组只需要一行插件（`id("cn.enaium.fabricpvz.loom")` 加上它的版本），由 `loom` 仓库发布在 Gradle Plugin Portal 上；该工具链带来 loader、Mixin、ASM、API 和本游戏（以发行包形式，见上文），外加一个通过真实 loader 启动游戏的 `runClient` 任务。模组可以用 Kotlin 写，也可以用 Java：工具链以与 Java 源码相同的 class 文件目标编译 Kotlin，针对 `kotlin-stdlib` 编译模组但不打包、不发布它，并带来 `fabric-language-kotlin` 语言模块——`kotlin.*` 运行时的唯一所有者、`"kotlin"` 值适配器的提供者。Kotlin 模组在自己的 `fabric.mod.json` 中声明该模块，并使用带无参构造器的 `class`（而不是 `object`）作为入口点，除非它显式指定适配器。

## 移植状态

移植已经完成：上游每一个可运行的模式、界面和子系统都已实现；游戏代码里只剩一个 `UnsupportedOperationException`，针对旧版 `.dat` 关卡中存档格式（见下）。

> 状态：`Done` = 已实现且已测试；`Partial` = 主要机制已实现，但少数分支抛出 `UnsupportedOperationException` 并在代码中标注；`N/A` = 上游概念无法或无需映射到 Java 侧（附上游证据）。
> 验证：JUnit 套件（`./gradlew test`）、像素级渲染后端测试（软件后端与真实 GPU 后端），以及在资源未安装时自动跳过的真实 Steam 资源测试。

| 模块 | 状态 | 验证 |
|---|---|---|
| 引擎：SDL3 平台层（窗口/输入/光标/剪贴板/计时） | Done | `SdlRendererTest` + `SdlRendererGpuTest`（包含一次真实的 Metal 运行） |
| 引擎：渲染后端（纹理/旋转/翻转/着色/裁剪/离屏/回读/三角形） | Done | 7 个像素级测试：6 个针对软件渲染器 + 1 个针对真实 GPU（Metal）的着色/三角形 |
| 引擎：Image/MemoryImage/Graphics/字体（desc 解析器位图字体） | Done | 逐代理测试 + 真实字体资源测试 |
| 引擎：资源清单/PAK/编译缓存/DataSync/资源发现（本地 + Steam） | Done | `ResourceManagerRealAssetsTest`（691/168/20 个条目）、`PakInterfaceTest`、`SteamLibraryFinderTest` |
| 引擎：音频（OGG/MO3/增益/淡入淡出） | Done | 62 个音频测试，包括 169 个真实 OGG 文件和 2 个 MO3（libopenmpt） |
| 引擎：Widget/Dialog/应用主循环（100 Hz 固定步长） | Done | `WidgetTest`/`WidgetManagerTest`/`SexyAppBaseTest` |
| 游戏：Reanimator/Attachment/Atlas/粒子/特效/Foley/Trail | Done | `ReanimatorTest`/`ParticleTest`/`FoleyTest` 等 + 真实 reanim 资源 |
| 游戏：Board/网格/波次/阳光/小推车/绘制层顺序 | Done | 全部 22 个 `BoardTest` 测试通过（逐条对照上游裁定）；`GameBootSmokeTest`：用真实 Steam 资源启动 → 冒险模式 1-1 → 600 tick 无异常 |
| 游戏：植物/僵尸/弹射物/硬币/网格物品（完整定义表） | Done | `PlantTest`（53 行表）/`ZombieTest`（33 行表）/`ProjectileTest`/`CoinTest` |
| 游戏：LawnApp/界面与对话框/过场动画 | Done | `LawnAppTest`/逐界面测试/`CutSceneTest` |
| 游戏：存档（users.dat/userN.dat/关卡中存档 .v4） | Done | `ProfileMgrTest` 字节级黄金样本 |
| 游戏：禅境花园（花园网格/花盆/需求/Stinky/商店/教程/离开） | Done | 34 个 `ZenGardenTest` 测试（像素↔网格、需求状态机、成长与硬币、Stinky 状态机、售价表）；`ChallengeModeCoverageTest` 实际运行花园模式并断言 `mZenGarden.mBoard` 已接管 |
| 游戏：Zombatar（编辑器/配件表/调色板/TOS/写回存档） | Done | `ZombatarTest` 12 + `ZombatarWidgetTest` 10 + `ZombatarHeadTest` 3；4 个 `GameSelectorTest` 测试验证按钮→编辑器/TOS 绑定 |
| 游戏：全部挑战模式实现（Beghouled·Twist / Scary Potter·Vasebreaker / I, Zombie / Portal Combat / Slot Machine / Whack-a-Zombie / Zombiquarium / Squirrel / Tree of Wisdom / 天气与 Last Stand 等） | Done | 10 个 `ChallengeXxx` 辅助类共 193 个测试；`ChallengeModeCoverageTest` 把 **73 个模式**逐一启动、各跑 150 tick，并断言它们各自独特的结构（花盆、4 个传送门、≥7 只松鼠、鱼、墓碑、老虎机拉杆、智慧树云朵、僵尸的预设植物等） |
| 游戏：Crazy Dave 商店追加推销过场（GAMEMODE_UPSELL） | Done | 12 个 `CutSceneTest` 测试：完整的 3300→3317 序列（每个阶段的桌台、商店/菜单按钮交接、`StoreScreen.getItemCost`） |
| 游戏：作弊对话框与关卡跳转 | Done | `CheatDialogTest` 18 + `LawnAppCheatTest` 8（包括 `sscanf` 语法族和无效输入分支） |
| 游戏：关卡中存档（`.v4`） | Done | 19 个 `SaveGameTest` 测试：进行中关卡的往返、读取后逐字节相同的重写、CRC/版本拒绝 |
| 游戏：旧版 `.dat` 关卡中存档 | N/A | 上游 README：`.dat` 是"原始内存转储……不要跨平台共享此文件"；移植版按上游标准识别它们并给出可操作的提示（用上游版本打开以自动迁移为 `.v4`），而不伪造解码器 |
| Fabric Loader 集成（game provider + API + 真实 `mods/` 目录） | Done | `FabricLoaderEndToEndTest` 在第二个 JVM 中启动 Fabric Loader 0.19.5，让 API jar、示例模组和夹具模组作为真实 `mods/` 目录中仅有的三个 jar——在测试运行的两种模式下 API 都不在类路径上——并验证解析出的模组列表（游戏、loader、聚合 jar 内嵌的 API 五个模块、三个模组 jar）、应用到游戏类上的模组 Mixin、触达模组监听器的 API 钩子链、夹具在真实棋盘上的植物与僵尸注册，以及游戏启动冒险模式第 1 关直到 `SCENE_PLAYING`（300 tick，模组的 Board Mixin 处于运行中） |
| 发行/发布（`application` 插件的 zip） | Done | `./gradlew distZip` → `pvz-portable-1.2.5-offline.zip`（仅 `bin/` + `lib/`，约 28 MB），`./gradlew mcVersionZip` → `pvz-portable-1.2.5-online.zip`，含 36 个 LWJGL natives jar、内置的 `lib/native/windows-{x64,arm64}/` libopenmpt（因此 Windows 无需安装库）且没有 `mods/` 目录；已在仓库外验证：解压到 `/tmp`、在 `bin/` 旁创建 `mods/`、复制模组 jar、从无关工作目录启动 `./bin/pvz-portable` → Fabric Loader 解析模组，游戏到达 `SCENE_PLAYING` |

## 与上游的差异（偏差日志）

| 项目 | 上游 | 本移植版 | 原因 |
|---|---|---|---|
| 窗口/渲染 | SDL2 + SDL_GL（GLES2/GL2.1）+ 自定义 GL 批处理 | **SDL3 的 `SDL_Renderer`**（macOS 上为 Metal，无 GPU 时为软件渲染） | LWJGL 只提供 SDL3 绑定；它以最低的代码量和风险保持 2D 语义一致 |
| 音频 | SDL-Mixer-X fork + libopenmpt | **OpenAL + stb_vorbis**；MO3 经由 **libopenmpt**（JDK FFM） | 没有 SDL_mixer 的 JVM 版本；增益/淡入淡出语义得以保留 |
| 图像解码 | libpng/libjpeg/内置 gif/TGA | **Java ImageIO**（PNG/JPEG/GIF）+ 自实现 TGA | 去掉本地依赖 |
| 注册表持久化 | regemu（真实资源下 `mRegKey` 为空，因此它没有效果） | 相同（保留 RegEmu 实现） | 与上游行为一致 |
| 编译缓存 | `cache64/` 二进制缓存 | 保留，读写同一缓存格式 | 见资源相关研究笔记 |
| Mixin | Sponge Mixin 0.8 | **随真实 Fabric Loader 附带的 Sponge Mixin 构建**，通过 `fabric.mod.json` 的 `mixins` 条目配置 | 无需维护自制引擎；模组使用标准 Mixin API 和配置格式 |
| 种子选择器 | 一页零售种子网格 | **第 0 页就是该网格**；玩家解锁的超出零售范围的种子类型（id ≥ `SeedType.NUM_SEEDS_IN_CHOOSER`，即模组注册的内容）获得后续页面，每页按 id 顺序最多 `SeedChooserScreen.CHOOSER_PAGE_SIZE`（48）个，"Let's Rock" 旁有上一页/下一页箭头 | 模组内容需要在选择器中有容身之处，又不能打乱零售布局；只有一页时网格之外的任何东西都不绘制、不可点击，因此未装模组的选择器与上游完全一致 |
| 图鉴 | 一个固定的植物网格和一个固定的僵尸网格 | **第 0 页就是这两个网格**；植物页为玩家已解锁的已注册种子类型获得后续页面（id ≥ `SeedType.NUM_SEEDS_IN_CHOOSER`，每页按 id 顺序 `AlmanacDialog.ALMANAC_PLANT_PAGE_SIZE` = 48 个），僵尸页为零售范围之上的已定义僵尸类型获得后续页面（id ≥ `ZombieType.NUM_ZOMBIE_TYPES`，每页 25 个），底栏按钮行的空余区域有上一页/下一页箭头和`页码/总数`计数器 | 与选择器相同的规则，且游戏侧保持中立：名称和描述经由图鉴自己的 `AlmanacDialog.getPlantDescriptionText` / `getZombieNameText` / `getZombieDescriptionText` 接缝，由 `api-plant`/`api-zombie` 为已注册内容注入；只有一页时不绘制箭头和计数器，因此未装模组的图鉴与上游完全一致 |
| 缓存植物帧 | `ReanimatorCache::MakeCachedPlantFrame` 为一份写死的种子类型列表绘制单独的 `anim_head_idle` 层 | 规则改为从植物的重动画读取（存在 `anim_head_idle` ⇒ 绘制该层），这正是那份列表的本意 | 该帧是光标预览、种子包美术和图鉴图片所显示的内容，而零售列表让一个用相同重动画注册的植物在这三处都没有头；零售集合不变（只有五个豌豆射手的重动画带有该层） |
| 窗口与呈现 | 窗口以 `SDL_WINDOW_OPENGL \| SDL_WINDOW_RESIZABLE` 创建（无高 DPI 标志）；每个图元直接画进窗口并由 SDL 的逻辑呈现缩放，窗口服务器在 Retina 显示器上拉伸 800x600 的帧 | 窗口请求 `SDL_WINDOW_HIGH_PIXEL_DENSITY`；每一帧 1:1 画进 800x600 的离屏**画布**，渲染器每帧把该画布合成到 drawable 一次（上游的 `mScreenImage` 架构），整数倍放大用最近邻过滤，其余用线性过滤 | 用户要求 SDL 窗口的高像素模式；缩放每个图元会扭曲旋转/倾斜的四边形，且在分数倍缩放下会把部分源像素复制两次、另一些完全丢失（文字笔画粗细不均）——见 `../verification/VERIFICATION.md` §52。这是对上游的刻意偏离，不是移植上的捷径 |
| 重新开始按钮 | 按钮标签用 `[RESTART_LEVEL]`（`ContinueDialog.cpp` / `NewOptionsDialog.cpp`） | 两处按钮（"继续游戏？"对话框与关卡内选项菜单）改用 `[RESTART_BUTTON]` | 零售字符串表里 `RESTART_LEVEL` 的值是一句确认问话（"你确定想要重新开始这一关卡吗？"）而非按钮文本，按钮文本在 `RESTART_BUTTON`（"重新开始"）下；照抄上游会让按钮显示整句问话 |
| 存档中的字幕文本 | 关卡存档按原始字节同步字幕标签（`SaveGame.cpp:690-701`，英文零售版里即 ASCII） | 字幕标签的定长缓冲改用 UTF-8 编解码（`SaveGame.syncFixedString`） | 本移植版运行在本地化零售资源上，字幕标签是中文文本；ISO_8859_1 之类"逐字节原样"编解码会在**写入时**把每个汉字替换成 `?`（0x3F），于是"提示期间保存退出再进入"后字幕显示为一排问号。改用 UTF-8 后标签原样往返；仅影响 `MessageWidget` 的两个标签缓冲 |

## 开发任务

测试套件是 JUnit 6.1.3（`useJUnitPlatform()`），145 个测试类中共 1458 个测试。每个测试在 `SEPARATE_THREAD` 模式下有 180 秒的墙钟预算，卡死的循环只会让一个测试失败而不是挂起构建；测试 JVM 无头运行（`SDL_VIDEODRIVER=dummy` 配软件渲染器），macOS 上添加 `-XstartOnFirstThread`。需要真实资源的测试通过 `pvzp/TestAssets.java` 获取（`-Dpvz.test.resources=<dir>` 或标准发现链），并用 `Assumptions` 门控，因此没装游戏时套件依然全绿。

写测试时值得了解的夹具：`test/HeadlessTestApp.java`（用桩图像/字体和桩声音管理器填充资源表，因此 `new LawnApp()` 之后不需要真实资源）、`lawn/LawnEntityTestSupport.java`、`pvzp/reanim/ReanimTestSupport.java`、`pvzp/audio/TestAudio.java`（在内存中构建 WAV）以及 `contentmodfixture/`（loader 端到端夹具）。

以下任务手工运行真实的东西（它们不属于 `test`）；所有开关都以 Gradle 属性（`-P…`）的形式传入：

| 任务 | 用途 |
|---|---|
| `./gradlew dumpWindow` | 打开一个真实窗口，把每帧的两幅图像（间隔 0.5 秒）写入 `build/window-dump/`：`frame-NNN.png` 是游戏画进去的 800x600 画布，`window-NNN.png` 是窗口显示的内容（合成进 drawable 的画布）；每帧打印 `appLogical=`/`canvas=`/`window=`/`present=`；`-Ppvz.renderer=software\|metal\|opengl\|…` 选择后端，`-Ppvz.window.dumpDir=…` 指定输出目录 |
| `./gradlew sceneDump` | 某个渲染场景的若干帧，用于逐帧查看特定动画；`-Ppvz.dump.scenario=graveRise\|fume\|packet\|zomboni` |
| `./gradlew zoomDump` | 关卡奖励种子包被拾取前后的若干帧（放大到 2 倍），外加一个针对缩放绘制过滤方式的探针；`-Ppvz.zoom.dumpDir/resDir/logClicks` |
| `./gradlew playthrough` | 从菜单开始，用**合成 SDL 事件**经真实输入路径完整游玩冒险模式；`-Ppvz.play.firstLevel/lastLevel/firstMode/lastMode/tickBudget/fast/fromMenu/logClicks` |
| `./gradlew bench` | 真实循环的 fps/绘制耗时/GC；`-Ppvz.bench.seconds/level/zombies/cpuFrames/iterations/jfr` |

编写这类驱动程序的三条硬规则（驱动程序自身也有记录）：

1. 在 `SDL_RenderPresent` **之前**回读窗口内容（present 之后后备缓冲区内容未定义，Metal 上回读是空白）；
2. 在帧循环线程上做回读（SDL 渲染器不是线程安全的；从其他线程读取在 Metal 上会中止）；
3. 驱动程序使用的"作弊"（秒杀僵尸、压缩波次倒计时）必须放在驱动自己的钩子监听器里，**绝不能改动游戏代码**。

改渲染代码之前还有两件事值得了解：

- **逻辑尺寸与 drawable 尺寸，以及画布。** 移植版区分逻辑窗口尺寸（用于应用布局，例如 800×600）和 drawable 尺寸（用于渲染，Retina 显示器上 1470×895 点的窗口对应 2940×1790 像素）。`WINDOW_RESIZE` 事件只携带逻辑尺寸；把 drawable 尺寸当作窗口尺寸喂回去会拉伸画布，窗口看起来什么都没有。渲染方向相反：框架把每一帧 1:1 画进 800×600 的**画布**（仅当 drawable 与画布不同时才创建的离屏纹理），并在 `WidgetManager.drawScreen` 末尾调用的 `RendererBackend.compositeToOutput()` 把它一次性缩放进 drawable（`SdlRenderer`）。`renderOutputWidth/Height` 是画布，`presentedWidth/Height` 是 drawable，`readTexturePixels(0, ..)` 读画布，`readPresentedPixels(..)` 读窗口，`logicalPresentationRect()` 以窗口**点**为单位（鼠标空间）。在 drawable 等于画布的窗口上，这一切都不会发生，窗口被直接绘制。
- **溯源标记。** 每个移植的类型/方法都在 javadoc 中记录其上游出处——`Ported from src/Lawn/Board.cpp:246 (BoardInitForPlayer)`——`src/...:<行号>` 与上游源码保持同步（`src/main` 的 375 个文件中有 355 个带标记，总计约 2358 个）。偏离处会在偏离代码旁边标注 `PORT-NOTE …`。这些注释是"为什么长这样"的证据链，预期保持准确。

## 许可证

- 移植版采用 **LGPL-3.0-or-later**，跟随其移植自的上游项目（[wszqkzqk/PvZ-Portable](https://github.com/wszqkzqk/PvZ-Portable)，© 2026 Zhou Qiankang），文件头中保留 `Ported from …` 溯源信息。许可证文本和声明随本仓库原样分发：`LICENSE` 和 `COPYING`（LGPL-3.0 + GPL-3.0）、`NOTICE`，以及 `SexyAppFramework-LICENSE.txt`（SexyAppFramework 相关部分遵循 PopCap Games Framework 许可证）。
- **不附带任何游戏资源**：《植物大战僵尸》版权归 PopCap/EA 所有，本项目与两者均无关联。运行时资源由用户提供（见 [游戏资源（用户提供）](#游戏资源用户提供)）。
- 第三方依赖：LWJGL（BSD-3）、Fabric Loader（Apache-2.0）、Sponge Mixin（MIT）、ASM（BSD-3）、Gson（Apache-2.0）、Kotlin（Apache-2.0——模组工具链的 Kotlin 插件，以及 `fabric-language-kotlin` 模组提供给 Kotlin 模组的标准库）、JUnit（EPL-2.0，仅测试）以及 **libopenmpt 0.8.9（BSD-3-Clause）**——可选的 MO3 解码器，发行包把它的 Windows 版构建放在 `lib/native/`，并附上游的 `LICENSE.txt`（即 `lib/native/libopenmpt-LICENSE.txt`）。旁边的四个编解码 DLL 是上游自己对 libmpg123（LGPL-2.1）、libogg 和 libvorbis（BSD-3）和 zlib 的构建，并携带这些库的许可证；上游在其二进制发行版的 `Licenses/` 目录（`lib.openmpt.org/files/libopenmpt/bin/`）列出这些文本——那不是本构建锁定的压缩包；逐文件的溯源见 `lib/native/libopenmpt-natives.txt`。

## 相关项目

本游戏是 **FabricPvZ** 家族中的一个仓库。其余仓库是它的依赖，但不属于它的构建：

| 仓库 | 它是什么，以及本项目与它的关系 |
|---|---|
| `fabric-api` | 模组 API：`api-base`（事件总线 + API 入口点）、`api-events`（lawn 与生命周期事件）、`api-plant` 和 `api-zombie`（内容注册，以及把已注册定义的行为分派进本游戏方法的 Mixin）、`api-resource`（模组自己的图像：游戏从不绘制的槽位、游戏自己的种子包/缓存帧绘制器、按轨道的重动画覆盖，带自己的入口点和 Mixin）。它的事件类型和 Mixin 针对本游戏的类定义，并以唯一的聚合模组 jar `fabric-api` 安装进 `mods/`——绝不是类路径依赖 |
| `loom` | 模组 Gradle 插件 `cn.enaium.fabricpvz.loom`，发布在 Gradle Plugin Portal 上：一行插件就给模组带来 loader、Mixin、ASM、API 和**本游戏**（以发行包形式），外加一个通过真实 loader 启动游戏的 `runClient` 任务 |
| `pvz-example-mod` | 参考模组：API 事件、模组钩子和一个注入游戏的 Mixin。本项目的 loader 端到端测试从 `mavenLocal` 加载它的 jar |
| `speed-mod` | 游戏速度控制（0.5×–3× 和暂停），以菜单按钮旁的按钮形式呈现 |
| `auto-collect-mod` | 替玩家收集阳光和硬币，带一个开关按钮 |

模组在自己的 `fabric.mod.json` 中声明 `"pvz-portable": "*"`，因此游戏出新版本不需要模组改动。
