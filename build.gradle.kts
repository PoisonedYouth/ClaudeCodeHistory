import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.2.20"
    kotlin("plugin.serialization") version "2.2.20"
    kotlin("plugin.compose") version "2.2.20"
    id("org.jetbrains.compose") version "1.9.3"
}

group = "com.claudecode"
version = "1.3.0"

repositories {
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    google()
}

configurations.all {
    resolutionStrategy {
        force("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
    }
}

dependencies {
    // Compose Desktop
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)

    // Database - SQLite with Exposed ORM
    implementation("org.xerial:sqlite-jdbc:3.45.0.0")
    implementation("org.jetbrains.exposed:exposed-core:0.57.0")
    implementation("org.jetbrains.exposed:exposed-dao:0.57.0")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.57.0")
    implementation("org.jetbrains.exposed:exposed-kotlin-datetime:0.57.0")

    // JSON Parsing
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")

    // Date/Time - Force consistent version
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.1")

    // Syntax Highlighting
    implementation("dev.snipme:kodeview:0.9.0")
    implementation("dev.snipme:highlights:0.9.0")

    // Logging
    implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")
    implementation("ch.qos.logback:logback-classic:1.4.14")

    // HTTP Client for Ollama (optional, for semantic search)
    implementation("io.ktor:ktor-client-core:3.0.3")
    implementation("io.ktor:ktor-client-cio:3.0.3")
    implementation("io.ktor:ktor-client-content-negotiation:3.0.3")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.0.3")

    // Testing
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
    testImplementation("io.kotest:kotest-assertions-core:5.9.1")
    testImplementation("io.kotest:kotest-runner-junit5:5.9.1")
    testImplementation("io.mockk:mockk:1.13.8")
    testImplementation("io.ktor:ktor-client-mock:3.0.3")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

compose.desktop {
    application {
        mainClass = "com.claudecode.history.MainKt"

        // Explicitly include runtime dependencies
        buildTypes.release.proguard {
            isEnabled.set(false)
        }

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "ClaudeCodeHistory"
            packageVersion = "1.0.0"
            description = "Search and browse your Claude Code conversation history"
            vendor = "Claude Code Community"

            // Include all runtime dependencies
            modules("java.sql", "java.naming", "jdk.unsupported")

            macOS {
                bundleID = "com.claudecode.history"
                // iconFile.set(project.file("src/main/resources/icon.icns"))
            }

            windows {
                // iconFile.set(project.file("src/main/resources/icon.ico"))
                menuGroup = "Claude Code"
                upgradeUuid = "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
            }

            linux {
                // iconFile.set(project.file("src/main/resources/icon.png"))
            }
        }
    }
}
