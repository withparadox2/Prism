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
    implementation(kotlin("stdlib"))
    implementation(files("/Users/bytedance/.gradle/caches/modules-2/files-2.1/com.jetbrains.intellij.idea/ideaIC/2022.2/42c296374014a649785bb84aa6d8dc2d18f2ca0e/ideaIC-2022.2/plugins/android/lib/dexlib2.jar"))
    implementation(files("/Users/bytedance/.gradle/caches/modules-2/files-2.1/com.jetbrains.intellij.idea/ideaIC/2022.2/42c296374014a649785bb84aa6d8dc2d18f2ca0e/ideaIC-2022.2/lib/3rd-party-rt.jar"))
}

intellij {
//    type.set("IC")
//    version.set("2024.1.4")
//    plugins.set(listOf("com.intellij.java"))

    localPath.set("/Applications/Android Studio 2.app/Contents")
    plugins.set(listOf("android","com.intellij.java"))
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