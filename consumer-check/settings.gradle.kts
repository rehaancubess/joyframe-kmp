pluginManagement { repositories { gradlePluginPortal(); google(); mavenCentral() } }
dependencyResolutionManagement {
    repositories { maven { url=uri("../build/staging") }; google(); mavenCentral() }
}
rootProject.name="joyframe-independent-consumer"
