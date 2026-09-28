import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

abstract class StagePreviewBoat : DefaultTask() {
    @get:InputFile @get:Optional abstract val modelFile: RegularFileProperty
    @get:OutputDirectory abstract val outputDir: DirectoryProperty
    @TaskAction fun stage() {
        val target=outputDir.file("files/demo-boat.glb").get().asFile
        if(modelFile.isPresent) {
            target.parentFile.mkdirs()
            modelFile.get().asFile.copyTo(target,overwrite=true)
        } else {
            // Only our generated preview file: avoid carrying a private asset into a later public build.
            target.delete()
        }
        target.parentFile.mkdirs()
        outputDir.file("files/preview-mode.txt").get().asFile.writeText(if(modelFile.isPresent) "local" else "procedural")
    }
}

plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.android.library")
}
kotlin {
    jvm("desktop")
    androidTarget()
    iosArm64 { binaries.framework { baseName="LakeLab"; isStatic=true } }
    iosSimulatorArm64 { binaries.framework { baseName="LakeLab"; isStatic=true } }
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser { commonWebpackConfig { outputFileName="lake-lab.js" } }
        binaries.executable()
    }
    jvmToolchain(17)
    sourceSets {
        commonMain.dependencies {
            if(providers.gradleProperty("joyframe.usePublished").orNull == "true")
                implementation("io.github.rehaancubess:joyframe:${project.version}")
            else implementation(project(":joyframe"))
            implementation(compose.material)
            implementation(compose.components.resources)
        }
        getByName("desktopMain").dependencies { implementation(compose.desktop.currentOs) }
        commonTest.dependencies { implementation(kotlin("test")) }
        val iosMain by creating { dependsOn(commonMain.get()) }
        iosArm64Main.get().dependsOn(iosMain)
        iosSimulatorArm64Main.get().dependsOn(iosMain)
    }
}
val stagePreviewBoat = tasks.register<StagePreviewBoat>("stagePreviewBoat") {
    outputDir.set(layout.buildDirectory.dir("generated/previewResources"))
    providers.gradleProperty("joyframe.demoBoat").orNull?.let { modelFile.set(rootProject.file(it)) }
}
compose.resources {
    packageOfResClass="example.resources"
    generateResClass=always
    customDirectory(sourceSetName="commonMain",directoryProvider=stagePreviewBoat.flatMap { it.outputDir })
}
android {
    namespace="example.lakelab.shared"
    compileSdk=36
    defaultConfig { minSdk=26 }
    compileOptions {
        sourceCompatibility=JavaVersion.VERSION_17
        targetCompatibility=JavaVersion.VERSION_17
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
