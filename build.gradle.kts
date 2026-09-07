plugins {
    id("java")
    id("org.jetbrains.intellij.platform")
}

repositories {
    mavenCentral()

    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    compileOnly("net.java.dev.jna:jna-platform:5.17.0")

    implementation("org.apache.sshd:sshd-core:2.12.1")
    implementation("org.slf4j:slf4j-api:1.7.36")
    runtimeOnly("org.slf4j:slf4j-nop:1.7.36")

    intellijPlatform {
        intellijIdea("2025.2.6")
        bundledPlugin("com.intellij.java")
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "251"
        }
    }
}