import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

dependencies {
    testImplementation(kotlin("test-junit"))

    intellijPlatform {
        create(IntelliJPlatformType.IntellijIdeaCommunity, "2024.2.6") {
            useInstaller = false
        }
        bundledPlugin("org.jetbrains.plugins.gradle")
    }
}

kotlin {
    jvmToolchain(21)
    compilerOptions.jvmTarget.set(JvmTarget.JVM_21)
}

intellijPlatform {
    buildSearchableOptions = false

    pluginConfiguration {
        ideaVersion {
            sinceBuild = "242"
            untilBuild = provider { null }
        }
    }

    pluginVerification {
        ides {
            current()
        }
    }
}

tasks {
    withType<JavaCompile>().configureEach {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }

    withType<VerifyPluginTask>().configureEach {
        systemProperty(
            "plugin.verifier.home.dir",
            layout.buildDirectory.dir("pluginVerifier-home").get().asFile.absolutePath,
        )
    }
}
