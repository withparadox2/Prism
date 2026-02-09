plugins {
    kotlin("jvm") version "1.9.22"
    id("org.jetbrains.intellij") version "1.17.3"
}


group = "demo.linemap"
version = "0.1.0"


repositories {
    mavenCentral()
}


intellij {
    type.set("IC") // Android Studio 基于 IntelliJ Community
    version.set("2022.2")
    plugins.set(listOf("android", "com.intellij.java"))
}

tasks {
    patchPluginXml {
        changeNotes.set("Initial version")
    }
}

kotlin {
    jvmToolchain(17)
}


intellij {
    instrumentCode.set(false)
}