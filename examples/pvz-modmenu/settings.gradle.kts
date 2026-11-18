pluginManagement {
    repositories {
        // 模组工具链插件：本地已发布的版本优先，再回退到 Gradle Plugin Portal
        mavenLocal()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenLocal()
        mavenCentral()
        // Fabric Loader / Mixin 的坐标来自 Fabric 的 maven（Maven Central 上没有）
        maven {
            name = "Fabric"
            url = uri("https://maven.fabricmc.net/")
        }
    }
}

rootProject.name = "modmenu"
