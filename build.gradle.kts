plugins {
    kotlin("jvm") version "1.9.22"
    id("org.jetbrains.intellij") version "1.17.3"
}

group = "com.bytedance.idea.plugin.prism"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.smali:dexlib2:2.5.2")
}

// ./gradlew runIde -Pdebug=true
// ./gradlew buildPlugin
val isDebugEnv: Boolean
    get() = project.hasProperty("debug") && project.property("debug") == "true"
            || System.getenv("debug") == "true"

intellij {
    if (isDebugEnv) {
        localPath.set("/Applications/Android Studio 2.app/Contents")
        plugins.set(listOf("android","com.intellij.java"))
    } else {
        type.set("IC")
        version.set("2024.1.4")
        plugins.set(listOf("com.intellij.java"))
    }
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