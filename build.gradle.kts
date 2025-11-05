import com.github.javaparser.printer.concretesyntaxmodel.CsmElement.token
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.github.breadmoirai.githubreleaseplugin.GithubReleaseExtension

plugins {
    kotlin("jvm") version "2.2.0"
    id("fabric-loom") version "1.11-SNAPSHOT"
    id("maven-publish")
    id("com.github.johnrengelman.shadow") version "7.1.2"
    id("com.modrinth.minotaur") version "2.8.7"
    id("com.matthewprenger.cursegradle") version "1.4.0"
    id("com.github.breadmoirai.github-release") version "2.5.2"
}

version = project.property("mod_version") as String
group = project.property("maven_group") as String

base {
    archivesName.set(project.property("archives_base_name") as String)
}

val targetJavaVersion = 21
java {
    toolchain.languageVersion = JavaLanguageVersion.of(targetJavaVersion)
    withSourcesJar()
}

loom {
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
    maven("https://maven.terraformersmc.com/") // Mod Menu
}

dependencies {
    // To change the versions see the gradle.properties file
    minecraft("com.mojang:minecraft:${project.property("minecraft_version")}")
    mappings("net.fabricmc:yarn:${project.property("yarn_mappings")}:v2")
    modImplementation("net.fabricmc:fabric-loader:${project.property("loader_version")}")
    modImplementation("net.fabricmc:fabric-language-kotlin:${project.property("kotlin_loader_version")}")
    
    // 确保 JSch 依赖正确配置
    implementation("com.jcraft:jsch:0.1.55")
    // 或者使用 include 来确保它包含在最终的 JAR 中
    include("com.jcraft:jsch:0.1.55")

    modImplementation("net.fabricmc.fabric-api:fabric-api:${project.property("fabric_version")}")
    
    // Cloth Config API removed
    
    // Mod Menu integration
    modImplementation("com.terraformersmc:modmenu:16.0.0-rc.1")
}

tasks.processResources {
    inputs.property("version", project.version)
    inputs.property("minecraft_version", project.property("minecraft_version"))
    inputs.property("loader_version", project.property("loader_version"))
    filteringCharset = "UTF-8"

    filesMatching("fabric.mod.json") {
        expand(
            "version" to project.version,
            "minecraft_version" to project.property("minecraft_version"),
            "loader_version" to project.property("loader_version"),
            "kotlin_loader_version" to project.property("kotlin_loader_version")
        )
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(targetJavaVersion)
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(JvmTarget.fromTarget(targetJavaVersion.toString()))
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
    releaseAssets.setFrom(tasks.remapJar.get().outputs.files)

    /* ---------- 发布选项 ---------- */
    draft.set(false)
    prerelease.set(false)

    /* ---------- 发布说明（可选） ---------- */
    body.set("详见提交记录或 CHANGELOG.md")
}
// 移除 ShadowJar 配置，因为 Fabric Loom 已经处理了依赖打包
tasks.withType<ShadowJar>().configureEach {
    enabled = false
}