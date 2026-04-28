import com.mikepenz.gradle.tasks.VersionTask
import io.github.kdroidfilter.nucleus.desktop.application.dsl.TargetFormat

buildscript {
    dependencies {
        classpath("com.mikepenz.convention:convention:0.10.3")
    }
}

plugins {
    alias(baseLibs.plugins.kotlinMultiplatform)
    alias(baseLibs.plugins.composeMultiplatform)
    alias(baseLibs.plugins.composeCompiler)
    alias(baseLibs.plugins.kotlinSerialization)
    alias(baseLibs.plugins.aboutLibraries)
    alias(libs.plugins.nucleus)
    alias(libs.plugins.metro)
    id("dev.mikepenz.composebuddy") version "0.3.0"
}

configurations.all {
    resolutionStrategy {
        force(libs.kotlinx.datetime)
    }
}

val appVersion = providers.gradleProperty("app.version").get()
val appPackageVersion = appVersion.replace(Regex("[+-].*"), "")
require(appPackageVersion.matches(Regex("\\d+\\.\\d+\\.\\d+"))) {
    "appPackageVersion '$appPackageVersion' derived from app.version '$appVersion' must be MAJOR.MINOR.PATCH"
}

val generateVersion = project.tasks.register<VersionTask>("generateVersion") {
    packageString.set("com.mikepenz.agentbuddy")
    version.set(appVersion)
    store(resources)
    into(layout.buildDirectory.dir("generated-version/kotlin/"))
}

kotlin {
    jvm()

    sourceSets {
        commonMain {
            kotlin.srcDir(generateVersion)
        }
        @Suppress("DEPRECATION")
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(baseLibs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kermit)
            implementation(baseLibs.aboutlibraries.core)
            implementation(baseLibs.aboutlibraries.compose.m3)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(baseLibs.kotlinx.coroutines.swing)
            implementation(libs.jetbrains.lifecycle.viewmodel)
            implementation(libs.jetbrains.lifecycle.viewmodel.compose)
            implementation(libs.metrox.viewmodel.compose)
            implementation(libs.ktor.server.core)
            implementation(libs.ktor.server.netty)
            implementation(libs.ktor.server.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.cio)
            implementation(libs.markdown.renderer)
            implementation(libs.markdown.renderer.code)
            implementation(libs.jna)
            implementation(libs.java.keyring)
            implementation(libs.nucleus.darkmode.detector)
            implementation(libs.nucleus.decorated.window.core)
            implementation(libs.nucleus.decorated.window.jni)
            implementation(libs.nucleus.decorated.window.material3)
            implementation(libs.nucleus.notification.macos)
            implementation(libs.nucleus.notification.linux)
            implementation(libs.nucleus.global.hotkey)
            implementation(libs.nucleus.updater.runtime)
            implementation(libs.composenativetray)
            implementation(libs.kotlinx.coroutines.jdk8)
            implementation(libs.copilot.sdk.java)
            implementation(libs.sqlite.jdbc)
            implementation(libs.vico.compose)
        }
    }
}


aboutLibraries {
    export {
        outputPath = file("src/commonMain/composeResources/files/aboutlibraries.json")
    }
    library {
        duplicationMode = com.mikepenz.aboutlibraries.plugin.DuplicateMode.MERGE
    }
}

nucleus.application {
    mainClass = "com.mikepenz.agentbuddy.MainKt"

    jvmArgs += listOf(
        "--add-opens", "java.desktop/java.awt=ALL-UNNAMED",
        "--add-opens", "java.desktop/sun.lwawt.macosx=ALL-UNNAMED",
        "--add-opens", "java.desktop/sun.lwawt=ALL-UNNAMED",
    )
    if (providers.gradleProperty("devMode").isPresent) {
        args += listOf("--dev")
    }

    nativeDistributions {
        targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
        packageName = "AgentBuddy"
        packageVersion = appPackageVersion
        description = "Centralized approval UI for AI agent tool requests"
        vendor = "mikepenz"
        homepage = "https://github.com/mikepenz/agent-buddy"

        cleanupNativeLibs = true

        modules("java.sql")

        macOS {
            iconFile.set(project.file("../icons/app.icns"))
            layeredIconDir.set(project.file("../icons/AgentBuddy.icon"))
            bundleID = "com.mikepenz.agentbuddy"
            dockName = "Agent Buddy"
            appCategory = "public.app-category.developer-tools"
            infoPlist {
                extraKeysRawXml = """
                    <key>NSUserNotificationAlertStyle</key>
                    <string>banner</string>
                """.trimIndent()
            }
        }
        windows {
            iconFile.set(project.file("../icons/app.ico"))
            console = false
            perUserInstall = true
        }
        linux {
            iconFile.set(project.file("../icons/app.png"))
            debMaintainer = "Mike Penz <opensource@mikepenz.dev>"
        }
    }
}

composeBuddy {
    devicePreviewEnabled.set(false)
}
