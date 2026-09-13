// BiliForge · 顶层设置
// 国内网络可把 google()/mavenCentral() 换成阿里云镜像（见 README）
pluginManagement {
    repositories {
        maven { url = uri("local-repo") }   // 工程内本地仓库（MIUIX 元数据修正版）
        // 国内镜像优先（国内构建友好；缺失时自动落回官方源）
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/public")
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url = uri("local-repo") }   // 工程内本地仓库（MIUIX 元数据修正版）
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/public")
        google()
        mavenCentral()
    }
}
rootProject.name = "BiliForge"
include(":app")
