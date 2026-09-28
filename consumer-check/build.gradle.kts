plugins { kotlin("jvm") version "2.3.20" }
kotlin { jvmToolchain(17) }
dependencies {
    implementation("io.github.rehaancubess:joyframe:0.1.0-alpha03")
    testImplementation(kotlin("test"))
}
