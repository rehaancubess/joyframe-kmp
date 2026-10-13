import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.gradle.api.publish.maven.MavenPublication

plugins {
    kotlin("multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.vanniktech.maven.publish") version "0.37.0"
    `maven-publish`
    signing
}

kotlin {
    jvm("desktop")
    android {
        namespace = "io.github.rehaancubess.joyframe"
        compileSdk = 37
        minSdk = 26
        compilerOptions { jvmTarget = JvmTarget.JVM_17 }
    }
    iosArm64(); iosSimulatorArm64()
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs { browser() }
    jvmToolchain(17)
    sourceSets {
        commonMain.dependencies {
            api("org.jetbrains.compose.runtime:runtime:1.11.1")
            api("org.jetbrains.compose.foundation:foundation:1.11.1")
            api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
        }
        commonTest.dependencies { implementation(kotlin("test")) }
        val glMain by creating { dependsOn(commonMain.get()) }
        val iosMain by creating {
            dependsOn(commonMain.get())
        }
        iosArm64Main.get().dependsOn(iosMain)
        iosSimulatorArm64Main.get().dependsOn(iosMain)
        androidMain.get().dependsOn(glMain)
        wasmJsMain.get().dependsOn(glMain)
        val desktopMain by getting {
            dependsOn(glMain)
            dependencies {
                implementation("org.jetbrains.compose.desktop:desktop:1.11.1")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.11.0")
                implementation("net.java.dev.jna:jna:5.14.0")
                for (module in listOf("lwjgl", "lwjgl-opengl", "lwjgl-glfw")) {
                    implementation("org.lwjgl:$module:3.3.6")
                    for (classifier in listOf("natives-windows", "natives-linux", "natives-macos", "natives-macos-arm64")) {
                        runtimeOnly("org.lwjgl:$module:3.3.6:$classifier")
                    }
                }
                implementation("org.lwjgl:lwjgl-jawt:3.3.6")
                implementation("org.lwjglx:lwjgl3-awt:0.2.3") { isTransitive = false }
            }
        }
    }
}
publishing {
    repositories {
        maven { name = "staging"; url = uri(rootProject.layout.buildDirectory.dir("staging")) }
    }
    publications.withType<MavenPublication>().configureEach {
        pom {
            name.set("Joyframe KMP")
            description.set("Kotlin Multiplatform game toolkit: rendering, audio and controller support.")
            url.set("https://github.com/rehaancubess/joyframe-kmp")
            licenses { license {
                name.set("The Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            } }
            developers { developer { id.set("rehaancubess"); name.set("Rehaan") } }
            scm {
                url.set("https://github.com/rehaancubess/joyframe-kmp")
                connection.set("scm:git:https://github.com/rehaancubess/joyframe-kmp.git")
            }
        }
    }
}
mavenPublishing {
    coordinates("io.github.rehaancubess", "joyframe", version.toString())
    // Upload and validation only. Publishing a Central release remains an explicit portal action.
    publishToMavenCentral()
    if (providers.gradleProperty("joyframe.release").orNull == "true") signAllPublications()
}
