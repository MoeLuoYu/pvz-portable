package cn.enaium.fabricpvz.modmenu;

import cn.enaium.fabricpvz.modmenu.api.ModMenuApi;
import cn.enaium.fabricpvz.portable.sexy.graphics.Image;
import cn.enaium.fabricpvz.portable.sexy.graphics.MemoryImage;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import net.fabricmc.loader.api.metadata.ModOrigin;
import net.fabricmc.loader.api.metadata.Person;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 一个模组的归一化信息：从 Fabric Loader 的 {@link ModContainer} 元数据提取
 * 详情面板要显示的全部字段（{@code net.fabricmc.loader.api.metadata} 0.19.5 的
 * ContactInformation 只有 {@code get(String)}/{@code asMap()}，官网/问题/链接
 * 都从联系方式的 map 按键取）。
 */
final class ModEntry {

    /** 模组 id（fabric.mod.json 的 id，唯一）。 */
    final String modId;
    /** 显示名（空名回退 id）。 */
    final String name;
    /** 版本（friendly string）。 */
    final String version;
    /** 描述（可能为空串）。 */
    final String description;
    /** 官网；未设置 null。 */
    final String website;
    /** 问题追踪；未设置 null。 */
    final String issues;
    /** 链接条目（显示名 + URL，可点超链接）；sources → "源码"，contact 的 modmenu.* 键 → 平台名。 */
    final List<Link> links;
    /** 许可证（逗号连接）；未设置空串。 */
    final String license;
    /** 作者（逗号连接）；未设置空串。 */
    final String authors;
    /** 贡献者（逗号连接）；未设置空串。 */
    final String contributors;
    /** 模组包内图标（加载成功时）；否则 null，界面画占位框。 */
    final Image icon;
    /** 是否声明了 modmenu 配置入口点。 */
    final boolean hasConfig;

    private ModEntry(ModContainer theContainer, Image theIcon, boolean theHasConfig) {
        var aMeta = theContainer.getMetadata();
        modId = aMeta.getId();
        name = aMeta.getName().isEmpty() ? aMeta.getId() : aMeta.getName();
        version = aMeta.getVersion().getFriendlyString();
        description = aMeta.getDescription();
        Map<String, String> aContact = aMeta.getContact().asMap();
        website = aContact.get("website");
        issues = aContact.get("issues");
        List<Link> aLinks = new ArrayList<>();
        String aSources = aContact.get("sources");
        if (aSources != null) {
            for (String aUrl : aSources.split("\\s+")) {
                aLinks.add(new Link("源码", aUrl));
            }
        }
        // modmenu.<平台> 键 → 平台名超链接（模组在 contact 里自行声明社交/托管平台）
        for (Map.Entry<String, String> anEntry : aContact.entrySet()) {
            if (anEntry.getKey().startsWith("modmenu.") && anEntry.getKey().length() > 8) {
                aLinks.add(new Link(platformName(anEntry.getKey().substring(8)), anEntry.getValue()));
            }
        }
        links = List.copyOf(aLinks);
        license = aMeta.getLicense().stream().collect(Collectors.joining(", "));
        authors = aMeta.getAuthors().stream().map(Person::getName).collect(Collectors.joining(", "));
        contributors = aMeta.getContributors().stream().map(Person::getName).collect(Collectors.joining(", "));
        icon = theIcon;
        hasConfig = theHasConfig;
    }

    /** 一条命名超链接（链接区的显示名与目标 URL）。 */
    record Link(String name, String url) {
    }

    /** contact 键 {@code modmenu.<name>} → 显示名；常见海内外平台映射，未收录的键首字母大写回落。 */
    static String platformName(String theKey) {
        return switch (theKey.toLowerCase()) {
            case "github" -> "GitHub";
            case "gitlab" -> "GitLab";
            case "codeberg" -> "Codeberg";
            case "gitee" -> "Gitee";
            case "sourceforge" -> "SourceForge";
            case "modrinth" -> "Modrinth";
            case "curseforge" -> "CurseForge";
            case "discord" -> "Discord";
            case "qq", "qqgroup" -> "QQ群";
            case "wechat", "weixin" -> "微信";
            case "weibo" -> "微博";
            case "zhihu" -> "知乎";
            case "bilibili" -> "哔哩哔哩";
            case "douyin" -> "抖音";
            case "x", "twitter" -> "X";
            case "mastodon" -> "Mastodon";
            case "bluesky" -> "Bluesky";
            case "youtube" -> "YouTube";
            case "reddit" -> "Reddit";
            case "telegram" -> "Telegram";
            case "twitch" -> "Twitch";
            case "tiktok" -> "TikTok";
            case "instagram" -> "Instagram";
            case "facebook" -> "Facebook";
            case "patreon" -> "Patreon";
            case "kofi", "ko-fi" -> "Ko-fi";
            default -> Character.toUpperCase(theKey.charAt(0)) + theKey.substring(1);
        };
    }

    /** 提取已装载模组，按显示名排序（游戏本体 pvz-portable 置顶）。 */
    static List<ModEntry> loadAll() {
        // 配置入口点：懒实例化一次并按 provider 分组；声明了入口点但类型不符的模组
        // 在 Loader 侧会抛包装异常——按"无配置"处理，不影响其余模组
        Map<String, Boolean> aConfigIds = new java.util.HashMap<>();
        try {
            for (EntrypointContainer<ModMenuApi> anEntry :
                    FabricLoader.getInstance().getEntrypointContainers("modmenu", ModMenuApi.class)) {
                aConfigIds.put(anEntry.getProvider().getMetadata().getId(), true);
            }
        } catch (RuntimeException ignored) {
            // 某模组的 modmenu 入口点不是 ModMenuApi——忽略它
        }

        List<ModEntry> aList = new ArrayList<>();
        for (ModContainer aContainer : FabricLoader.getInstance().getAllMods()) {
            // 与主界面"模组已载入 N 个"同一口径：排除嵌套模组（loader 内嵌的 MixinExtras、
            // fabric-api 聚合 jar 的 api-* 子模块）与 builtin 环境占位（JVM 等）；
            // 游戏本体（builtin）与 loader 自身保留
            if (aContainer.getOrigin().getKind() == ModOrigin.Kind.NESTED) {
                continue;
            }
            var aMeta = aContainer.getMetadata();
            if (aMeta.getType().equals("builtin") && !aMeta.getId().equals("pvz-portable")) {
                continue;
            }
            boolean aHasConfig = aConfigIds.getOrDefault(aMeta.getId(), false);
            aList.add(new ModEntry(aContainer, loadIcon(aContainer), aHasConfig));
        }
        aList.sort(Comparator.comparing(a -> a.name.toLowerCase()));
        // 游戏本体永远排第一
        aList.sort((a, b) -> Boolean.compare(!b.modId.equals("pvz-portable"), !a.modId.equals("pvz-portable")));
        return aList;
    }

    /** 读模组包内图标：getIconPath 相对路径 → findPath → PNG 解码 → MemoryImage。失败 null。 */
    private static Image loadIcon(ModContainer theContainer) {
        return theContainer.getMetadata().getIconPath(0)
                .flatMap(theContainer::findPath)
                .flatMap(aPath -> {
                    try (var anIn = Files.newInputStream(aPath)) {
                        java.awt.image.BufferedImage anImage = ImageIO.read(new ByteArrayInputStream(anIn.readAllBytes()));
                        if (anImage == null) {
                            return java.util.Optional.<Image>empty();
                        }
                        MemoryImage aMemoryImage = new MemoryImage();
                        aMemoryImage.setBits(anImage.getRGB(0, 0, anImage.getWidth(), anImage.getHeight(),
                                null, 0, anImage.getWidth()), anImage.getWidth(), anImage.getHeight(), true);
                        return java.util.Optional.<Image>of(aMemoryImage);
                    } catch (Exception e) {
                        return java.util.Optional.<Image>empty();
                    }
                })
                .orElse(null);
    }
}
