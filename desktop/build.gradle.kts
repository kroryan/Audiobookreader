import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm")
    id("org.jetbrains.compose")
}

kotlin {
    jvmToolchain(21)
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = "1.8"
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material)
    implementation("org.apache.commons:commons-compress:1.26.1")
    implementation("org.json:json:20240303")
    implementation("org.apache.pdfbox:pdfbox:2.0.30")
    implementation("org.jsoup:jsoup:1.17.2")
    // Same sherpa-onnx JVM API as Android, with native desktop binaries.
    implementation("com.github.k2-fsa.sherpa-onnx:sherpa-onnx-jvm:1.13.7")
    implementation("com.github.k2-fsa.sherpa-onnx:sherpa-onnx-native-lib-linux-x64:1.13.7")
    implementation("com.github.k2-fsa.sherpa-onnx:sherpa-onnx-native-lib-linux-aarch64:1.13.7")
    implementation("com.github.k2-fsa.sherpa-onnx:sherpa-onnx-native-lib-win-x64:1.13.7")
    implementation("com.github.k2-fsa.sherpa-onnx:sherpa-onnx-native-lib-win-arm64:1.13.7")
    implementation("com.github.k2-fsa.sherpa-onnx:sherpa-onnx-native-lib-osx-x64:1.13.7")
    implementation("com.github.k2-fsa.sherpa-onnx:sherpa-onnx-native-lib-osx-aarch64:1.13.7")
}

compose.desktop {
    application {
        mainClass = "com.audiobookreader.desktop.MainKt"
        nativeDistributions {
            packageName = "BookReader"
            packageVersion = "0.1.7"
            description = "Read books aloud with downloadable local voices"
            vendor = "BookReader"
            targetFormats(TargetFormat.Deb, TargetFormat.AppImage, TargetFormat.Msi, TargetFormat.Exe)
            linux {
                iconFile.set(project.file("../assets/bookreader-icon.png"))
            }
        }
    }
}
