import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
}

dependencies {
    testImplementation("junit:junit:4.13.2")

    // IntelliJ Platform Gradle Plugin Dependencies Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    intellijPlatform {
        rider("2026.1", configure = { useInstaller = false })
        //local("C:\\Program Files\\JetBrains\\JetBrains Rider 2026.1.1\\")
        testFramework(TestFrameworkType.Platform)
    }
}
