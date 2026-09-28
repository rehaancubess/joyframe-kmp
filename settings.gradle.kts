pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositories {
        if (providers.gradleProperty("joyframe.usePublished").orNull == "true") maven { url=uri("build/staging") }
        google(); mavenCentral()
    }
}
rootProject.name = "joyframe-kmp"
include(":joyframe", ":sample", ":sample-android")
