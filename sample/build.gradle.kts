plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}
kotlin {
    jvm("desktop")
    jvmToolchain(17)
    sourceSets {
        commonMain.dependencies {
            implementation(project(":joyframe"))
            implementation(compose.material)
        }
        getByName("desktopMain").dependencies { implementation(compose.desktop.currentOs) }
    }
}
compose.desktop { application {
    mainClass = "example.MainKt"
    nativeDistributions {
        modules("jdk.unsupported")
        packageName = "JoyframePlayground"
        // jpackage requires a non-zero major on macOS; independent of the library's alpha version.
        packageVersion = "1.0.0"
    }
} }
