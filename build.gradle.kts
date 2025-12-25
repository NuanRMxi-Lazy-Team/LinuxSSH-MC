import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import com.github.breadmoirai.githubreleaseplugin.GithubReleaseExtension

val minecraft_version: String by project
val loader_version: String by project
val kotlin_loader_version: String by project


plugins {
    kotlin("jvm") version "2.3.0"
    // 同时应用这两个插件，或者回退到 fabric-loom 但配合 useIntermediateMappings (如果版本支持)
    // 针对 26.1-snapshot-1，目前最稳妥的组合如下：
    id("net.fabricmc.fabric-loom") version "1.14-SNAPSHOT"
    id("maven-publish")
    id("com.modrinth.minotaur") version "2.8.7"
    id("com.matthewprenger.cursegradle") version "1.4.0"
    id("com.github.breadmoirai.github-release") version "2.5.2"
}

kotlin {
    jvmToolchain(25)        // 与 java 保持一致
}


version = project.property("mod_version") as String
group = project.property("maven_group") as String

base {
    archivesName.set(project.property("archives_base_name") as String)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile>().configureEach {
    jvmTargetValidationMode.set(org.jetbrains.kotlin.gradle.dsl.jvm.JvmTargetValidationMode.WARNING)
}

val targetJavaVersion = 25
java {
    toolchain.languageVersion = JavaLanguageVersion.of(targetJavaVersion)
    withSourcesJar()
    // 同步模板中的兼容性设置
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

loom {
    // 显式禁用重映射，确保进入“非混淆模式”
    // 注意：在 net.fabricmc.fabric-loom 插件中，这个属性是核心开关
    (this as ExtensionAware).extensions.extraProperties.set("useIntermediateMappings", false)

    splitEnvironmentSourceSets()

    mods {
        register("linuxssh") {
            sourceSet("main")
            sourceSet("client")
        }
    }
}

fabricApi {
    configureDataGeneration {
        client = true
    }
}

repositories {
    // Add repositories to retrieve artifacts from in here.
    // maven("https://maven.shedaniel.me/") // Cloth Config API (removed)
    // Primary: Fabric maven for Loom/unpick and Fabric artifacts
    maven("https://maven.fabricmc.net/") { name = "Fabric" }
    // Central fallback for widely published artifacts
    mavenCentral()
}

dependencies {
    minecraft("com.mojang:minecraft:${project.property("minecraft_version")}")
    
    // 如果 modImplementation 报错，尝试直接使用普通的 implementation。
    // 因为在该版本中，不需要对依赖进行重映射（Remap），所以普通的 implementation 也是生效的。
    implementation("net.fabricmc:fabric-loader:${project.property("loader_version")}")
    implementation("net.fabricmc:fabric-language-kotlin:${project.property("kotlin_loader_version")}")
    modImplementation("com.jcraft.jsch:jsch:0.1.55")
    include("com.jcraft.jsch:jsch:0.1.55")
    // 暂时不要使用全量的 fabric-api，因为它包含了太多还没适配新环境的 AW 文件
    implementation("net.fabricmc.fabric-api:fabric-api:${project.property("fabric_version")}")
    


    // ... 其他依赖
}

tasks.processResources {
    inputs.property("version", project.version.toString())
    inputs.property("minecraft_version", project.property("minecraft_version").toString())
    inputs.property("loader_version", project.property("loader_version").toString())
    filteringCharset = "UTF-8"


    filesMatching("fabric.mod.json") {
    expand(
        "version" to project.version.toString(),
        "minecraft_version" to project.property("minecraft_version").toString(),
        "loader_version" to project.property("loader_version").toString(),
        "kotlin_loader_version" to project.property("kotlin_loader_version").toString()
    )
}
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(targetJavaVersion)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.DEFAULT)
    }
}

tasks.jar {
    from("LICENSE") {
        rename { "${it}_${project.base.archivesName}" }
    }
}

val modVersion: String = project.property("mod_version").toString()

// GitHub Release
githubRelease {
    // 1. 身份验证：直接调 token(...)
    token(
        providers.gradleProperty("release_token")
            .orElse(providers.environmentVariable("RELEASE_TOKEN"))
            .getOrElse("")
    )

    // 2. 仓库信息
    owner.set("NuanRMxi-Lazy-Team")
    repo.set("LinuxSSH-MC")


    /* ---------- 版本信息 ---------- */
    tagName.set("v${project.property("mod_version")}")
    releaseName.set("v${project.property("mod_version")}")
    targetCommitish.set("main")                         // 想指别的分支可改

    /* ---------- 资产上传 ---------- */
    // 使用更稳妥的 Kotlin DSL 引用方式
    releaseAssets.setFrom(tasks.named("jar"))

    /* ---------- 发布选项 ---------- */
    draft.set(false)
    prerelease.set(false)

    /* ---------- 发布说明（可选） ---------- */
    body.set("详见提交记录或 CHANGELOG.md")
}
// 移除 ShadowJar 配置，因为 Fabric Loom 已经处理了依赖打包
