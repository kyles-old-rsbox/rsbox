plugins {
    kotlin("jvm") version "1.9.0"
}

tasks.wrapper {
    gradleVersion = "8.2.1"
}

allprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")

    group = "io.rsbox"
    version = "1.0.0"

    repositories {
        mavenLocal()
        mavenCentral()
        maven(url = "https://jitpack.io")
        maven(url = "https://repo.openrs2.org/repository/openrs2")
        maven(url = "https://repo.openrs2.org/repository/openrs2-snapshots")
        maven(url = "https://maven.fabricmc.net")
    }

    dependencies {
        implementation(kotlin("reflect"))
        testImplementation(kotlin("test"))
    }

    tasks.test {
        useJUnitPlatform()
    }
}

allprojects {
    if(name != "client") {
        kotlin {
            jvmToolchain {
                languageVersion.set(JavaLanguageVersion.of(11))
            }
        }
    }
}