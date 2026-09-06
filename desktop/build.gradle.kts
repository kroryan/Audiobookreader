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
    testImplementation("junit:junit:4.13.2")
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
            packageVersion = "0.1.8"
            description = "Read books aloud with downloadable local voices"
            vendor = "BookReader"
            modules("java.desktop", "java.logging", "java.prefs", "jdk.crypto.ec", "jdk.unsupported")
            targetFormats(TargetFormat.Deb, TargetFormat.AppImage, TargetFormat.Msi, TargetFormat.Exe)
            linux {
                iconFile.set(project.file("../assets/bookreader-icon.png"))
            }
            windows {
                iconFile.set(project.file("../assets/bookreader-icon.ico"))
                perUserInstall = true
                menuGroup = "BookReader"
                shortcut = true
                dirChooser = true
                upgradeUuid = "628af63c-4199-4878-acb5-72581a0d727a"
            }
        }
    }
}

// Stage platform-independent bytecode plus Windows x64 natives for jpackage.
// This also permits packaging with the Windows JDK under Wine on Linux.
val windowsGraphics by configurations.creating
dependencies {
    windowsGraphics("org.jetbrains.skiko:skiko-awt-runtime-windows-x64:0.7.85.4")
}
val windowsDependencies = providers.provider {
    (configurations.runtimeClasspath.get().files + windowsGraphics.files).filter { file ->
        val name = file.name
        (!name.startsWith("skiko-awt-runtime-") || name.contains("windows-x64")) &&
            (!name.startsWith("sherpa-onnx-native-lib-") || name.contains("win-x64"))
    }
}
val windowsLauncherJar by tasks.registering(Jar::class) {
    dependsOn(tasks.jar, configurations.runtimeClasspath)
    inputs.files(windowsDependencies)
    inputs.property("applicationJar", tasks.jar.flatMap { it.archiveFileName })
    archiveFileName.set("BookReader-launcher.jar")
    destinationDirectory.set(layout.buildDirectory.dir("windows-launcher"))
    doFirst {
        manifest.attributes(
            "Main-Class" to "com.audiobookreader.desktop.MainKt",
            "Class-Path" to (listOf(tasks.jar.get().archiveFileName.get()) +
                windowsDependencies.get().map { it.name }).joinToString(" "),
        )
    }
}
tasks.register<Sync>("stageWindows") {
    dependsOn(tasks.jar, windowsLauncherJar, configurations.runtimeClasspath)
    from(tasks.jar)
    from(windowsLauncherJar)
    from(windowsDependencies)
    into(layout.buildDirectory.dir("windows-input"))
}
