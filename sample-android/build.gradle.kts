plugins {
    id("com.android.application")
    kotlin("android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace="example.lakelab"
    compileSdk=36
    defaultConfig {
        applicationId="io.github.rehaancubess.joyframe.lakelab"
        minSdk=26; targetSdk=36; versionCode=1; versionName="0.1"
    }
    compileOptions { sourceCompatibility=JavaVersion.VERSION_17; targetCompatibility=JavaVersion.VERSION_17 }
    buildFeatures { compose=true }
}
kotlin { jvmToolchain(17) }
dependencies {
    implementation(project(":sample"))
    if(providers.gradleProperty("joyframe.usePublished").orNull == "true")
        implementation("io.github.rehaancubess:joyframe:${rootProject.version}")
    else implementation(project(":joyframe"))
    implementation("androidx.activity:activity-compose:1.10.1")
}
